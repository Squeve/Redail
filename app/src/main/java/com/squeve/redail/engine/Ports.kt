package com.squeve.redail.engine

import com.squeve.redail.model.CallEvent
import kotlinx.coroutines.flow.Flow

/** Android-facing pieces hidden behind interfaces so the engine is unit-testable. */
interface Dialer {
    fun place(number: String): Boolean   // false if the call couldn't be started
    fun hangUp(): Boolean
}

interface CallStateSource {
    val events: Flow<CallEvent>
}

interface CallLogReader {
    /** Duration in seconds of the most recent outgoing call to [number] placed after [sinceMs]. */
    fun lastOutgoingDurationSec(number: String, sinceMs: Long): Long
}
