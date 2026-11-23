package com.drumtuner.app.tuning

enum class DrumType(val label: String) {
    SNARE("Snare"),
    TOM1("Tom 1"),
    TOM2("Tom 2"),
    FLOOR("Floor Tom"),
    KICK("Kick");

    val isTom: Boolean get() = this == TOM1 || this == TOM2 || this == FLOOR
    val isRackTom: Boolean get() = this == TOM1 || this == TOM2
}

enum class HeadSide(val label: String) {
    BATTER("Batter (Top)"),
    RESONANT("Resonant (Bottom)"),
}

/** A frequency search band in Hz. */
data class Band(val lowHz: Double, val highHz: Double) {
    val width: Double get() = highHz - lowHz
    operator fun contains(hz: Double): Boolean = hz in lowHz..highHz

    fun intersect(other: Band): Band? {
        val lo = maxOf(lowHz, other.lowHz)
        val hi = minOf(highHz, other.highHz)
        return if (hi > lo) Band(lo, hi) else null
    }
}

/** Expected f11 (lug overtone) search bands per drum. */
object DetectionBands {
    val KICK = Band(45.0, 160.0)
    val FLOOR_TOM = Band(80.0, 260.0)
    val RACK_TOM = Band(110.0, 420.0)
    val SNARE_BATTER = Band(150.0, 500.0)
    val SNARE_RESONANT = Band(250.0, 650.0)

    fun lugBand(drum: DrumType, side: HeadSide): Band = when (drum) {
        DrumType.KICK -> KICK
        DrumType.FLOOR -> FLOOR_TOM
        DrumType.TOM1, DrumType.TOM2 -> RACK_TOM
        DrumType.SNARE -> if (side == HeadSide.BATTER) SNARE_BATTER else SNARE_RESONANT
    }

    /**
     * Narrows the drum's band around a known target/baseline (±30 %, ≈ ±4.5 semitones) so that
     * the f01 fundamental or a strong neighbouring mode cannot be mistaken for the f11 lug tone.
     * If the target sits outside the nominal band, the target window wins.
     */
    fun trackingBand(base: Band, targetHz: Double?): Band {
        if (targetHz == null || !(targetHz > 0.0)) return base
        val window = Band(targetHz / 1.3, targetHz * 1.3)
        val inter = base.intersect(window)
        return if (inter != null && inter.width >= window.width * 0.3) inter else window
    }

    /** Band for a centre hit (fundamental f01) during the line check. */
    fun fundamentalBand(drum: DrumType, targetHz: Double?): Band {
        if (targetHz != null && targetHz > 0.0) return Band(targetHz / 1.35, targetHz * 1.35)
        val lug = lugBand(drum, HeadSide.BATTER)
        return Band(lug.lowHz / DrumAcoustics.F11_OVER_F01 * 0.8, lug.highHz / DrumAcoustics.F11_OVER_F01 * 1.1)
    }
}
