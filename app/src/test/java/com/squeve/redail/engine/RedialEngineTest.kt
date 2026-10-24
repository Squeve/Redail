package com.squeve.redail.engine

import com.squeve.redail.model.AttemptOutcome
import com.squeve.redail.model.AttemptResult
import com.squeve.redail.model.CallOrder
import com.squeve.redail.model.EngineState
import com.squeve.redail.model.RedialJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dry runs exercise ordering, gates and checkpoints with no phone involved. */
@OptIn(ExperimentalCoroutinesApi::class)
class RedialEngineTest {

    private class FakeDialer(private val canPlace: Boolean = true) : Dialer {
        val placed = mutableListOf<String>()
        var hangUps = 0
        override fun place(number: String, speaker: Boolean): Boolean {
            placed += number
            return canPlace
        }
        override fun hangUp(): Boolean {
            hangUps++
            return true
        }
    }

    private object NoLog : CallLogReader {
        override fun lastOutgoingDurationSec(number: String, sinceMs: Long): Long = 0L
    }

    private class FixedLog(private val seconds: Long) : CallLogReader {
        override fun lastOutgoingDurationSec(number: String, sinceMs: Long): Long = seconds
    }

    /** A phone line that goes busy when a call is placed and frees up by itself after a moment. */
    private class LineDialer(
        private val scope: CoroutineScope,
        private val busy: MutableStateFlow<Boolean>,
        private val endsAfterMs: Long,
    ) : Dialer {
        override fun place(number: String, speaker: Boolean): Boolean {
            busy.value = true
            scope.launch {
                delay(endsAfterMs)
                busy.value = false
            }
            return true
        }
        override fun hangUp(): Boolean {
            busy.value = false
            return true
        }
    }

    private fun job(number: String, calls: Int) =
        RedialJob(number = number, attempts = calls, gapMs = 1_000, hangUpAfterMs = 5_000)

    private fun engine(
        dialer: Dialer = FakeDialer(),
        dry: Boolean = true,
        results: MutableList<AttemptResult> = mutableListOf(),
        checkpoints: MutableList<List<Int>> = mutableListOf(),
        gate: () -> Gate = { Gate.Go },
        isHeld: (String) -> Boolean = { false },
        busy: MutableStateFlow<Boolean> = MutableStateFlow(false),
        callLog: CallLogReader = NoLog,
        busyMaxSec: Long = 15,
    ) = RedialEngine(
        dialers = listOf(dialer),
        phoneBusy = busy,
        phoneRinging = MutableStateFlow(false),
        callLog = callLog,
        busyMaxSec = busyMaxSec,
        onResult = { results += it },
        onCheckpoint = { checkpoints += it },
        gate = gate,
        isHeld = isHeld,
        dryRun = dry,
        startTimeoutMs = 100,
        settleTimeoutMs = 100,
        postCallPauseMs = 0,
    )

    @Test
    fun sequentialCallsEachNumberInFull() = runTest {
        val results = mutableListOf<AttemptResult>()
        val e = engine(results = results)
        e.start(this, listOf(job("A", 2), job("B", 1)), CallOrder.SEQUENTIAL)
        advanceUntilIdle()
        assertEquals(listOf("A", "A", "B"), results.map { it.number })
        assertTrue(e.state.value is EngineState.Finished)
    }

    @Test
    fun roundRobinTakesTurns() = runTest {
        val results = mutableListOf<AttemptResult>()
        val e = engine(results = results)
        e.start(this, listOf(job("A", 2), job("B", 3), job("C", 1)), CallOrder.ROUND_ROBIN)
        advanceUntilIdle()
        assertEquals(listOf("A", "B", "C", "A", "B", "B"), results.map { it.number })
    }

    @Test
    fun randomOrderKeepsEveryCall() = runTest {
        val results = mutableListOf<AttemptResult>()
        val e = engine(results = results)
        e.start(this, listOf(job("A", 3), job("B", 2)), CallOrder.RANDOM)
        advanceUntilIdle()
        assertEquals(listOf("A", "A", "A", "B", "B"), results.map { it.number }.sorted())
    }

    @Test
    fun gateStopEndsTheRunEarly() = runTest {
        val results = mutableListOf<AttemptResult>()
        var checks = 0
        val e = engine(results = results, gate = { if (checks++ >= 2) Gate.Stop("daily cap") else Gate.Go })
        e.start(this, listOf(job("A", 5)), CallOrder.SEQUENTIAL)
        advanceUntilIdle()
        assertEquals(2, results.size)
        assertEquals("daily cap", e.stopReason)
        assertTrue(e.state.value is EngineState.Finished)
    }

    @Test
    fun checkpointTracksCompletedCalls() = runTest {
        val checkpoints = mutableListOf<List<Int>>()
        val e = engine(checkpoints = checkpoints)
        e.start(this, listOf(job("A", 2), job("B", 1)), CallOrder.SEQUENTIAL)
        advanceUntilIdle()
        assertEquals(3, checkpoints.size)
        assertEquals(listOf(1, 0), checkpoints.first())
        assertEquals(listOf(2, 1), checkpoints.last())
    }

    @Test
    fun callThatNeverStartsIsReportedAsFailed() = runTest {
        val results = mutableListOf<AttemptResult>()
        val dialer = FakeDialer(canPlace = true)
        val e = engine(dialer = dialer, dry = false, results = results)
        e.start(this, listOf(job("A", 1)), CallOrder.SEQUENTIAL)
        advanceUntilIdle()
        assertEquals(1, results.size)
        assertEquals(AttemptOutcome.FAILED_TO_START, results.first().outcome)
        assertEquals(1, dialer.hangUps)
    }

    @Test
    fun dialerThatRefusesIsReportedAsFailed() = runTest {
        val results = mutableListOf<AttemptResult>()
        val dialer = FakeDialer(canPlace = false)
        val e = engine(dialer = dialer, dry = false, results = results)
        e.start(this, listOf(job("A", 1)), CallOrder.SEQUENTIAL)
        advanceUntilIdle()
        assertEquals(listOf("A"), dialer.placed)
        assertEquals(AttemptOutcome.FAILED_TO_START, results.first().outcome)
    }

    @Test
    fun heldNumberIsPassedOverUntilItIsReleased() = runTest {
        val results = mutableListOf<AttemptResult>()
        // A stays on hold until B has been called.
        val e = engine(results = results, isHeld = { n -> n == "A" && results.none { it.number == "B" } })
        e.start(this, listOf(job("A", 2), job("B", 1)), CallOrder.SEQUENTIAL)
        advanceUntilIdle()
        assertEquals(listOf("B", "A", "A"), results.map { it.number })
        assertTrue(e.state.value is EngineState.Finished)
    }

    @Test
    fun callThatEndsFastWithoutAnswerIsBusy() = runTest {
        val results = mutableListOf<AttemptResult>()
        val busy = MutableStateFlow(false)
        val e = engine(dialer = LineDialer(this, busy, 3_000), dry = false, results = results, busy = busy)
        e.start(this, listOf(job("A", 1)), CallOrder.SEQUENTIAL)
        advanceUntilIdle()
        e.state.first { it is EngineState.Finished }
        assertEquals(AttemptOutcome.BUSY, results.single().outcome)
    }

    @Test
    fun callThatRingsLongWithoutAnswerIsNoAnswer() = runTest {
        val results = mutableListOf<AttemptResult>()
        val busy = MutableStateFlow(false)
        // busyMaxSec -1 means "no call ever counts as a quick end", standing in for a long ring.
        val e = engine(dialer = LineDialer(this, busy, 3_000), dry = false, results = results, busy = busy, busyMaxSec = -1)
        e.start(this, listOf(job("A", 1)), CallOrder.SEQUENTIAL)
        advanceUntilIdle()
        e.state.first { it is EngineState.Finished }
        assertEquals(AttemptOutcome.NO_ANSWER, results.single().outcome)
    }

    @Test
    fun callLogTimeMeansAnswered() = runTest {
        val results = mutableListOf<AttemptResult>()
        val busy = MutableStateFlow(false)
        val e = engine(
            dialer = LineDialer(this, busy, 3_000), dry = false, results = results, busy = busy,
            callLog = FixedLog(42),
        )
        e.start(this, listOf(job("A", 1)), CallOrder.SEQUENTIAL)
        advanceUntilIdle()
        e.state.first { it is EngineState.Finished }
        assertEquals(AttemptOutcome.CONNECTED, results.single().outcome)
        assertEquals(42L, results.single().durationSec)
    }

    @Test
    fun numberIsGivenUpOnceItsTimeLimitIsUsed() = runTest {
        val results = mutableListOf<AttemptResult>()
        val e = engine(results = results)
        val j = RedialJob("A", 3, 1_000, hangUpAfterMs = 5_000, giveUpAfterMs = 0)
        e.start(this, listOf(j), CallOrder.SEQUENTIAL)
        advanceUntilIdle()
        assertEquals(1, results.size)
        assertTrue(e.state.value is EngineState.Finished)
    }
}
