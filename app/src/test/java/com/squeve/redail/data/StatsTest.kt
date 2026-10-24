package com.squeve.redail.data

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatsTest {
    private val utc = TimeZone.getTimeZone("UTC")

    private fun rec(hour: Int, minute: Int, number: String, outcome: String, ring: Long = 0, dur: Long = 0) =
        HistoryRecord((hour * 60L + minute) * 60_000L, number, "", outcome, dur, -1, ring)

    @Test
    fun answerRateOverallAndPerNumber() {
        val s = computeStats(
            listOf(
                rec(10, 0, "A", "CONNECTED"), rec(10, 30, "A", "NO_ANSWER"),
                rec(14, 0, "B", "BUSY"), rec(14, 10, "B", "CONNECTED"), rec(14, 20, "C", "CONNECTED"),
            ),
            utc,
        )
        assertEquals(5, s.calls)
        assertEquals(3, s.answered)
        assertEquals(60, s.ratePct)
        assertEquals(1, s.busy)
        assertEquals(1, s.noAnswer)
        assertEquals(listOf("A", "B", "C"), s.perNumber.map { it.number })
        assertEquals(50, s.perNumber.first().ratePct)
    }

    @Test
    fun bestHourNeedsEnoughCalls() {
        val s = computeStats(
            listOf(
                rec(10, 0, "A", "CONNECTED"), rec(10, 30, "A", "NO_ANSWER"),
                rec(14, 0, "B", "BUSY"), rec(14, 10, "B", "CONNECTED"), rec(14, 20, "C", "CONNECTED"),
            ),
            utc,
        )
        assertEquals(14, s.bestHour(minCalls = 2)?.hour)
        assertNull(s.bestHour(minCalls = 10))
    }

    @Test
    fun ringOutIsLearnedFromUnansweredCalls() {
        val list = listOf(30L, 32L, 35L, 40L, 45L).mapIndexed { i, r -> rec(9, i, "A", "NO_ANSWER", ring = r) }
        assertEquals(43, learnRingOut(list))
    }

    @Test
    fun tooFewSamplesLearnNothing() {
        val list = listOf(30L, 32L).mapIndexed { i, r -> rec(9, i, "A", "NO_ANSWER", ring = r) }
        assertNull(learnRingOut(list))
    }

    @Test
    fun quickEndsAreNotUsedForRingOut() {
        val list = (0 until 6).map { rec(9, it, "A", "NO_ANSWER", ring = 6) }
        assertNull(learnRingOut(list))
    }
}
