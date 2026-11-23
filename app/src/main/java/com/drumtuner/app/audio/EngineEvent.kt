package com.drumtuner.app.audio

import com.drumtuner.app.tuning.Band

sealed interface EngineEvent {
    /** A hit was captured and analysed. [frequencyHz] is null if no clear peak was found in the band. */
    data class Hit(
        val id: Long,
        val frequencyHz: Double?,
        val peakLevelDb: Double?,
        val sizzleRatio: Double,
        val band: Band,
    ) : EngineEvent

    /** Sustain for hit [id] (20 dB decay time). */
    data class Decay(val id: Long, val decayMs: Double) : EngineEvent
}
