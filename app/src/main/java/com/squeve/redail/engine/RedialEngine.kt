package com.squeve.redail.engine

import com.squeve.redail.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * One call at a time. The next call is never placed until the phone line is idle again.
 * Order is decided up front (one by one, round robin or shuffled).
 */
class RedialEngine(
    private val dialer: Dialer,
    private val phoneBusy: StateFlow<Boolean>,
    private val callLog: CallLogReader,
    private val audio: AudioControl = NoAudio,
    private val onResult: suspend (AttemptResult) -> Unit = {},
    private val startTimeoutMs: Long = 10_000,   // max wait for a placed call to actually begin
    private val settleTimeoutMs: Long = 8_000,   // max wait for the line to free up after hang up
    private val postCallPauseMs: Long = 800,     // let the telephony stack settle between calls
) {
    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val pausedFlow = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = pausedFlow.asStateFlow()

    /** When the auto hang-up fires for the call in progress. null = timer off. */
    private val hangUpAt = MutableStateFlow<Long?>(null)
    @Volatile private var currentLimit: Long? = null
    @Volatile private var inCallNow = false

    private var runJob: Job? = null
    val isRunning: Boolean get() = runJob?.isActive == true

    fun start(scope: CoroutineScope, queue: List<RedialJob>, order: CallOrder = CallOrder.SEQUENTIAL) {
        if (isRunning || queue.isEmpty()) return
        pausedFlow.value = false
        runJob = scope.launch { run(queue, order) }
    }

    fun pause() { pausedFlow.value = true }
    fun resume() { pausedFlow.value = false }

    /** Cancel the auto hang-up for the current call; it now ends by itself. */
    fun holdCall() {
        if (inCallNow) hangUpAt.value = null
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
        hangUpAt.value = null
        inCallNow = false
        dialer.hangUp()
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
            val skipped = BooleanArray(queue.size)

            fun progressAt(s: Int): Progress {
                val ji = slots[s]
                val job = queue[ji]
                return Progress(ji, queue.size, job.number, attemptNo[s], job.attempts, s + 1, steps, done.toList())
            }

            var first = true
            var lastGap = 0L

            for (s in 0 until steps) {
                val ji = slots[s]
                if (skipped[ji]) continue
                val job = queue[ji]

                if (!first) cooldown(progressAt(s), lastGap)
                first = false

                awaitNotPaused()
                waitUntilIdle()

                val r = dialOnce(job, progressAt(s))
                results += r
                onResult(r)

                done[ji]++
                lastGap = job.gapMs
                if (r.outcome == AttemptOutcome.CONNECTED && job.stopOnConnected) {
                    skipped[ji] = true
                    done[ji] = job.attempts
                }
            }

            pausedFlow.value = false
            _state.value = EngineState.Finished(results)
        } catch (e: CancellationException) {
            _state.value = EngineState.Idle
            throw e
        }
    }

    private suspend fun dialOnce(job: RedialJob, p: Progress): AttemptResult {
        val startedAt = System.currentTimeMillis()
        _state.value = EngineState.Dialing(p)

        if (!dialer.place(job.number, job.speaker)) return failed(job, p)

        // Wait for the call to really begin (line goes busy).
        val started = withTimeoutOrNull(startTimeoutMs) { phoneBusy.first { it } } != null
        if (!started) {
            dialer.hangUp()
            awaitLineFree()
            return failed(job, p)
        }

        val limit = job.hangUpAfterMs
        currentLimit = limit
        hangUpAt.value = limit?.let { System.currentTimeMillis() + it }
        inCallNow = true

        val endedByItself: Boolean = coroutineScope {
            // Keeps the published state in step with the hang-up timer (armed, held, re-armed).
            val mirror = launch {
                hangUpAt.collect { at -> _state.value = EngineState.InCall(p, at, limit != null) }
            }
            // Re-apply speaker/mute while the call runs; the phone resets audio when the call connects.
            val keeper = if (job.speaker || job.muteMic) {
                launch {
                    while (true) {
                        audio.apply(job.speaker, job.muteMic)
                        delay(1_500)
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
            ended
        }

        if (!endedByItself) dialer.hangUp()
        hangUpAt.value = null
        currentLimit = null
        inCallNow = false
        // Call is over: stop showing the old countdown while the line settles.
        _state.value = EngineState.InCall(p, null, false)

        awaitLineFree()
        audio.restore()
        delay(postCallPauseMs)

        var duration = 0L
        val outcome = if (job.stopOnConnected) {
            duration = withContext(Dispatchers.IO) { callLog.lastOutgoingDurationSec(job.number, startedAt) }
            if (duration > 0) AttemptOutcome.CONNECTED else AttemptOutcome.NO_ANSWER
        } else {
            AttemptOutcome.COMPLETED
        }
        return AttemptResult(job.number, p.attempt, outcome, duration)
    }

    private fun failed(job: RedialJob, p: Progress) =
        AttemptResult(job.number, p.attempt, AttemptOutcome.FAILED_TO_START)

    /** Before dialing: if some other call is active, wait for it. Never hang up a call we didn't place. */
    private suspend fun waitUntilIdle() {
        if (phoneBusy.value) phoneBusy.first { !it }
    }

    /** After we hung up: make sure the line really is free, nudging endCall() once more if needed. */
    private suspend fun awaitLineFree() {
        if (!phoneBusy.value) return
        val freed = withTimeoutOrNull(settleTimeoutMs) { phoneBusy.first { !it } } != null
        if (!freed) {
            dialer.hangUp()
            withTimeoutOrNull(settleTimeoutMs) { phoneBusy.first { !it } }
        }
    }

    private suspend fun cooldown(next: Progress, gapMs: Long) {
        _state.value = EngineState.Cooldown(next, System.currentTimeMillis() + gapMs)
        var remaining = gapMs
        while (remaining > 0) {
            if (pausedFlow.value) {
                _state.value = EngineState.Paused(_state.value)
                pausedFlow.first { !it }
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
            pausedFlow.first { !it }
            _state.value = before
        }
    }
}
