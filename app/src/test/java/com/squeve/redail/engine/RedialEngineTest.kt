package com.squeve.redail.engine

import com.squeve.redail.model.AttemptOutcome
import com.squeve.redail.model.AttemptResult
import com.squeve.redail.model.CallOrder
import com.squeve.redail.model.EngineState
import com.squeve.redail.model.RedialJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    private fun job(number: String, calls: Int) =
        RedialJob(number = number, attempts = calls, gapMs = 1_000, hangUpAfterMs = 5_000)

    private fun engine(
        dialer: Dialer = FakeDialer(),
        dry: Boolean = true,
        results: MutableList<AttemptResult> = mutableListOf(),
        checkpoints: MutableList<List<Int>> = mutableListOf(),
        gate: () -> Gate = { Gate.Go },
    ) = RedialEngine(
        dialers = listOf(dialer),
        phoneBusy = MutableStateFlow(false),
        phoneRinging = MutableStateFlow(false),
        callLog = NoLog,
        onResult = { results += it },
        onCheckpoint = { checkpoints += it },
        gate = gate,
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
}
