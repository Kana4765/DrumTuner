package com.drumtuner.app.dsp

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

data class NoteReading(
    val midi: Int,
    val name: String,
    /** Deviation from the nearest equal-tempered note, -50..+50 cents. */
    val cents: Int,
) {
    /** e.g. "F#3 -14¢" */
    val label: String
        get() = "$name ${if (cents >= 0) "+" else ""}$cents¢"
}

object PitchMath {
    val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    const val A4_HZ = 440.0
    const val A4_MIDI = 69

    fun midiToHz(midi: Double): Double = A4_HZ * 2.0.pow((midi - A4_MIDI) / 12.0)

    fun midiToHz(midi: Int): Double = midiToHz(midi.toDouble())

    fun hzToMidi(hz: Double): Double = A4_MIDI + 12.0 * log2(hz / A4_HZ)

    fun noteName(midi: Int): String {
        val pc = Math.floorMod(midi, 12)
        val octave = Math.floorDiv(midi, 12) - 1
        return "${NOTE_NAMES[pc]}$octave"
    }

    fun reading(hz: Double): NoteReading? {
        if (!(hz > 0.0) || hz.isInfinite()) return null
        val exact = hzToMidi(hz)
        val nearest = exact.roundToInt()
        val cents = ((exact - nearest) * 100.0).roundToInt()
        return NoteReading(nearest, noteName(nearest), cents)
    }

    /** Signed distance in cents from [referenceHz] to [hz]. */
    fun centsBetween(hz: Double, referenceHz: Double): Double = 1200.0 * log2(hz / referenceHz)
}
