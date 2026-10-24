package com.squeve.redail.engine

import com.squeve.redail.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.random.Random

/**
 * One call at a time. The next call is never placed until the phone line is idle again.
 * Order is decided up front (one by one, round robin or shuffled).
 */
class RedialEngine(
    private val dialers: List<Dialer>,
    private val phoneBusy: StateFlow<Boolean>,
    private val phoneRinging: StateFlow<Boolean>,
    private val callLog: CallLogReader,
    private val audio: AudioControl = NoAudio,
    private val onResult: suspend (AttemptResult) -> Unit = {},
    private val onEvent: (String) -> Unit = {},
    private val onCheckpoint: (List<Int>) -> Unit = {},
    private val gate: () -> Gate = { Gate.Go },
    private val isHeld: (String) -> Boolean = { false },   // numbers the user put on hold during the run
    private val onAnswerGuess: (String, Int) -> Unit = { _, _ -> },   // a call is probably answered
    private val simMode: Int = 0,                // 0 fixed SIM, 1 fall back to the next SIM, 2 alternate every call
    private val answeredAfterSec: Int = 5,       // a call this long (per the call log) counts as answered
    private val dryRun: Boolean = false,         // simulate: no real calls
    private val busyMaxSec: Long = 15,           // a call that ends by itself this fast, unanswered, was busy or declined
    private val startTimeoutMs: Long = 10_000,   // max wait for a placed call to actually begin
    private val settleTimeoutMs: Long = 8_000,   // max wait for the line to free up after hang up
    private val postCallPauseMs: Long = 800,     // let the telephony stack settle between calls
) {
    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val pausedFlow = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = pausedFlow.asStateFlow()

    /** Why the engine is waiting (quiet hours, airplane mode, ...). null when it is not. */
    private val waitFlow = MutableStateFlow<String?>(null)
    val waiting: StateFlow<String?> = waitFlow.asStateFlow()

    /** Set when the run ended early on purpose (for example the daily cap). */
    @Volatile var stopReason: String? = null
        private set

    /** When the auto hang-up fires for the call in progress. null = timer off. */
    private val hangUpAt = MutableStateFlow<Long?>(null)
    @Volatile private var currentLimit: Long? = null
    @Volatile private var inCallNow = false
    @Volatile private var callCounter = 0

    /** True once the user has taken over the call: the mute is no longer forced. */
    private val micReleased = MutableStateFlow(false)

    private var runJob: Job? = null
    val isRunning: Boolean get() = runJob?.isActive == true

    fun start(scope: CoroutineScope, queue: List<RedialJob>, order: CallOrder = CallOrder.SEQUENTIAL) {
        if (isRunning || queue.isEmpty()) return
        pausedFlow.value = false
        stopReason = null
        runJob = scope.launch { run(queue, order) }
    }

    fun pause() { pausedFlow.value = true }
    fun resume() { pausedFlow.value = false }

    /** Cancel the auto hang-up for the current call; it now ends by itself. */
    fun holdCall() {
        if (inCallNow) {
            hangUpAt.value = null
            releaseMic()    // keeping a call means you want to talk
        }
    }

    /** Stop forcing the microphone mute for the call in progress and open the mic now. */
    fun releaseMic() {
        if (!inCallNow) return
        micReleased.value = true
        if (!dryRun) audio.unmute()
    }

    /** Start the auto hang-up countdown again for the current call. */
    fun rearmTimer() {
        val limit = currentLimit
        if (inCallNow && limit != null) hangUpAt.value = System.currentTimeMillis() + limit
    }

    fun stop() {
        runJob?.cancel()
        runJob = null
        pausedFlow.value = false
        waitFlow.value = null
        hangUpAt.value = null
        inCallNow = false
        if (!dryRun) dialers.first().hangUp()
        audio.restore()
        _state.value = EngineState.Idle
    }

    // ───────────── ordering ─────────────
    private fun buildOrder(queue: List<RedialJob>, order: CallOrder): List<Int> {
        val slots = mutableListOf<Int>()
        when (order) {
            CallOrder.SEQUENTIAL ->
                queue.forEachIndexed { i, j -> repeat(j.attempts) { slots += i } }

            CallOrder.ROUND_ROBIN -> {
                val most = queue.maxOf { it.attempts }
                for (round in 1..most) {
                    queue.forEachIndexed { i, j -> if (j.attempts >= round) slots += i }
                }
            }

            CallOrder.RANDOM -> {
                queue.forEachIndexed { i, j -> repeat(j.attempts) { slots += i } }
                slots.shuffle()
                separateRepeats(slots)
            }
        }
        return slots
    }

    private fun pairDup(l: List<Int>, i: Int) = i in 1 until l.size && l[i] == l[i - 1]

    private fun dupsAround(l: List<Int>, a: Int, b: Int) =
        listOf(a, a + 1, b, b + 1).distinct().count { pairDup(l, it) }

    /** After shuffling, try not to call the same number twice in a row. */
    private fun separateRepeats(l: MutableList<Int>) {
        for (k in 1 until l.size) {
            if (!pairDup(l, k)) continue
            for (c in (0 until l.size).shuffled().take(60)) {
                if (c == k) continue
                val before = dupsAround(l, k, c)
                val tmp = l[k]; l[k] = l[c]; l[c] = tmp
                if (dupsAround(l, k, c) < before) {
                    if (!pairDup(l, k)) break
                } else {
                    val back = l[k]; l[k] = l[c]; l[c] = back
                }
            }
        }
    }

    private fun pickGap(job: RedialJob): Long {
        val max = job.gapMaxMs
        return if (max != null && max > job.gapMs) Random.nextLong(job.gapMs, max + 1) else job.gapMs
    }

    // ───────────── main loop ─────────────
    private suspend fun run(queue: List<RedialJob>, order: CallOrder) {
        val results = mutableListOf<AttemptResult>()
        try {
            val slots = buildOrder(queue, order)
            val steps = slots.size

            val counter = IntArray(queue.size)
            val attemptNo = IntArray(steps)
            slots.forEachIndexed { s, ji ->
                counter[ji]++
                attemptNo[s] = counter[ji]
            }
            val done = IntArray(queue.size)
            val answered = IntArray(queue.size)
            val skipped = BooleanArray(queue.size)
            val firstAt = LongArray(queue.size)

            fun progressAt(s: Int): Progress {
                val ji = slots[s]
                val job = queue[ji]
                return Progress(ji, queue.size, job.number, attemptNo[s], job.attempts, s + 1, steps, done.toList())
            }

            var first = true
            var lastGap = 0L

            // Calls still to place. A number on hold is passed over until it is released.
            val pending = ArrayDeque((0 until steps).toList())
            val holdMsg = "Numbers on hold - resume one or stop"
            while (true) {
                pending.removeAll { skipped[slots[it]] }
                if (pending.isEmpty()) break
                val next = pending.firstOrNull { !isHeld(queue[slots[it]].number) }
                if (next == null) {
                    if (waitFlow.value != holdMsg) {
                        waitFlow.value = holdMsg
                        onEvent("Everything left is on hold")
                    }
                    delay(400)
                    continue
                }
                if (waitFlow.value == holdMsg) waitFlow.value = null
                pending.remove(next)
                val s: Int = next
                val ji = slots[s]
                val job = queue[ji]

                if (!first) cooldown(progressAt(s), lastGap)
                first = false

                awaitNotPaused()
                val why = awaitGate()
                if (why != null) {
                    stopReason = why
                    onEvent("Stopped: $why")
                    break
                }
                if (isHeld(job.number)) {
                    // Put on hold while we were waiting: keep its place in line.
                    pending.addFirst(s)
                    continue
                }
                val limitMs = job.giveUpAfterMs
                if (limitMs != null && firstAt[ji] > 0L && System.currentTimeMillis() - firstAt[ji] >= limitMs) {
                    skipped[ji] = true
                    done[ji] = job.attempts
                    onEvent("${job.number}: gave up after ${limitMs / 60_000} min")
                    onCheckpoint(done.toList())
                    continue
                }
                waitUntilIdle()
                if (firstAt[ji] == 0L) firstAt[ji] = System.currentTimeMillis()

                val r = dialOnce(job, progressAt(s))
                results += r
                onResult(r)

                done[ji]++
                if (r.outcome == AttemptOutcome.CONNECTED) answered[ji]++
                lastGap = when (r.outcome) {
                    AttemptOutcome.BUSY -> job.gapBusyMs ?: pickGap(job)
                    AttemptOutcome.NO_ANSWER -> job.gapNoAnswerMs ?: pickGap(job)
                    else -> pickGap(job)
                }
                if (job.stopOnConnected && answered[ji] >= job.stopAfterAnswers) {
                    skipped[ji] = true
                    done[ji] = job.attempts
                    onEvent("${job.number}: answered, skipping its remaining calls")
                }
                onCheckpoint(done.toList())
            }

            pausedFlow.value = false
            waitFlow.value = null
            onEvent("Run finished · ${results.size} calls")
            _state.value = EngineState.Finished(results)
        } catch (e: CancellationException) {
            _state.value = EngineState.Idle
            throw e
        }
    }

    private fun pickDialers(): List<Dialer> {
        val n = dialers.size
        return when {
            n <= 1 -> dialers
            simMode == 2 -> listOf(dialers[callCounter % n])
            simMode == 1 -> dialers
            else -> listOf(dialers[0])
        }
    }

    private suspend fun dialOnce(job: RedialJob, p: Progress): AttemptResult {
        if (dryRun) return dryCall(job, p)
        val startedAt = System.currentTimeMillis()

        var used: Dialer? = null
        for (d in pickDialers()) {
            _state.value = EngineState.Dialing(p)
            val simNote = if (dialers.size > 1 && d.slot >= 0) " · SIM ${d.slot + 1}" else ""
            onEvent("Dialing ${job.number} · call ${p.step}/${p.steps}$simNote")
            if (!d.place(job.number, job.speaker)) {
                onEvent("Could not place the call")
                continue
            }
            // Wait for the call to really begin (line goes busy).
            val started = withTimeoutOrNull(startTimeoutMs) { phoneBusy.first { it } } != null
            if (started) {
                used = d
                break
            }
            onEvent("The call did not start")
            d.hangUp()
            awaitLineFree()
        }
        callCounter++
        if (used == null) return failed(job, p, -1)
        val dialer = used
        val callStartedAt = System.currentTimeMillis()

        val limit = job.hangUpAfterMs
        currentLimit = limit
        hangUpAt.value = limit?.let { System.currentTimeMillis() + it }
        micReleased.value = false
        inCallNow = true

        val endedByItself: Boolean = coroutineScope {
            // Keeps the published state in step with the hang-up timer (armed, held, re-armed).
            val mirror = launch {
                combine(hangUpAt, micReleased) { at, released -> at to released }.collect { (at, released) ->
                    _state.value = EngineState.InCall(p, at, limit != null, job.muteMic && !released)
                }
            }
            // Re-apply speaker/mute while the call runs; the phone resets audio when the call connects.
            val keeper = if (job.speaker || job.muteMic) {
                launch {
                    while (!micReleased.value) {
                        audio.apply(job.speaker, job.muteMic)
                        delay(1_500)
                    }
                    audio.unmute()
                }
            } else null

            // Android never says when a call is answered, so a call still going after this long counts as answered.
            val guesser = if (job.answerGuessSec > 0) {
                launch {
                    delay(job.answerGuessSec * 1000L)
                    if (inCallNow && phoneBusy.value) {
                        onEvent("Probably answered · call still going after ${job.answerGuessSec}s")
                        if (job.answerAction >= 1) {
                            holdCall()
                            if (job.answerAction >= 2) {
                                delay(1_700)   // let the audio keeper finish its last pass first
                                audio.apply(job.answerAction == 2, false)
                            }
                        }
                        onAnswerGuess(job.number, job.answerAction)
                    }
                }
            } else null

            var ended = false
            while (true) {
                val deadline = hangUpAt.value
                val remaining =
                    if (deadline == null) Long.MAX_VALUE
                    else (deadline - System.currentTimeMillis()).coerceAtLeast(0)

                val r = withTimeoutOrNull(remaining) {
                    combine(phoneBusy, hangUpAt) { busy, d -> busy to d }
                        .first { (busy, d) -> !busy || d != deadline }
                }
                if (r == null) { ended = false; break }       // timer reached: hang up
                if (!r.first) { ended = true; break }         // call ended by itself
                // otherwise the timer was held / re-armed: loop and recompute
            }
            mirror.cancel()
            keeper?.cancel()
            guesser?.cancel()
            ended
        }
        val ringSec = (System.currentTimeMillis() - callStartedAt) / 1000

        if (!endedByItself) {
            onEvent("Hanging up (time limit)")
            dialer.hangUp()
        }
        hangUpAt.value = null
        currentLimit = null
        inCallNow = false
        // Call is over: stop showing the old countdown while the line settles.
        _state.value = EngineState.InCall(p, null, false)

        awaitLineFree()
        audio.restore()
        delay(postCallPauseMs)

        // The call log is the only hint Android gives about whether anyone picked up.
        val duration = withContext(Dispatchers.IO) { callLog.lastOutgoingDurationSec(job.number, startedAt) }
        val outcome = when {
            duration >= answeredAfterSec -> AttemptOutcome.CONNECTED
            endedByItself && ringSec <= busyMaxSec -> AttemptOutcome.BUSY
            else -> AttemptOutcome.NO_ANSWER
        }
        onEvent(
            "Ended · ${duration}s · " + when (outcome) {
                AttemptOutcome.CONNECTED -> "answered"
                AttemptOutcome.BUSY -> "busy or declined"
                else -> "no answer"
            },
        )
        return AttemptResult(job.number, p.attempt, outcome, duration, simSlot = dialer.slot, ringSec = ringSec)
    }

    /** Pretend call: walks the same states and timing logic without touching the phone. */
    private suspend fun dryCall(job: RedialJob, p: Progress): AttemptResult {
        _state.value = EngineState.Dialing(p)
        onEvent("DRY · dialing ${job.number} · call ${p.step}/${p.steps}")
        delay(350)
        val limit = job.hangUpAfterMs
        _state.value = EngineState.InCall(p, limit?.let { System.currentTimeMillis() + 700 }, limit != null)
        delay(700)
        onEvent("DRY · ended")
        return AttemptResult(job.number, p.attempt, AttemptOutcome.COMPLETED, 0, simSlot = -1)
    }

    private fun failed(job: RedialJob, p: Progress, slot: Int) =
        AttemptResult(job.number, p.attempt, AttemptOutcome.FAILED_TO_START, simSlot = slot)

    /** Waits while the gate says wait. Returns a reason when the run should end, otherwise null. */
    private suspend fun awaitGate(): String? {
        var shown: String? = null
        while (true) {
            when (val g = gate()) {
                is Gate.Go -> {
                    if (shown != null) {
                        waitFlow.value = null
                        onEvent("Resumed")
                    }
                    return null
                }
                is Gate.Stop -> {
                    waitFlow.value = null
                    return g.reason
                }
                is Gate.Wait -> {
                    if (g.reason != shown) {
                        shown = g.reason
                        waitFlow.value = g.reason
                        onEvent("Waiting: ${g.reason}")
                    }
                    if (_state.value !is EngineState.Paused) _state.value = EngineState.Paused(_state.value)
                    delay(3_000)
                }
            }
        }
    }

    /** Before dialing: if you are on another call (or one is ringing), wait. We never hang up calls we didn't place. */
    private suspend fun waitUntilIdle() {
        if (dryRun) return
        if (phoneBusy.value || phoneRinging.value) {
            onEvent("Waiting for your other call to finish")
            combine(phoneBusy, phoneRinging) { a, b -> a || b }.first { !it }
        }
    }

    /** After we hung up: make sure the line really is free, nudging endCall() once more if needed. */
    private suspend fun awaitLineFree() {
        if (!phoneBusy.value) return
        val freed = withTimeoutOrNull(settleTimeoutMs) { phoneBusy.first { !it } } != null
        if (!freed) {
            dialers.first().hangUp()
            withTimeoutOrNull(settleTimeoutMs) { phoneBusy.first { !it } }
        }
    }

    private suspend fun cooldown(next: Progress, gapMs: Long) {
        val wait = if (dryRun) minOf(gapMs, 500L) else gapMs
        if (gapMs >= 2_000) onEvent("Next call in ${gapMs / 1000}s")
        _state.value = EngineState.Cooldown(next, System.currentTimeMillis() + wait)
        var remaining = wait
        while (remaining > 0) {
            if (pausedFlow.value) {
                _state.value = EngineState.Paused(_state.value)
                onEvent("Paused")
                pausedFlow.first { !it }
                onEvent("Resumed")
                _state.value = EngineState.Cooldown(next, System.currentTimeMillis() + remaining)
            }
            val step = minOf(250L, remaining)
            delay(step)
            remaining -= step
        }
    }

    private suspend fun awaitNotPaused() {
        if (pausedFlow.value) {
            val before = _state.value
            _state.value = EngineState.Paused(before)
            onEvent("Paused")
            pausedFlow.first { !it }
            onEvent("Resumed")
            _state.value = before
        }
    }
}
