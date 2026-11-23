package com.drumtuner.app.dsp

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Synthetic drum-like test signals. */
object TestSignals {
    const val SR = 44_100

    /** Exponentially decaying partials: (freqHz, amplitude) list, decay time-constant [tauSec]. */
    fun decayingTone(
        partials: List<Pair<Double, Double>>,
        samples: Int,
        tauSec: Double = 0.3,
        noise: Double = 0.0,
        seed: Int = 1,
        phase: Double = 0.37,
    ): FloatArray {
        val rnd = Random(seed)
        return FloatArray(samples) { n ->
            val t = n.toDouble() / SR
            var v = 0.0
            for ((f, a) in partials) v += a * sin(2 * PI * f * t + phase)
            v *= exp(-t / tauSec)
            if (noise > 0) v += noise * (rnd.nextDouble() * 2 - 1)
            v.toFloat()
        }
    }

    /**
     * A full "hit": [leadSilence] samples of quiet noise, a 5 ms broadband stick click,
     * then a decaying tone at [freqHz]. Returns the signal and the onset index.
     */
    fun hit(
        freqHz: Double,
        leadSilence: Int,
        totalSamples: Int,
        tauSec: Double = 0.35,
        amplitude: Double = 0.6,
        seed: Int = 7,
    ): FloatArray {
        val rnd = Random(seed)
        val out = FloatArray(totalSamples)
        for (i in 0 until totalSamples) out[i] = (0.0005 * (rnd.nextDouble() * 2 - 1)).toFloat()
        val click = (SR * 0.005).toInt()
        for (n in 0 until totalSamples - leadSilence) {
            val t = n.toDouble() / SR
            var v = amplitude * exp(-t / tauSec) * sin(2 * PI * freqHz * t)
            if (n < click) v += 1.5 * amplitude * (rnd.nextDouble() * 2 - 1) * (1.0 - n.toDouble() / click)
            out[leadSilence + n] += v.coerceIn(-1.0, 1.0).toFloat()
        }
        return out
    }
}
