package com.squeve.redail.engine

import com.squeve.redail.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Core loop: for each job -> N attempts -> dial, wait for ring timeout, hang up, cool down.
 * Pure Kotlin + coroutines, no Android classes, so it can be tested on the JVM.
 */
class RedialEngine(
    private val dialer: Dialer,
    private val callState: CallStateSource,
    private val callLog: CallLogReader,
    private val onResult: suspend (AttemptResult) -> Unit = {},
    private val startTimeoutMs: Long = 15_000,   // max wait for the call to actually begin
    private val hangupSettleMs: Long = 3_000,    // wait for IDLE after endCall()
) {
    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val paused = MutableStateFlow(false)
    private var runJob: Job? = null

    fun start(scope: CoroutineScope, queue: List<RedialJob>) {
        if (runJob?.isActive == true) return
        paused.value = false
        runJob = scope.launch { run(queue) }
    }

    fun pause() { paused.value = true }
    fun resume() { paused.value = false }

    fun stop() {
        runJob?.cancel()
        dialer.hangUp()
        _state.value = EngineState.Idle
    }

    private suspend fun run(queue: List<RedialJob>) {
        val results = mutableListOf<AttemptResult>()
        try {
            for (job in queue) {
                for (attempt in 1..job.attempts) {
                    awaitNotPaused()
                    val result = dialOnce(job, attempt)
                    results += result
                    onResult(result)

                    if (result.outcome == AttemptOutcome.CONNECTED && job.stopOnConnected) break
                    if (attempt < job.attempts) cooldown(job, attempt + 1)
                }
            }
            _state.value = EngineState.Finished(results)
        } catch (e: CancellationException) {
            _state.value = EngineState.Idle
            throw e
        }
    }

    private suspend fun dialOnce(job: RedialJob, attempt: Int): AttemptResult = coroutineScope {
        val startedAt = System.currentTimeMillis()
        _state.value = EngineState.Dialing(job.number, attempt, job.attempts)

        // Subscribe BEFORE dialing so we can't miss the first event.
        val offhook = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(startTimeoutMs) { callState.events.first { it == CallEvent.OFFHOOK } }
        }

        if (!dialer.place(job.number)) {
            offhook.cancel()
            return@coroutineScope AttemptResult(job.number, attempt, AttemptOutcome.FAILED_TO_START)
        }
        if (offhook.await() == null) {
            return@coroutineScope AttemptResult(job.number, attempt, AttemptOutcome.FAILED_TO_START)
        }

        _state.value = EngineState.InCall(job.number, attempt, job.attempts)

        // Wait for the call to end on its own; otherwise hang up at the ring timeout.
        val endedByItself = withTimeoutOrNull(job.ringTimeoutMs) {
            callState.events.first { it == CallEvent.IDLE }
        } != null

        if (!endedByItself) {
            dialer.hangUp()
            withTimeoutOrNull(hangupSettleMs) { callState.events.first { it == CallEvent.IDLE } }
        }

        // Android has no "answered" signal; call-log duration > 0 is the best heuristic.
        val duration = callLog.lastOutgoingDurationSec(job.number, startedAt)
        AttemptResult(
            number = job.number,
            attemptNo = attempt,
            outcome = if (duration > 0) AttemptOutcome.CONNECTED else AttemptOutcome.NO_ANSWER,
            durationSec = duration,
        )
    }

    private suspend fun cooldown(job: RedialJob, nextAttempt: Int) {
        val until = System.currentTimeMillis() + job.gapBetweenAttemptsMs
        _state.value = EngineState.Cooldown(job.number, nextAttempt, until)
        var remaining = job.gapBetweenAttemptsMs
        while (remaining > 0) {   // tick so pause freezes the countdown
            awaitNotPaused()
            val step = minOf(500L, remaining)
            delay(step)
            remaining -= step
        }
    }

    private suspend fun awaitNotPaused() {
        if (paused.value) {
            val before = _state.value
            _state.value = EngineState.Paused(before)
            paused.first { !it }
            _state.value = before
        }
    }
}
