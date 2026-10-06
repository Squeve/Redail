package com.squeve.redail.engine

/** Android-facing pieces hidden behind interfaces so the engine is unit-testable. */
interface Dialer {
    fun place(number: String, speaker: Boolean = false): Boolean   // false if the call couldn't be started
    fun hangUp(): Boolean
}

interface CallLogReader {
    /** Duration in seconds of the most recent outgoing call to [number] placed after [sinceMs]. */
    fun lastOutgoingDurationSec(number: String, sinceMs: Long): Long
}

/** Speaker / mute control while a call is active. */
interface AudioControl {
    fun apply(speaker: Boolean, mute: Boolean)
    fun restore()
}

object NoAudio : AudioControl {
    override fun apply(speaker: Boolean, mute: Boolean) {}
    override fun restore() {}
}
