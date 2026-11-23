package com.drumtuner.app.dsp

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

/** Periodic Hann window (the right choice for spectral analysis). */
object HannWindow {
    fun create(size: Int): DoubleArray =
        DoubleArray(size) { 0.5 - 0.5 * cos(2.0 * PI * it / size) }
}

data class SpectralPeak(
    /** Interpolated frequency in Hz. */
    val frequencyHz: Double,
    /** Peak level in dB relative to a full-scale sine. */
    val levelDb: Double,
    /** Integer FFT bin of the raw maximum. */
    val bin: Int,
)

/**
 * Hann-windowed 8192-point spectrum analysis with parabolic peak interpolation.
 *
 * Not thread-safe: one instance per audio thread.
 */
class SpectrumAnalyzer(
    val sampleRate: Int = 44_100,
    val fftSize: Int = 8_192,
) {
    private val fft = Fft(fftSize)
    private val window = HannWindow.create(fftSize)
    private val re = DoubleArray(fftSize)
    private val im = DoubleArray(fftSize)

    /** Power spectrum |X[k]|² for k in 0..fftSize/2, valid after [analyze]. */
    val power = DoubleArray(fftSize / 2 + 1)

    val binHz: Double = sampleRate.toDouble() / fftSize

    /** Normalisation so a full-scale sine reads 0 dB at its peak bin (Hann coherent gain = 0.5). */
    private val fullScalePower: Double = (fftSize * 0.5 / 2.0).let { it * it }

    /**
     * Removes DC, applies the Hann window and computes the power spectrum.
     * [samples] must contain at least [fftSize] values in the range -1..1.
     */
    fun analyze(samples: FloatArray) {
        require(samples.size >= fftSize) { "Need $fftSize samples, got ${samples.size}" }
        var mean = 0.0
        for (i in 0 until fftSize) mean += samples[i]
        mean /= fftSize
        for (i in 0 until fftSize) {
            re[i] = (samples[i] - mean) * window[i]
            im[i] = 0.0
        }
        fft.transform(re, im)
        for (k in power.indices) power[k] = re[k] * re[k] + im[k] * im[k]
    }

    /**
     * Strongest local maximum between [lowHz] and [highHz], refined with parabolic
     * interpolation over bins k-1, k, k+1 on the log-magnitude spectrum.
     * Returns null if the band contains no local maximum.
     */
    fun findPeak(lowHz: Double, highHz: Double): SpectralPeak? {
        val first = max(1, ceil(lowHz / binHz).toInt())
        val last = min(power.size - 2, floor(highHz / binHz).toInt())
        if (first > last) return null

        var best = -1
        var bestPower = 0.0
        for (k in first..last) {
            val p = power[k]
            if (p > bestPower && p >= power[k - 1] && p >= power[k + 1]) {
                best = k
                bestPower = p
            }
        }
        if (best < 0 || bestPower <= 0.0) return null

        val a = log10(power[best - 1] + TINY)
        val b = log10(power[best] + TINY)
        val c = log10(power[best + 1] + TINY)
        val denom = a - 2.0 * b + c
        val delta = if (denom != 0.0) (0.5 * (a - c) / denom).coerceIn(-0.5, 0.5) else 0.0

        // Peak height of the fitted parabola (in log10 power units)
        val peakLog = b - 0.25 * (a - c) * delta
        val levelDb = 10.0 * (peakLog - log10(fullScalePower))
        return SpectralPeak((best + delta) * binHz, levelDb, best)
    }

    /** Sum of |X[k]|² for bins whose centre lies in [lowHz, highHz]. */
    fun bandEnergy(lowHz: Double, highHz: Double): Double {
        val first = max(0, ceil(lowHz / binHz).toInt())
        val last = min(power.size - 1, floor(highHz / binHz).toInt())
        var sum = 0.0
        for (k in first..last) sum += power[k]
        return sum
    }

    private companion object {
        const val TINY = 1e-20
    }
}
