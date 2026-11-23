package com.drumtuner.app.dsp

import kotlin.math.log10

enum class WireZone(val label: String) { RATTLY("Rattly"), CRISP("Crisp Sweet Spot"), CHOKED("Choked") }

data class WireReading(
    /** E(3–8 kHz) / E(150–450 Hz). */
    val ratio: Double,
    val ratioDb: Double,
    val zone: WireZone,
    /** 0 = fully choked … 1 = fully rattly, for the gauge needle. */
    val gaugePosition: Float,
)

/**
 * Snare Wire Tension Analyzer.
 *
 * Loose wires keep buzzing long after the hit, so the 3–8 kHz "sizzle" band carries a lot of
 * energy relative to the 150–450 Hz body of the drum (Rattly). Over-tightened wires choke the
 * resonant head and the sizzle collapses (Choked). The sweet spot sits in between.
 */
object SnareWire {
    const val SIZZLE_LOW_HZ = 3_000.0
    const val SIZZLE_HIGH_HZ = 8_000.0
    const val BODY_LOW_HZ = 150.0
    const val BODY_HIGH_HZ = 450.0

    /** Ratio (dB) above which the wires are considered rattly. */
    const val RATTLY_ABOVE_DB = -8.0
    /** Ratio (dB) below which the wires are considered choked. */
    const val CHOKED_BELOW_DB = -22.0

    private const val GAUGE_MIN_DB = -32.0
    private const val GAUGE_MAX_DB = 2.0

    fun sizzleRatio(analyzer: SpectrumAnalyzer): Double {
        val body = analyzer.bandEnergy(BODY_LOW_HZ, BODY_HIGH_HZ)
        val sizzle = analyzer.bandEnergy(SIZZLE_LOW_HZ, SIZZLE_HIGH_HZ)
        return if (body <= 0.0) 0.0 else sizzle / body
    }

    fun classify(ratio: Double): WireReading {
        val db = if (ratio <= 0.0) -120.0 else 10.0 * log10(ratio)
        val zone = when {
            db > RATTLY_ABOVE_DB -> WireZone.RATTLY
            db < CHOKED_BELOW_DB -> WireZone.CHOKED
            else -> WireZone.CRISP
        }
        val pos = ((db - GAUGE_MIN_DB) / (GAUGE_MAX_DB - GAUGE_MIN_DB)).coerceIn(0.0, 1.0).toFloat()
        return WireReading(ratio, db, zone, pos)
    }
}
