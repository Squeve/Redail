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
    val gapMaxMs: Long? = null,               // when set, the interval is random between gapMs and gapMaxMs
    val hangUpAfterMs: Long? = 30_000,        // call duration; null = let the call run
    val stopOnConnected: Boolean = false,     // skip to the next number once answered
    val stopAfterAnswers: Int = 1,            // how many answers count as "answered"
    val speaker: Boolean = false,             // start the call on speakerphone
    val muteMic: Boolean = false,             // keep the microphone muted during the call
    val gapBusyMs: Long? = null,              // interval after a busy or declined call; null = the normal interval
    val gapNoAnswerMs: Long? = null,          // interval after a call that rang out; null = the normal interval
    val giveUpAfterMs: Long? = null,          // stop trying a number this long after its first call
    val answerGuessSec: Int = 0,              // a call still going after this long is taken as answered (0 = off)
    val answerAction: Int = 0,                // 0 alert only, 1 + keep call and unmute, 2 + speaker, 3 + earpiece
)

/** Everything needed to start (or later resume / schedule) a run. */
data class RunPlan(
    val jobs: List<RedialJob>,
    val order: CallOrder = CallOrder.SEQUENTIAL,
    val simSlot: Int = -1,                    // -1 = phone default
    val dry: Boolean = false,                 // simulate only, no real calls
)

enum class AttemptOutcome { COMPLETED, CONNECTED, NO_ANSWER, FAILED_TO_START, BUSY }

data class AttemptResult(
    val number: String,
    val attemptNo: Int,
    val outcome: AttemptOutcome,
    val durationSec: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val simSlot: Int = -1,
    val ringSec: Long = 0,                    // seconds from the line going busy until the call ended
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
    data class InCall(
        val p: Progress,
        val hangUpAt: Long?,
        val hasLimit: Boolean,
        /** The microphone is currently being held muted by this run. */
        val micMuted: Boolean = false,
    ) : EngineState
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
