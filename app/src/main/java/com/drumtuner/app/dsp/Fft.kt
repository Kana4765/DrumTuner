package com.drumtuner.app.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * In-place iterative Radix-2 Cooley–Tukey FFT.
 *
 * Twiddle factors and the bit-reversal permutation are pre-computed once per size,
 * so repeated transforms in the audio thread allocate nothing.
 */
class Fft(val size: Int) {

    private val cosTable: DoubleArray
    private val sinTable: DoubleArray
    private val bitReversed: IntArray

    init {
        require(size >= 2 && (size and (size - 1)) == 0) { "FFT size must be a power of two, was $size" }
        val half = size / 2
        cosTable = DoubleArray(half) { cos(2.0 * PI * it / size) }
        sinTable = DoubleArray(half) { sin(2.0 * PI * it / size) }

        val bits = Integer.numberOfTrailingZeros(size)
        bitReversed = IntArray(size) { i -> Integer.reverse(i) ushr (32 - bits) }
    }

    /**
     * Forward transform. [re] and [im] are overwritten with the complex spectrum.
     * Uses the e^(-j·2πkn/N) sign convention.
     */
    fun transform(re: DoubleArray, im: DoubleArray) {
        require(re.size == size && im.size == size) { "Buffers must have length $size" }

        // Bit-reversal permutation
        for (i in 0 until size) {
            val j = bitReversed[i]
            if (j > i) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }

        // Butterflies
        var len = 2
        while (len <= size) {
            val halfLen = len / 2
            val tableStep = size / len
            var start = 0
            while (start < size) {
                var k = 0
                for (j in start until start + halfLen) {
                    val wr = cosTable[k]
                    val wi = -sinTable[k]
                    val l = j + halfLen
                    val xr = re[l] * wr - im[l] * wi
                    val xi = re[l] * wi + im[l] * wr
                    re[l] = re[j] - xr
                    im[l] = im[j] - xi
                    re[j] += xr
                    im[j] += xi
                    k += tableStep
                }
                start += len
            }
            len = len shl 1
        }
    }
}
