package com.squeve.redail.model

/** How the calls of all numbers are ordered. */
enum class CallOrder {
    SEQUENTIAL,    // number 1 x N, then number 2 x N, ...
    ROUND_ROBIN,   // 1, 2, 3, 1, 2, 3, ...
    RANDOM,        // all calls shuffled
}

/** One number in the queue plus its own rules. */
data class RedialJob(
    val number: String,
    val attempts: Int = 5,                    // number of calls to this number
    val gapMs: Long = 5_000,                  // interval between calls
    val hangUpAfterMs: Long? = 30_000,        // call duration; null = let the call run
    val stopOnConnected: Boolean = false,     // skip to next number once answered
    val speaker: Boolean = false,             // start the call on speakerphone
    val muteMic: Boolean = false,             // keep the microphone muted during the call
)

enum class AttemptOutcome { COMPLETED, CONNECTED, NO_ANSWER, FAILED_TO_START }

data class AttemptResult(
    val number: String,
    val attemptNo: Int,
    val outcome: AttemptOutcome,
    val durationSec: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * Where the run currently is.
 * attempt/total = this number's own counter, step/steps = position in the whole run,
 * completed = finished calls per queue entry.
 */
data class Progress(
    val jobIndex: Int,
    val jobCount: Int,
    val number: String,
    val attempt: Int,
    val total: Int,
    val step: Int,
    val steps: Int,
    val completed: List<Int>,
)

sealed interface EngineState {
    data object Idle : EngineState
    data class Dialing(val p: Progress) : EngineState

    /** hangUpAt = when the auto hang-up fires (null = timer off); hasLimit = a duration was configured. */
    data class InCall(val p: Progress, val hangUpAt: Long?, val hasLimit: Boolean) : EngineState
    data class Cooldown(val next: Progress, val untilMs: Long) : EngineState
    data class Paused(val last: EngineState) : EngineState
    data class Finished(val results: List<AttemptResult>) : EngineState
}

fun EngineState.progress(): Progress? = when (this) {
    is EngineState.Dialing -> p
    is EngineState.InCall -> p
    is EngineState.Cooldown -> next
    is EngineState.Paused -> last.progress()
    else -> null
}
