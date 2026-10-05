package com.squeve.redail.model

/** One number in the queue plus its own retry rules. */
data class RedialJob(
    val number: String,
    val attempts: Int = 3,
    val gapBetweenAttemptsMs: Long = 30_000,   // wait after each attempt
    val ringTimeoutMs: Long = 25_000,          // hang up if still ringing after this
    val stopOnConnected: Boolean = true,       // skip remaining attempts once answered
)

enum class AttemptOutcome { CONNECTED, NO_ANSWER, FAILED_TO_START, CANCELLED }

data class AttemptResult(
    val number: String,
    val attemptNo: Int,
    val outcome: AttemptOutcome,
    val durationSec: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
)

/** Phone-level events the engine cares about. */
enum class CallEvent { OFFHOOK, IDLE }

sealed interface EngineState {
    data object Idle : EngineState
    data class Dialing(val number: String, val attempt: Int, val total: Int) : EngineState
    data class InCall(val number: String, val attempt: Int, val total: Int) : EngineState
    data class Cooldown(val number: String, val nextAttempt: Int, val untilMs: Long) : EngineState
    data class Paused(val resumeTo: EngineState) : EngineState
    data class Finished(val results: List<AttemptResult>) : EngineState
}
