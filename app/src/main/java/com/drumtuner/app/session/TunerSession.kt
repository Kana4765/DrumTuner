package com.drumtuner.app.session

import com.drumtuner.app.data.KitJson
import com.drumtuner.app.data.KitProfile
import com.drumtuner.app.dsp.PitchMath
import com.drumtuner.app.dsp.SnareWire
import com.drumtuner.app.tuning.Band
import com.drumtuner.app.tuning.DetectionBands
import com.drumtuner.app.tuning.DrumAcoustics
import com.drumtuner.app.tuning.DrumType
import com.drumtuner.app.tuning.FilterResult
import com.drumtuner.app.tuning.HeadSide
import com.drumtuner.app.tuning.HitMedianFilter
import com.drumtuner.app.tuning.Shell
import com.drumtuner.app.tuning.StarPattern
import com.drumtuner.app.tuning.TuningStyle
import com.drumtuner.app.tuning.BandKey
import java.util.UUID
import kotlin.math.abs

/**
 * All tuning-session logic in pure Kotlin (no Android), driven by engine events and UI actions.
 * The ViewModel owns one instance and republishes [state] after every call.
 */
class TunerSession(
    initialKits: List<KitProfile>,
    activeKitId: String?,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    var state: SessionState
        private set

    private sealed interface DecayTarget {
        data class Lug(val key: HeadKey, val lug: Int) : DecayTarget
        data class Line(val drum: DrumType) : DecayTarget
    }

    private val pendingDecays = LinkedHashMap<Long, DecayTarget>()

    init {
        val kits = initialKits.map { it.normalized() }.ifEmpty { listOf(defaultKit()) }
        val active = kits.firstOrNull { it.id == activeKitId }?.id ?: kits.first().id
        state = SessionState(kits = kits, activeKitId = active)
    }

    fun defaultKit(name: String = "My Kit"): KitProfile = KitProfile(id = newId(), name = name)

    // ───────────────────────────── Engine events ─────────────────────────────

    fun onHit(id: Long, frequencyHz: Double?, sizzleRatio: Double) {
        val s = state
        when (s.tab) {
            AppTab.TUNER -> onTunerHit(id, frequencyHz)
            AppTab.SOUNDCHECK -> when (s.soundcheckMode) {
                SoundcheckMode.WIRES -> state = s.copy(
                    wire = SnareWire.classify(sizzleRatio),
                    wirePitchHz = frequencyHz,
                    message = null,
                )
                SoundcheckMode.LINE_CHECK -> onLineCheckHit(id, frequencyHz)
            }
            else -> Unit
        }
    }

    fun onDecay(id: Long, decayMs: Double) {
        val target = pendingDecays.remove(id) ?: return
        val s = state
        state = when (target) {
            is DecayTarget.Lug -> {
                val head = s.head(target.key)
                val lug = head.lug(target.lug)
                s.copy(heads = s.heads + (target.key to head.copy(lugs = head.lugs + (target.lug to lug.copy(decays = lug.decays + decayMs)))))
            }
            is DecayTarget.Line -> {
                val e = s.lineCheck[target.drum] ?: LineCheckEntry()
                s.copy(lineCheck = s.lineCheck + (target.drum to e.copy(decayMs = decayMs)))
            }
        }
    }

    private fun remember(id: Long, target: DecayTarget) {
        pendingDecays[id] = target
        while (pendingDecays.size > 32) pendingDecays.remove(pendingDecays.keys.first())
    }

    private fun onTunerHit(id: Long, frequencyHz: Double?) {
        val s = state
        if (frequencyHz == null) {
            state = s.copy(message = "No clear tone in band — hit closer to the lug")
            return
        }
        val key = s.headKey
        var head = s.currentHead
        val lugNo = head.activeLug
        var lug = head.lug(lugNo)
        if (lug.isLocked) lug = LugState() // re-measure a locked lug (e.g. after head complete)

        val hits = lug.hits + frequencyHz
        remember(id, DecayTarget.Lug(key, lugNo))

        var message: String?
        when (val r = HitMedianFilter.evaluate(hits)) {
            is FilterResult.NeedMore -> {
                lug = lug.copy(hits = hits, lockedHz = null)
                head = head.copy(lugs = head.lugs + (lugNo to lug), complete = false)
                message = if (r.hitsRequired == 3) "Hits disagree — Hit 3/3…" else "Hit ${r.hitsSoFar + 1}/2…"
            }
            is FilterResult.Locked -> {
                lug = lug.copy(hits = hits, lockedHz = r.valueHz)
                val lugs = head.lugs + (lugNo to lug)
                val next = StarPattern.nextLug(head.lugCount, lugNo) { n -> lugs[n]?.isLocked == true }
                head = if (next != null) {
                    head.copy(lugs = lugs, activeLug = next, complete = false)
                } else {
                    head.copy(lugs = lugs, complete = true)
                }
                message = if (next != null) "Lug $lugNo locked → Lug $next" else "Head complete ✓"
                if (r.usedMedian) message += " (median of 3)"
            }
        }
        state = s.copy(heads = s.heads + (key to head), message = message)
    }

    private fun onLineCheckHit(id: Long, frequencyHz: Double?) {
        val s = state
        val drum = s.lineCheckDrum ?: return
        if (frequencyHz == null) {
            state = s.copy(message = "No clear tone — hit ${drum.label} in the centre")
            return
        }
        remember(id, DecayTarget.Line(drum))
        val lineCheck = s.lineCheck + (drum to LineCheckEntry(pitchHz = frequencyHz))
        val order = enabledDrums(s)
        val idx = order.indexOf(drum)
        val next = (order.drop(idx + 1) + order.take(idx + 1)).firstOrNull { lineCheck[it]?.pitchHz == null }
        state = s.copy(
            lineCheck = lineCheck,
            lineCheckDrum = next,
            message = if (next != null) "Now hit ${next.label}" else "Line check complete",
        )
    }

    private fun enabledDrums(s: SessionState): List<DrumType> =
        DrumType.values().filter { s.kit.shell(it).enabled }

    // ───────────────────────────── Tuner actions ─────────────────────────────

    fun selectTab(tab: AppTab) { state = state.copy(tab = tab, message = null) }

    fun selectMode(mode: TuningMode) { state = state.copy(mode = mode) }

    fun selectDrum(drum: DrumType) { state = state.copy(drum = drum, message = null) }

    fun selectSide(side: HeadSide) { state = state.copy(side = side, message = null) }

    fun setThreshold(db: Float) { state = state.copy(thresholdDb = db.coerceIn(MIN_THRESHOLD_DB, MAX_THRESHOLD_DB)) }

    /** Tap on a lug: make it active and clear it so it is re-measured. */
    fun tapLug(lug: Int) {
        val s = state
        val head = s.currentHead
        if (lug !in 1..head.lugCount) return
        val updated = head.copy(lugs = head.lugs - lug, activeLug = lug, complete = false)
        state = s.copy(heads = s.heads + (s.headKey to updated), message = "Re-measuring Lug $lug")
    }

    fun resetHead() {
        val s = state
        state = s.copy(heads = s.heads - s.headKey, message = "Head reset — start at Lug 1")
    }

    /** Changes the lug count of the current drum (both heads) and resets its measurements. */
    fun setLugCount(count: Int) {
        if (count !in StarPattern.SUPPORTED_LUG_COUNTS) return
        val s = state
        val shell = s.kit.shell(s.drum).copy(lugCount = count)
        val heads = s.heads - HeadKey(s.drum, HeadSide.BATTER) - HeadKey(s.drum, HeadSide.RESONANT)
        state = replaceKit(s.kit.withShell(shell)).copy(heads = heads)
    }

    // ───────────────────────────── Derived values ─────────────────────────────

    /** Reference for the current head: Lug 1 (Mode A) or the kit target (Mode B). */
    fun referenceHz(s: SessionState = state): Double? = when (s.mode) {
        TuningMode.RELATIVE -> s.currentHead.lug(1).lockedHz
        TuningMode.TARGET -> s.targets[s.drum]?.lugTarget(s.side) ?: s.currentHead.lug(1).lockedHz
    }

    fun readout(s: SessionState = state): Readout = buildReadout(s, s.currentHead.activeLug)

    fun lugReadout(lug: Int, s: SessionState = state): Readout = buildReadout(s, lug)

    private fun buildReadout(s: SessionState, lugNo: Int): Readout {
        val head = s.currentHead
        val lug = head.lug(lugNo)
        val hz = lug.displayHz
        val ref = referenceHz(s)
        val isBaseline = s.mode == TuningMode.RELATIVE && lugNo == 1
        val delta = if (hz != null && ref != null && !isBaseline) hz - ref else null
        val status = when {
            hz == null -> TuneStatus.NONE
            isBaseline || ref == null -> TuneStatus.BASELINE
            abs(delta!!) <= IN_TUNE_HZ -> TuneStatus.IN_TUNE
            delta < 0 -> TuneStatus.TIGHTEN
            else -> TuneStatus.LOOSEN
        }
        val severity = when (status) {
            TuneStatus.IN_TUNE -> Severity.OK
            TuneStatus.TIGHTEN, TuneStatus.LOOSEN -> if (abs(delta!!) <= WARN_HZ) Severity.WARN else Severity.BAD
            else -> Severity.NEUTRAL
        }
        val prompt = when (status) {
            TuneStatus.NONE -> "Hit Lug $lugNo"
            TuneStatus.BASELINE -> if (lug.isLocked) "BASELINE 0.0 Hz" else "Measuring…"
            TuneStatus.IN_TUNE -> "IN TUNE ✓"
            TuneStatus.TIGHTEN -> "TIGHTEN ↑"
            TuneStatus.LOOSEN -> "LOOSEN ↓"
        }
        return Readout(
            lug = lugNo,
            displayHz = hz,
            referenceHz = ref,
            deltaHz = delta,
            status = status,
            severity = severity,
            noteLabel = hz?.let { PitchMath.reading(it)?.label },
            decayMs = lug.decayMs,
            dots = HitMedianFilter.dots(lug.hits),
            prompt = prompt,
        )
    }

    /** Lugs whose sustain is >35 % shorter than the head's median sustain. */
    fun deadLugs(s: SessionState = state): List<Int> {
        val head = s.currentHead
        val decays = (1..head.lugCount).mapNotNull { n -> head.lug(n).decayMs?.let { n to it } }
        if (decays.size < 3) return emptyList()
        val median = medianOrNull(decays.map { it.second }) ?: return emptyList()
        return decays.filter { it.second < median * (1.0 - DEAD_HEAD_FRACTION) }.map { it.first }
    }

    /** Frequency band the engine should search for the current context. */
    fun searchBand(s: SessionState = state): Band = when (s.tab) {
        AppTab.TUNER -> {
            val base = DetectionBands.lugBand(s.drum, s.side)
            val activeIsLug1Relative = s.mode == TuningMode.RELATIVE && s.currentHead.activeLug == 1
            DetectionBands.trackingBand(base, if (activeIsLug1Relative) null else referenceHz(s))
        }
        AppTab.SOUNDCHECK -> {
            val drum = if (s.soundcheckMode == SoundcheckMode.WIRES) DrumType.SNARE else (s.lineCheckDrum ?: DrumType.SNARE)
            DetectionBands.fundamentalBand(drum, lineCheckReferenceHz(drum, s))
        }
        else -> Band(45.0, 650.0)
    }

    // ───────────────────────────── Soundcheck ─────────────────────────────

    fun selectSoundcheckMode(mode: SoundcheckMode) { state = state.copy(soundcheckMode = mode, message = null) }

    fun startLineCheck() {
        val first = enabledDrums(state).firstOrNull()
        state = state.copy(lineCheck = emptyMap(), lineCheckDrum = first, message = first?.let { "Hit ${it.label} in the centre" })
    }

    fun selectLineCheckDrum(drum: DrumType) {
        state = state.copy(lineCheckDrum = drum, lineCheck = state.lineCheck - drum, message = "Hit ${drum.label} in the centre")
    }

    /** Saved reference pitch for a centre hit, falling back to the computed key target. */
    fun lineCheckReferenceHz(drum: DrumType, s: SessionState = state): Double? =
        s.kit.shell(drum).refPitchHz ?: s.targets[drum]?.fundamentalHz

    fun saveLineCheckAsReference() {
        val s = state
        var kit = s.kit
        for ((drum, entry) in s.lineCheck) {
            val shell = kit.shell(drum)
            kit = kit.withShell(shell.copy(refPitchHz = entry.pitchHz ?: shell.refPitchHz, refDecayMs = entry.decayMs ?: shell.refDecayMs))
        }
        state = replaceKit(kit).copy(message = "Saved as reference for ${kit.name}")
    }

    fun clearReferences() {
        val s = state
        val kit = s.kit.copy(shells = s.kit.shells.map { it.copy(refPitchHz = null, refDecayMs = null) })
        state = replaceKit(kit).copy(message = "References cleared")
    }

    // ───────────────────────────── Kit editing ─────────────────────────────

    fun updateShell(drum: DrumType, transform: (Shell) -> Shell) {
        val s = state
        val before = s.kit.shell(drum)
        val after = transform(before)
        var next = replaceKit(s.kit.withShell(after))
        if (after.lugCount != before.lugCount) {
            next = next.copy(heads = next.heads - HeadKey(drum, HeadSide.BATTER) - HeadKey(drum, HeadSide.RESONANT))
        }
        state = next
    }

    fun setKey(key: BandKey) { state = replaceKit(state.kit.copy(key = key)) }

    fun setStyle(style: TuningStyle) { state = replaceKit(state.kit.copy(style = style)) }

    fun nudge(drum: DrumType, deltaHz: Double) = updateShell(drum) { it.copy(nudgeHz = it.nudgeHz + deltaHz) }

    fun resetNudge(drum: DrumType) = updateShell(drum) { it.copy(nudgeHz = 0.0) }

    // ───────────────────────────── Presets ─────────────────────────────

    fun selectKit(id: String) {
        if (state.kits.none { it.id == id }) return
        state = state.copy(activeKitId = id, heads = emptyMap(), lineCheck = emptyMap(), lineCheckDrum = null)
    }

    fun newKit(name: String) {
        val kit = defaultKit(name.ifBlank { "Kit ${state.kits.size + 1}" })
        state = state.copy(kits = state.kits + kit, activeKitId = kit.id, heads = emptyMap())
    }

    fun duplicateKit(id: String) {
        val src = state.kits.firstOrNull { it.id == id } ?: return
        val copy = src.copy(id = newId(), name = "${src.name} (copy)")
        state = state.copy(kits = state.kits + copy, activeKitId = copy.id)
    }

    fun renameKit(id: String, name: String) {
        if (name.isBlank()) return
        state = state.copy(kits = state.kits.map { if (it.id == id) it.copy(name = name.trim()) else it })
    }

    fun deleteKit(id: String) {
        val s = state
        val remaining = s.kits.filter { it.id != id }.ifEmpty { listOf(defaultKit()) }
        val active = if (s.activeKitId == id) remaining.first().id else s.activeKitId
        state = s.copy(kits = remaining, activeKitId = active, heads = if (s.activeKitId == id) emptyMap() else s.heads)
    }

    fun exportJson(): String = KitJson.encode(state.kits)

    /** Imports kits from backup JSON; kits whose id already exists get a fresh id. Returns count. */
    fun importJson(text: String): Int {
        val imported = KitJson.decode(text)
        if (imported.isEmpty()) return 0
        val existing = state.kits.map { it.id }.toMutableSet()
        val fresh = imported.map { k ->
            if (k.id in existing) k.copy(id = newId()).also { existing += it.id } else k.also { existing += it.id }
        }
        state = state.copy(kits = state.kits + fresh, message = "Imported ${fresh.size} kit(s)")
        return fresh.size
    }

    fun clearMessage() { state = state.copy(message = null) }

    fun showMessage(text: String) { state = state.copy(message = text) }

    private fun replaceKit(kit: KitProfile): SessionState {
        val s = state
        return s.copy(kits = s.kits.map { if (it.id == kit.id) kit else it })
    }

    companion object {
        const val IN_TUNE_HZ = 1.0
        const val WARN_HZ = 5.0
        const val DEAD_HEAD_FRACTION = 0.35
        const val MIN_THRESHOLD_DB = -60f
        const val MAX_THRESHOLD_DB = -6f
    }
}
