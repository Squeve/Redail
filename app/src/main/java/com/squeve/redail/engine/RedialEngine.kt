package com.squeve.redail.engine

import com.squeve.redail.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Strictly sequential: number 1 x N calls, then number 2 x N calls, and so on.
 * One call at a time. The next call is never placed until the phone line is idle again.
 */
class RedialEngine(
    private val dialer: Dialer,
    private val phoneBusy: StateFlow<Boolean>,
    private val callLog: CallLogReader,
    private val onResult: suspend (AttemptResult) -> Unit = {},
    private val startTimeoutMs: Long = 15_000,   // max wait for a placed call to actually begin
    private val settleTimeoutMs: Long = 8_000,   // max wait for the line to free up after hang up
    private val postCallPauseMs: Long = 800,     // let the telephony stack settle between calls
) {
    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val pausedFlow = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = pausedFlow.asStateFlow()

    private var runJob: Job? = null
    val isRunning: Boolean get() = runJob?.isActive == true

    fun start(scope: CoroutineScope, queue: List<RedialJob>) {
        if (isRunning || queue.isEmpty()) return
        pausedFlow.value = false
        runJob = scope.launch { run(queue) }
    }

    fun pause() { pausedFlow.value = true }
    fun resume() { pausedFlow.value = false }

    fun stop() {
        runJob?.cancel()
        runJob = null
        pausedFlow.value = false
        dialer.hangUp()
        _state.value = EngineState.Idle
    }

    private suspend fun run(queue: List<RedialJob>) {
        val results = mutableListOf<AttemptResult>()
        try {
            // Flat, ordered plan: all calls for number 1, then all calls for number 2, ...
            val plan = queue.flatMapIndexed { ji, job ->
                (1..job.attempts).map { a -> Progress(ji, queue.size, job.number, a, job.attempts) }
            }
            val skip = mutableSetOf<Int>()
            var first = true
            var lastGap = 0L

            for (p in plan) {
                if (p.jobIndex in skip) continue
                val job = queue[p.jobIndex]

                if (!first) cooldown(p, lastGap)
                first = false

                awaitNotPaused()
                waitUntilIdle()

                val r = dialOnce(job, p)
                results += r
                onResult(r)
                lastGap = job.gapMs

                if (r.outcome == AttemptOutcome.CONNECTED && job.stopOnConnected) skip += p.jobIndex
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

        if (!dialer.place(job.number)) return failed(job, p)

        // Wait for the call to really begin (line goes busy).
        val started = withTimeoutOrNull(startTimeoutMs) { phoneBusy.first { it } } != null
        if (!started) {
            dialer.hangUp()
            awaitLineFree()
            return failed(job, p)
        }

        _state.value = EngineState.InCall(p)

        // Let the call run until it ends, or hang up when the call duration is reached.
        val limit = job.hangUpAfterMs
        val endedByItself: Boolean = if (limit == null) {
            phoneBusy.first { !it }
            true
        } else {
            withTimeoutOrNull(limit) { phoneBusy.first { !it } } != null
        }
        if (!endedByItself) dialer.hangUp()

        awaitLineFree()
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
