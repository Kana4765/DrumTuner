package com.drumtuner.app.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SpectrumAnalyzerTest {

    private val analyzer = SpectrumAnalyzer(44_100, 8_192)

    @Test
    fun peakAccuracyBetterThan0_2HzAcrossAllDrumBands() {
        var worst = 0.0
        var f = 45.0
        while (f <= 650.0) {
            val sig = TestSignals.decayingTone(listOf(f to 0.5), 8192, tauSec = 0.3)
            analyzer.analyze(sig)
            val peak = analyzer.findPeak(f * 0.8, f * 1.2)
            assertNotNull("no peak for $f Hz", peak)
            worst = maxOf(worst, abs(peak!!.frequencyHz - f))
            f += 3.7
        }
        assertTrue("worst error $worst Hz", worst < 0.2)
    }

    @Test
    fun accurateWithOvertonesAndNoise() {
        // f01, f11 (≈1.593×), f21 (≈2.136×) like a real membrane, plus white noise
        val f11 = 182.4
        val f01 = f11 / 1.593
        val sig = TestSignals.decayingTone(
            listOf(f01 to 0.25, f11 to 0.5, f11 * 1.341 to 0.3),
            8192, tauSec = 0.25, noise = 0.01,
        )
        analyzer.analyze(sig)
        val peak = analyzer.findPeak(150.0, 220.0)!!
        assertEquals(f11, peak.frequencyHz, 0.2)
    }

    @Test
    fun bandRestrictionSelectsRequestedMode() {
        // A louder 100 Hz tone must not win when searching the 150–500 Hz band.
        val sig = TestSignals.decayingTone(listOf(100.0 to 0.8, 310.0 to 0.2), 8192)
        analyzer.analyze(sig)
        assertEquals(310.0, analyzer.findPeak(150.0, 500.0)!!.frequencyHz, 0.2)
        assertEquals(100.0, analyzer.findPeak(45.0, 160.0)!!.frequencyHz, 0.2)
    }

    @Test
    fun fullScaleSineReadsNearZeroDb() {
        val sig = TestSignals.decayingTone(listOf(250.0 to 1.0), 8192, tauSec = 1e9)
        analyzer.analyze(sig)
        val peak = analyzer.findPeak(200.0, 300.0)!!
        assertEquals(0.0, peak.levelDb, 1.6) // Hann scalloping ≤ 1.42 dB
    }

    @Test
    fun silenceHasNoPeak() {
        analyzer.analyze(FloatArray(8192))
        assertNull(analyzer.findPeak(100.0, 400.0))
    }

    @Test
    fun sizzleRatioSeparatesWireStates() {
        val rattly = TestSignals.decayingTone(listOf(220.0 to 0.3, 4_500.0 to 0.4, 6_000.0 to 0.3), 8192)
        val choked = TestSignals.decayingTone(listOf(220.0 to 0.6, 4_500.0 to 0.01), 8192)
        analyzer.analyze(rattly)
        val r = SnareWire.classify(SnareWire.sizzleRatio(analyzer))
        analyzer.analyze(choked)
        val c = SnareWire.classify(SnareWire.sizzleRatio(analyzer))
        assertEquals(WireZone.RATTLY, r.zone)
        assertEquals(WireZone.CHOKED, c.zone)
        assertTrue(r.gaugePosition > c.gaugePosition)
        assertEquals(WireZone.CRISP, SnareWire.classify(0.05).zone) // -13 dB
    }
}
