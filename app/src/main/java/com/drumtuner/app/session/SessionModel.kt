package com.drumtuner.app.session

import com.drumtuner.app.data.KitProfile
import com.drumtuner.app.dsp.WireReading
import com.drumtuner.app.tuning.DrumAcoustics
import com.drumtuner.app.tuning.DrumTarget
import com.drumtuner.app.tuning.DrumType
import com.drumtuner.app.tuning.HeadSide

enum class AppTab(val label: String) {
    TUNER("Tuner"),
    KIT("Kit & Key"),
    SOUNDCHECK("Soundcheck"),
    PRESETS("Presets"),
}

enum class TuningMode(val label: String) {
    /** Mode A: every lug is compared with Lug 1. */
    RELATIVE("Relative"),
    /** Mode B: every lug is compared with the kit/key target. */
    TARGET("Key Target"),
}

enum class SoundcheckMode(val label: String) {
    WIRES("Snare Wires"),
    LINE_CHECK("Line Check"),
}

data class LugState(
    val hits: List<Double> = emptyList(),
    val lockedHz: Double? = null,
    val decays: List<Double> = emptyList(),
) {
    val isLocked: Boolean get() = lockedHz != null
    val displayHz: Double? get() = lockedHz ?: hits.lastOrNull()
    val decayMs: Double? get() = medianOrNull(decays)
}

data class HeadKey(val drum: DrumType, val side: HeadSide)

data class HeadState(
    val lugCount: Int,
    val lugs: Map<Int, LugState> = emptyMap(),
    val activeLug: Int = 1,
    val complete: Boolean = false,
) {
    fun lug(n: Int): LugState = lugs[n] ?: LugState()
}

data class LineCheckEntry(val pitchHz: Double? = null, val decayMs: Double? = null)

data class SessionState(
    val kits: List<KitProfile>,
    val activeKitId: String,
    val tab: AppTab = AppTab.TUNER,
    val mode: TuningMode = TuningMode.RELATIVE,
    val drum: DrumType = DrumType.SNARE,
    val side: HeadSide = HeadSide.BATTER,
    val heads: Map<HeadKey, HeadState> = emptyMap(),
    val thresholdDb: Float = -30f,
    val soundcheckMode: SoundcheckMode = SoundcheckMode.WIRES,
    val wire: WireReading? = null,
    val wirePitchHz: Double? = null,
    val lineCheck: Map<DrumType, LineCheckEntry> = emptyMap(),
    val lineCheckDrum: DrumType? = null,
    /** Short status message for the UI (e.g. "Hit 2/3…", "No clear tone"). */
    val message: String? = null,
) {
    val kit: KitProfile get() = kits.firstOrNull { it.id == activeKitId } ?: kits.first()

    val targets: Map<DrumType, DrumTarget> by lazy {
        DrumAcoustics.calculateTargets(kit.shells, kit.key, kit.style)
    }

    val headKey: HeadKey get() = HeadKey(drum, side)

    fun head(key: HeadKey): HeadState = heads[key] ?: HeadState(kit.shell(key.drum).lugCount)

    val currentHead: HeadState get() = head(headKey)
}

enum class TuneStatus { NONE, BASELINE, IN_TUNE, TIGHTEN, LOOSEN }

enum class Severity { OK, WARN, BAD, NEUTRAL }

/** Everything the centre of the lug radar needs. */
data class Readout(
    val lug: Int,
    val displayHz: Double?,
    val referenceHz: Double?,
    val deltaHz: Double?,
    val status: TuneStatus,
    val severity: Severity,
    val noteLabel: String?,
    val decayMs: Double?,
    val dots: String,
    val prompt: String,
)

fun medianOrNull(values: List<Double>): Double? {
    if (values.isEmpty()) return null
    val s = values.sorted()
    val m = s.size / 2
    return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2.0
}
