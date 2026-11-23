package com.drumtuner.app.dsp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TransientGateTest {

    private class Recorder : TransientGate.Listener {
        val triggers = mutableListOf<Long>()
        val captures = mutableListOf<Pair<Long, FloatArray>>()
        override fun onTrigger(sampleIndex: Long) { triggers += sampleIndex }
        override fun onCapture(buffer: FloatArray, triggerSampleIndex: Long) { captures += triggerSampleIndex to buffer }
    }

    private fun feedInRandomChunks(gate: TransientGate, signal: FloatArray, seed: Int = 3) {
        val rnd = Random(seed)
        var i = 0
        while (i < signal.size) {
            val n = minOf(signal.size - i, 64 + rnd.nextInt(2000))
            gate.process(signal.copyOfRange(i, i + n), n)
            i += n
        }
    }

    @Test
    fun skips25msThenCaptures8192Samples() {
        val onset = 4_410
        val sig = TestSignals.hit(200.0, leadSilence = onset, totalSamples = 30_000)
        val rec = Recorder()
        val gate = TransientGate(thresholdDb = -30.0, listener = rec)
        assertEquals(1_102, gate.skipSamples)
        assertEquals(8_192, gate.captureSize)

        feedInRandomChunks(gate, sig)

        assertEquals(1, rec.triggers.size)
        val trig = rec.triggers[0]
        assertTrue("trigger $trig should be at the onset", trig in onset..onset + 5)
        assertEquals(1, rec.captures.size)
        val (capTrig, buf) = rec.captures[0]
        assertEquals(trig, capTrig)
        val start = (trig + 1_102).toInt()
        assertArrayEquals(sig.copyOfRange(start, start + 8_192), buf, 0f)
    }

    @Test
    fun capturedTailYieldsCorrectPitch() {
        val sig = TestSignals.hit(187.3, leadSilence = 2_000, totalSamples = 20_000)
        val rec = Recorder()
        val gate = TransientGate(thresholdDb = -30.0, listener = rec)
        gate.process(sig, sig.size)
        val analyzer = SpectrumAnalyzer()
        analyzer.analyze(rec.captures.single().second)
        assertEquals(187.3, analyzer.findPeak(110.0, 420.0)!!.frequencyHz, 0.2)
    }

    @Test
    fun refractoryCooldownBlocksDoubleBounce() {
        val sr = 44_100
        val total = sr * 2
        val sig = TestSignals.hit(200.0, leadSilence = 1_000, totalSamples = total, tauSec = 0.05)
        // Second hit ("double bounce") 300 ms after the first → still inside capture + cooldown
        val bounce = TestSignals.hit(200.0, leadSilence = 0, totalSamples = 4_000, tauSec = 0.05, seed = 9)
        val bounceAt = 1_000 + (0.300 * sr).toInt()
        for (i in bounce.indices) sig[bounceAt + i] += bounce[i]
        // A genuine re-hit 1.2 s later must be captured
        val second = TestSignals.hit(200.0, leadSilence = 0, totalSamples = 8_000, tauSec = 0.05, seed = 11)
        val secondAt = 1_000 + (1.2 * sr).toInt()
        for (i in second.indices) sig[secondAt + i] += second[i]

        val rec = Recorder()
        val gate = TransientGate(thresholdDb = -30.0, listener = rec)
        feedInRandomChunks(gate, sig)

        assertEquals(2, rec.captures.size)
        assertTrue(rec.triggers[1] in secondAt.toLong()..secondAt + 5L)
    }

    @Test
    fun longRingingDrumDoesNotRetriggerItself() {
        // τ = 1.5 s: still far above threshold when the 400 ms cooldown ends.
        val sig = TestSignals.hit(90.0, leadSilence = 1_000, totalSamples = 44_100 * 2, tauSec = 1.5)
        val rec = Recorder()
        val gate = TransientGate(thresholdDb = -40.0, listener = rec)
        feedInRandomChunks(gate, sig)
        assertEquals(1, rec.triggers.size)
    }

    @Test
    fun quietSignalBelowThresholdNeverTriggers() {
        val sig = TestSignals.hit(200.0, leadSilence = 1_000, totalSamples = 20_000, amplitude = 0.005)
        val rec = Recorder()
        val gate = TransientGate(thresholdDb = -20.0, listener = rec)
        gate.process(sig, sig.size)
        assertTrue(rec.triggers.isEmpty())
        assertTrue(gate.lastBlockPeakDb < -20.0)
    }
}
