package com.drumtuner.app.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class FftTest {

    @Test
    fun matchesNaiveDftOnRandomInput() {
        val n = 64
        val rnd = Random(42)
        val xr = DoubleArray(n) { rnd.nextDouble() * 2 - 1 }
        val xi = DoubleArray(n) { rnd.nextDouble() * 2 - 1 }

        val re = xr.copyOf()
        val im = xi.copyOf()
        Fft(n).transform(re, im)

        for (k in 0 until n) {
            var sr = 0.0
            var si = 0.0
            for (t in 0 until n) {
                val ang = -2 * PI * k * t / n
                sr += xr[t] * cos(ang) - xi[t] * sin(ang)
                si += xr[t] * sin(ang) + xi[t] * cos(ang)
            }
            assertEquals("re[$k]", sr, re[k], 1e-9)
            assertEquals("im[$k]", si, im[k], 1e-9)
        }
    }

    @Test
    fun impulseGivesFlatSpectrum() {
        val n = 8192
        val re = DoubleArray(n).also { it[0] = 1.0 }
        val im = DoubleArray(n)
        Fft(n).transform(re, im)
        for (k in 0 until n) {
            assertEquals(1.0, re[k], 1e-12)
            assertEquals(0.0, im[k], 1e-12)
        }
    }

    @Test
    fun binCenteredSineLandsInItsBin() {
        val n = 8192
        val bin = 37
        val re = DoubleArray(n) { cos(2 * PI * bin * it / n) }
        val im = DoubleArray(n)
        Fft(n).transform(re, im)
        assertEquals(n / 2.0, re[bin], 1e-6)
        assertEquals(n / 2.0, re[n - bin], 1e-6)
        assertEquals(0.0, re[bin + 1], 1e-6)
    }

    @Test
    fun rejectsNonPowerOfTwo() {
        assertThrows(IllegalArgumentException::class.java) { Fft(1000) }
    }
}
