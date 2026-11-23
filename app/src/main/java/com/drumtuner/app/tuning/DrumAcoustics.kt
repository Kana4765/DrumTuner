package com.drumtuner.app.tuning

import com.drumtuner.app.dsp.PitchMath
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

enum class HeadPly(val label: String, val lugFactor: Double) {
    /** Single-ply 10 mil: reference. */
    ONE_PLY_10("1-Ply 10mil", 1.00),
    /**
     * Two-ply 14 mil: the extra mass and inter-ply damping lower the overtone ratio,
     * so the same fundamental sits ~3 % (≈ 53 ¢) lower at the lug.
     */
    TWO_PLY_14("2-Ply 14mil", 0.97),
}

enum class TuningStyle(val label: String, val resonantRatio: Double) {
    /** Resonant head 12 % above batter: shorter pitch-bend-down, punchy attack. */
    PUNCHY("Punchy Rock/Metal", 1.12),
    /** Both heads at the same pitch: longest sustain. */
    MAX_SUSTAIN("Max Sustain", 1.00),
}

enum class ScaleType(val intervals: IntArray) {
    MINOR(intArrayOf(0, 2, 3, 5, 7, 8, 10)),
    MAJOR(intArrayOf(0, 2, 4, 5, 7, 9, 11)),
}

enum class BandKey(val label: String, val rootPitchClass: Int, val scale: ScaleType) {
    E_MINOR("E Minor", 4, ScaleType.MINOR),
    D_MINOR("D Minor", 2, ScaleType.MINOR),
    C_SHARP_MINOR("C# Minor", 1, ScaleType.MINOR),
    DROP_C("Drop C", 0, ScaleType.MINOR),
    DROP_D("Drop D", 2, ScaleType.MINOR),
    A_MINOR("A Minor", 9, ScaleType.MINOR),
    G_MINOR("G Minor", 7, ScaleType.MINOR),
    B_MINOR("B Minor", 11, ScaleType.MINOR),
    F_SHARP_MINOR("F# Minor", 6, ScaleType.MINOR),
    F_MINOR("F Minor", 5, ScaleType.MINOR),
    C_MINOR("C Minor", 0, ScaleType.MINOR),
    E_MAJOR("E Major", 4, ScaleType.MAJOR),
    A_MAJOR("A Major", 9, ScaleType.MAJOR),
    G_MAJOR("G Major", 7, ScaleType.MAJOR),
    D_MAJOR("D Major", 2, ScaleType.MAJOR),
    C_MAJOR("C Major", 0, ScaleType.MAJOR);

    val fifthPitchClass: Int get() = (rootPitchClass + 7) % 12

    fun contains(midi: Int): Boolean {
        val rel = Math.floorMod(midi - rootPitchClass, 12)
        return scale.intervals.contains(rel)
    }
}

/** One registered drum shell. */
data class Shell(
    val drum: DrumType,
    val diameterIn: Double,
    val depthIn: Double,
    val ply: HeadPly = HeadPly.ONE_PLY_10,
    val lugCount: Int,
    val enabled: Boolean = true,
    /** Manual offset applied to the target fundamental (sympathetic buzz nudge). */
    val nudgeHz: Double = 0.0,
    /** Reference centre-hit pitch saved from a line check. */
    val refPitchHz: Double? = null,
    /** Reference sustain saved from a line check. */
    val refDecayMs: Double? = null,
) {
    val sizeLabel: String get() = "${fmt(diameterIn)}\"x${fmt(depthIn)}\""

    private fun fmt(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
}

data class BuzzWarning(
    /** e.g. "E (root)" */
    val conflictLabel: String,
    val conflictHz: Double,
    /** Which of the drum's targets is too close: "fundamental" or "batter lug". */
    val targetKind: String,
    val distanceHz: Double,
)

data class DrumTarget(
    val drum: DrumType,
    val noteMidi: Int,
    /** Target fundamental (f01) incl. nudge. */
    val fundamentalHz: Double,
    /** Target f11 lug frequency for the batter head. */
    val batterLugHz: Double,
    /** Target f11 lug frequency for the resonant head. */
    val resonantLugHz: Double,
    /** Interval from the next-higher tom, e.g. "P4" or "M3". */
    val intervalFromAbove: String? = null,
    val buzzWarning: BuzzWarning? = null,
) {
    val noteName: String get() = PitchMath.noteName(noteMidi)

    fun lugTarget(side: HeadSide): Double = if (side == HeadSide.BATTER) batterLugHz else resonantLugHz
}

/**
 * Acoustic target calculator.
 *
 * Model (documented approximations):
 *  - An ideal circular membrane has f11 / f01 = j₁,₁ / j₀,₁ = 3.8317 / 2.4048 ≈ 1.593.
 *    The lug tone measured by tapping ~1" in from a lug is dominated by f11, so
 *    lug target = fundamental × 1.593 × ply factor.
 *  - The perceived fundamental of a two-headed drum follows the mean of both heads, so with a
 *    resonant/batter ratio r: batter = 2·L/(1+r), resonant = r·batter, where L is the mean lug target.
 *  - Each shell has a practical ("shell-safe") fundamental range that depends on its diameter
 *    (and slightly on depth); targets are only picked inside it.
 */
object DrumAcoustics {
    const val F11_OVER_F01 = 1.593
    const val BUZZ_WINDOW_HZ = 3.0
    const val NUDGE_STEP_HZ = 6.0

    private data class RangePoint(val diameter: Double, val lowHz: Double, val highHz: Double)

    private val TOM_RANGES = listOf(
        RangePoint(8.0, 175.0, 262.0),
        RangePoint(10.0, 147.0, 220.0),
        RangePoint(12.0, 123.0, 185.0),
        RangePoint(13.0, 110.0, 165.0),
        RangePoint(14.0, 98.0, 147.0),
        RangePoint(15.0, 87.0, 131.0),
        RangePoint(16.0, 78.0, 117.0),
        RangePoint(18.0, 65.0, 98.0),
    )
    private val KICK_RANGES = listOf(
        RangePoint(18.0, 55.0, 82.0),
        RangePoint(20.0, 48.0, 72.0),
        RangePoint(22.0, 43.0, 64.0),
        RangePoint(24.0, 38.0, 57.0),
        RangePoint(26.0, 34.0, 51.0),
    )
    private val SNARE_RANGES = listOf(
        RangePoint(10.0, 240.0, 360.0),
        RangePoint(12.0, 215.0, 320.0),
        RangePoint(13.0, 200.0, 300.0),
        RangePoint(14.0, 180.0, 270.0),
    )

    /** Shell-safe fundamental range for a shell. */
    fun shellRange(shell: Shell): Band {
        val table: List<RangePoint>
        val typicalDepthRatio: Double
        when (shell.drum) {
            DrumType.KICK -> { table = KICK_RANGES; typicalDepthRatio = 0.8 }
            DrumType.SNARE -> { table = SNARE_RANGES; typicalDepthRatio = 0.43 }
            else -> { table = TOM_RANGES; typicalDepthRatio = 0.75 }
        }
        val base = interpolate(table, shell.diameterIn)
        val ratio = if (shell.diameterIn > 0) shell.depthIn / shell.diameterIn else typicalDepthRatio
        // Deeper shells have more air mass and sit slightly lower.
        val depthFactor = (1.0 - 0.08 * (ratio - typicalDepthRatio)).coerceIn(0.93, 1.05)
        return Band(base.lowHz * depthFactor, base.highHz * depthFactor)
    }

    private fun interpolate(table: List<RangePoint>, diameter: Double): Band {
        if (diameter <= table.first().diameter) return table.first().let { Band(it.lowHz, it.highHz) }
        if (diameter >= table.last().diameter) return table.last().let { Band(it.lowHz, it.highHz) }
        for (i in 0 until table.size - 1) {
            val a = table[i]
            val b = table[i + 1]
            if (diameter >= a.diameter && diameter <= b.diameter) {
                val t = (diameter - a.diameter) / (b.diameter - a.diameter)
                return Band(geo(a.lowHz, b.lowHz, t), geo(a.highHz, b.highHz, t))
            }
        }
        return table.last().let { Band(it.lowHz, it.highHz) }
    }

    private fun geo(a: Double, b: Double, t: Double): Double = exp(ln(a) + (ln(b) - ln(a)) * t)

    /** Preferred position inside the range, 0 = bottom, 1 = top (log scale). */
    private fun preferredPosition(drum: DrumType): Double = when (drum) {
        DrumType.KICK -> 0.45
        DrumType.SNARE -> 0.5
        else -> 0.55
    }

    private fun centerMidi(shell: Shell): Double {
        val r = shellRange(shell)
        val lo = PitchMath.hzToMidi(r.lowHz)
        val hi = PitchMath.hzToMidi(r.highHz)
        return lo + (hi - lo) * preferredPosition(shell.drum)
    }

    private fun scaleNotesIn(range: Band, key: BandKey): List<Int> {
        val lo = kotlin.math.ceil(PitchMath.hzToMidi(range.lowHz)).toInt()
        val hi = kotlin.math.floor(PitchMath.hzToMidi(range.highHz)).toInt()
        return (lo..hi).filter { key.contains(it) }
    }

    fun intervalName(semitones: Int): String = when (semitones) {
        1 -> "m2"; 2 -> "M2"; 3 -> "m3"; 4 -> "M3"; 5 -> "P4"; 6 -> "TT"
        7 -> "P5"; 8 -> "m6"; 9 -> "M6"; 10 -> "m7"; 11 -> "M7"; 12 -> "P8"
        else -> "${semitones}st"
    }

    /**
     * Chooses notes for the toms (highest to lowest) spaced by a Major 3rd or Perfect 4th
     * inside the key, minimising the distance from each shell's sweet spot.
     * Returns pairs of (midi note, interval-from-above or null).
     */
    fun chooseTomNotes(toms: List<Shell>, key: BandKey): List<Pair<Int, String?>> {
        if (toms.isEmpty()) return emptyList()
        val sorted = toms.sortedBy { it.diameterIn }
        val firstCandidates = scaleNotesIn(shellRange(sorted[0]), key)
            .ifEmpty { listOf(Math.round(centerMidi(sorted[0])).toInt()) }

        var bestCost = Double.MAX_VALUE
        var best: List<Pair<Int, String?>> = emptyList()

        for (start in firstCandidates) {
            val chain = mutableListOf<Pair<Int, String?>>(start to null)
            var cost = abs(start - centerMidi(sorted[0]))
            var prev = start
            for (i in 1 until sorted.size) {
                val shell = sorted[i]
                val range = shellRange(shell)
                val center = centerMidi(shell)
                val inRange = { m: Int -> PitchMath.midiToHz(m) in range }
                val preferred = listOf(prev - 5, prev - 4).filter { key.contains(it) && inRange(it) }
                val pick: Int
                if (preferred.isNotEmpty()) {
                    pick = preferred.minBy { abs(it - center) }
                } else {
                    val below = scaleNotesIn(range, key).filter { it < prev }
                    if (below.isNotEmpty()) {
                        pick = below.minBy { abs(it - center) }
                        cost += 6.0
                    } else {
                        pick = scaleNotesIn(range, key).minByOrNull { abs(it - center) }
                            ?: Math.round(center).toInt()
                        cost += 10.0
                    }
                }
                cost += abs(pick - center)
                chain += pick to intervalName(prev - pick)
                prev = pick
            }
            if (cost < bestCost) {
                bestCost = cost
                best = chain
            }
        }
        // Map back from diameter order to the caller's order
        val byShell = sorted.zip(best).toMap()
        return toms.map { byShell.getValue(it) }
    }

    private fun chooseSingleNote(shell: Shell, key: BandKey, preferRootOrFifth: Boolean): Int {
        val range = shellRange(shell)
        val center = centerMidi(shell)
        val notes = scaleNotesIn(range, key)
        if (preferRootOrFifth) {
            val rf = notes.filter {
                val pc = Math.floorMod(it, 12)
                pc == key.rootPitchClass || pc == key.fifthPitchClass
            }
            if (rf.isNotEmpty()) return rf.minBy { abs(it - center) }
        }
        return notes.minByOrNull { abs(it - center) } ?: Math.round(center).toInt()
    }

    fun lugTargets(fundamentalHz: Double, ply: HeadPly, style: TuningStyle): Pair<Double, Double> {
        val meanLug = fundamentalHz * F11_OVER_F01 * ply.lugFactor
        val r = style.resonantRatio
        val batter = 2.0 * meanLug / (1.0 + r)
        return batter to batter * r
    }

    /** All root/fifth "bass note" frequencies between 30 Hz and 1 kHz for [key]. */
    fun rootAndFifthFrequencies(key: BandKey): List<Pair<String, Double>> {
        val out = mutableListOf<Pair<String, Double>>()
        for (midi in 23..83) {
            val pc = Math.floorMod(midi, 12)
            val role = when (pc) {
                key.rootPitchClass -> "root"
                key.fifthPitchClass -> "fifth"
                else -> null
            } ?: continue
            out += "${PitchMath.NOTE_NAMES[pc]} ($role)" to PitchMath.midiToHz(midi)
        }
        return out
    }

    fun buzzWarning(drum: DrumType, fundamentalHz: Double, batterLugHz: Double, key: BandKey): BuzzWarning? {
        if (drum != DrumType.SNARE && !drum.isRackTom) return null
        var best: BuzzWarning? = null
        for ((label, hz) in rootAndFifthFrequencies(key)) {
            for ((kind, target) in listOf("fundamental" to fundamentalHz, "batter lug" to batterLugHz)) {
                val d = abs(target - hz)
                if (d <= BUZZ_WINDOW_HZ && (best == null || d < best.distanceHz)) {
                    best = BuzzWarning(label, hz, kind, d)
                }
            }
        }
        return best
    }

    fun calculateTargets(
        shells: List<Shell>,
        key: BandKey,
        style: TuningStyle,
    ): Map<DrumType, DrumTarget> {
        val enabled = shells.filter { it.enabled }
        val notes = mutableMapOf<DrumType, Pair<Int, String?>>()

        val toms = enabled.filter { it.drum.isTom }
        chooseTomNotes(toms, key).forEachIndexed { i, n -> notes[toms[i].drum] = n }
        enabled.firstOrNull { it.drum == DrumType.SNARE }?.let {
            notes[DrumType.SNARE] = chooseSingleNote(it, key, preferRootOrFifth = false) to null
        }
        enabled.firstOrNull { it.drum == DrumType.KICK }?.let {
            notes[DrumType.KICK] = chooseSingleNote(it, key, preferRootOrFifth = true) to null
        }

        val result = linkedMapOf<DrumType, DrumTarget>()
        for (shell in enabled) {
            val (midi, interval) = notes[shell.drum] ?: continue
            val f0 = PitchMath.midiToHz(midi) + shell.nudgeHz
            val (batter, reso) = lugTargets(f0, shell.ply, style)
            result[shell.drum] = DrumTarget(
                drum = shell.drum,
                noteMidi = midi,
                fundamentalHz = f0,
                batterLugHz = batter,
                resonantLugHz = reso,
                intervalFromAbove = interval,
                buzzWarning = buzzWarning(shell.drum, f0, batter, key),
            )
        }
        return result
    }

    fun defaultShells(): List<Shell> = listOf(
        Shell(DrumType.SNARE, 14.0, 6.5, HeadPly.ONE_PLY_10, lugCount = 10),
        Shell(DrumType.TOM1, 10.0, 7.0, HeadPly.ONE_PLY_10, lugCount = 6),
        Shell(DrumType.TOM2, 12.0, 8.0, HeadPly.ONE_PLY_10, lugCount = 6),
        Shell(DrumType.FLOOR, 16.0, 14.0, HeadPly.ONE_PLY_10, lugCount = 8),
        Shell(DrumType.KICK, 22.0, 18.0, HeadPly.TWO_PLY_14, lugCount = 10),
    )
}
