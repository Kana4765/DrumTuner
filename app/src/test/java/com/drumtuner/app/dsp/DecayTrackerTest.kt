package com.drumtuner.app.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ln

class DecayTrackerTest {

    /** Amplitude e^(-t/τ) drops 20 dB after τ·ln(10). */
    private fun expected20dbMs(tau: Double) = tau * ln(10.0) * 1000.0

    @Test
    fun measures20dbDecayOfExponentialTone() {
        for (tau in listOf(0.08, 0.18, 0.4)) {
            val sig = TestSignals.decayingTone(listOf(150.0 to 0.7), 44_100 * 3, tauSec = tau)
            val tracker = DecayTracker()
            tracker.start(noiseFloorDb = -100.0)
            var result: Double? = null
            var i = 0
            while (result == null && i < sig.size) {
                val chunk = sig.copyOfRange(i, minOf(sig.size, i + 1024))
                result = tracker.process(chunk, chunk.size)
                i += 1024
            }
            assertNotNull(result)
            assertEquals("τ=$tau", expected20dbMs(tau), result!!, 15.0)
        }
    }

    @Test
    fun extrapolatesWhenInterruptedEarly() {
        val tau = 0.4 // 921 ms to drop 20 dB
        val sig = TestSignals.decayingTone(listOf(120.0 to 0.7), (44_100 * 0.5).toInt(), tauSec = tau)
        val tracker = DecayTracker()
        tracker.start(-100.0)
        tracker.process(sig, sig.size)
        val result = tracker.finish()
        assertNotNull(result)
        assertEquals(expected20dbMs(tau), result!!, 60.0)
    }

    @Test
    fun deadHeadDecaysMuchFaster() {
        fun measure(tau: Double): Double {
            val sig = TestSignals.decayingTone(listOf(200.0 to 0.7), 44_100 * 2, tauSec = tau)
            val t = DecayTracker()
            t.start(-100.0)
            return t.process(sig, sig.size) ?: t.finish()!!
        }
        val healthy = measure(0.3)
        val dead = measure(0.12)
        assertTrue(dead < healthy * 0.65)
    }
}
