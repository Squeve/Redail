package com.squeve.redail.data

import com.squeve.redail.model.CallOrder
import com.squeve.redail.model.RedialJob
import com.squeve.redail.model.RunPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoresTest {
    @Test
    fun planSurvivesAJsonRoundTrip() {
        val plan = RunPlan(
            jobs = listOf(
                RedialJob("123", 3, 2000L, 5000L, 30000L, true, 2, true, true),
                RedialJob("456", 1, 1000L, null, null),
            ),
            order = CallOrder.ROUND_ROBIN,
            simSlot = 1,
            dry = true,
        )
        assertEquals(plan, PlanCodec.fromJson(PlanCodec.toJson(plan)))
    }

    @Test
    fun remainingPlanKeepsOnlyUnfinishedCalls() {
        val plan = RunPlan(listOf(RedialJob("A", 3), RedialJob("B", 2)))
        val rest = RunStore.remainingPlan(Checkpoint(plan, listOf(1, 0)))
        assertEquals(listOf(2, 2), rest?.jobs?.map { it.attempts })
    }

    @Test
    fun finishedRunHasNothingLeftToResume() {
        val plan = RunPlan(listOf(RedialJob("A", 3), RedialJob("B", 2)))
        assertNull(RunStore.remainingPlan(Checkpoint(plan, listOf(3, 2))))
    }

    @Test
    fun checkpointCountsCalls() {
        val plan = RunPlan(listOf(RedialJob("A", 3), RedialJob("B", 2)))
        val cp = Checkpoint(plan, listOf(1, 2))
        assertEquals(5, cp.totalCalls)
        assertEquals(3, cp.doneCalls)
    }
}
