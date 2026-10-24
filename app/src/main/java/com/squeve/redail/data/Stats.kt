package com.squeve.redail.data

import java.util.Calendar
import java.util.TimeZone

data class NumberStat(val number: String, val calls: Int, val answered: Int) {
    val ratePct: Int get() = if (calls == 0) 0 else answered * 100 / calls
}

data class HourStat(val hour: Int, val calls: Int, val answered: Int) {
    val ratePct: Int get() = if (calls == 0) 0 else answered * 100 / calls
}

data class Stats(
    val calls: Int,
    val answered: Int,
    val busy: Int,
    val noAnswer: Int,
    val failed: Int,
    val perNumber: List<NumberStat>,
    val perHour: List<HourStat>,
) {
    val ratePct: Int get() = if (calls == 0) 0 else answered * 100 / calls

    /** The hour with the best answer rate, among hours with at least [minCalls] calls. */
    fun bestHour(minCalls: Int = 3): HourStat? =
        perHour.filter { it.calls >= minCalls }.maxWithOrNull(compareBy({ it.ratePct }, { it.calls }))
}

/** Answer rates from the call history: overall, per number (most called first) and per hour of the day. */
fun computeStats(records: List<HistoryRecord>, zone: TimeZone = TimeZone.getDefault()): Stats {
    // Dry runs never reach the history, but a "COMPLETED" record would not be a real answer either.
    val real = records.filter { it.outcome != "COMPLETED" }
    val perNumber = real.groupBy { it.number }.map { (n, list) ->
        NumberStat(n, list.size, list.count { it.outcome == "CONNECTED" })
    }.sortedWith(compareByDescending<NumberStat> { it.calls }.thenBy { it.number })

    val hours = IntArray(24)
    val hoursAnswered = IntArray(24)
    val cal = Calendar.getInstance(zone)
    real.forEach { r ->
        cal.timeInMillis = r.ts
        val h = cal.get(Calendar.HOUR_OF_DAY)
        hours[h]++
        if (r.outcome == "CONNECTED") hoursAnswered[h]++
    }
    val perHour = (0 until 24).filter { hours[it] > 0 }.map { HourStat(it, hours[it], hoursAnswered[it]) }

    return Stats(
        calls = real.size,
        answered = real.count { it.outcome == "CONNECTED" },
        busy = real.count { it.outcome == "BUSY" },
        noAnswer = real.count { it.outcome == "NO_ANSWER" },
        failed = real.count { it.outcome == "FAILED_TO_START" },
        perNumber = perNumber,
        perHour = perHour,
    )
}

/**
 * How long unanswered calls usually ring before the network gives up, plus a little margin.
 * A call still going after this long was very probably answered. Null when there is too little history.
 */
fun learnRingOut(records: List<HistoryRecord>, minSamples: Int = 5): Int? {
    val samples = records
        .filter { it.outcome == "NO_ANSWER" && it.durationSec == 0L && it.ringSec >= 15 }
        .map { it.ringSec }
        .sorted()
    if (samples.size < minSamples) return null
    val p90 = samples[((samples.size - 1) * 0.9).toInt()]
    return (p90 + 3).toInt().coerceIn(15, 120)
}
