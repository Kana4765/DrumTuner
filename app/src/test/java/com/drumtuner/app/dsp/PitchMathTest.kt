package com.drumtuner.app.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PitchMathTest {

    @Test
    fun a440IsA4() {
        val r = PitchMath.reading(440.0)!!
        assertEquals("A4", r.name)
        assertEquals(0, r.cents)
        assertEquals("A4 +0¢", r.label)
    }

    @Test
    fun fSharp3WithCents() {
        val fs3 = PitchMath.midiToHz(54)
        assertEquals(184.997, fs3, 0.001)
        val r = PitchMath.reading(fs3 * Math.pow(2.0, -14.0 / 1200.0))!!
        assertEquals("F#3", r.name)
        assertEquals(-14, r.cents)
        assertEquals("F#3 -14¢", r.label)
    }

    @Test
    fun lowNotesHaveCorrectOctave() {
        assertEquals("C2", PitchMath.noteName(36))
        assertEquals("B1", PitchMath.noteName(35))
        assertEquals("C-1", PitchMath.noteName(0))
    }

    @Test
    fun invalidFrequencyHasNoReading() {
        assertNull(PitchMath.reading(0.0))
        assertNull(PitchMath.reading(Double.NaN))
    }
}
