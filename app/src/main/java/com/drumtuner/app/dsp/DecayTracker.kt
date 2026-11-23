package com.drumtuner.app.dsp

import kotlin.math.log10
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Measures sustain: the time for the RMS envelope (10 ms frames) to fall by [dropDb]
 * (20 dB) from its post-impact maximum.
 *
 * The first [skipSamples] after the trigger are ignored so the stick transient does not
 * set the reference. If the envelope reaches the room noise floor, a new hit interrupts it,
 * or [maxDurationMs] elapses before the full drop, the decay is extrapolated from a
 * least-squares fit of the dB envelope.
 */
class DecayTracker(
    val sampleRate: Int = 44_100,
    val frameSize: Int = sampleRate / 100,       // 10 ms
    val dropDb: Double = 20.0,
    val skipSamples: Int = (sampleRate * 0.025).toInt(),
    val maxDurationMs: Double = 3_000.0,
    /** Frames after the skip in which the reference (peak) is searched. */
    val referenceSearchFrames: Int = 6,
) {
    private val frameMs = frameSize * 1000.0 / sampleRate
    private val maxFrames = (maxDurationMs / frameMs).toInt() + 1
    private val frames = DoubleArray(maxFrames)
    private var frameCount = 0

    private var sumSq = 0.0
    private var inFrame = 0
    private var skipped = 0
    private var noiseFloorDb = -120.0

    var isActive: Boolean = false
        private set

    fun start(noiseFloorDb: Double) {
        this.noiseFloorDb = noiseFloorDb
        frameCount = 0
        sumSq = 0.0
        inFrame = 0
        skipped = 0
        isActive = true
    }

    /** Feeds samples; returns the decay time in ms once finished, otherwise null. */
    fun process(samples: FloatArray, length: Int = samples.size): Double? {
        if (!isActive) return null
        var i = 0
        if (skipped < skipSamples) {
            val n = min(skipSamples - skipped, length)
            skipped += n
            i = n
        }
        while (i < length) {
            val s = samples[i].toDouble()
            sumSq += s * s
            inFrame++
            i++
            if (inFrame == frameSize) {
                val rms = sqrt(sumSq / frameSize)
                sumSq = 0.0
                inFrame = 0
                val result = addFrame(if (rms <= 1e-9) -180.0 else 20.0 * log10(rms))
                if (result != null || !isActive) return result
            }
        }
        return null
    }

    /** Forces completion (e.g. a new hit arrived) using extrapolation. */
    fun finish(): Double? {
        if (!isActive) return null
        isActive = false
        return extrapolate(frameCount)
    }

    private fun addFrame(db: Double): Double? {
        frames[frameCount++] = db
        if (frameCount < referenceSearchFrames) return null

        val ref = referenceIndex()
        val target = frames[ref] - dropDb
        for (f in ref + 1 until frameCount) {
            if (frames[f] <= target) {
                isActive = false
                val prev = frames[f - 1]
                val frac = if (prev > frames[f]) (prev - target) / (prev - frames[f]) else 1.0
                return ((f - 1 - ref) + frac) * frameMs
            }
        }
        // Reached the noise floor before the full drop: extrapolate from what we have.
        if (frameCount > ref + 1 && db <= noiseFloorDb + 3.0) {
            isActive = false
            return extrapolate(frameCount - 1)
        }
        if (frameCount >= maxFrames) {
            isActive = false
            return extrapolate(frameCount)
        }
        return null
    }

    private fun referenceIndex(): Int {
        var best = 0
        val limit = min(referenceSearchFrames, frameCount)
        for (f in 1 until limit) if (frames[f] > frames[best]) best = f
        return best
    }

    /** Linear regression of dB vs time on frames [ref, end). */
    private fun extrapolate(end: Int): Double? {
        if (end < 3) return null
        val ref = referenceIndex()
        val n = end - ref
        if (n < 3) return null
        var sx = 0.0; var sy = 0.0; var sxx = 0.0; var sxy = 0.0
        for (f in ref until end) {
            val x = (f - ref) * frameMs
            val y = frames[f]
            sx += x; sy += y; sxx += x * x; sxy += x * y
        }
        val denom = n * sxx - sx * sx
        if (denom == 0.0) return null
        val slope = (n * sxy - sx * sy) / denom   // dB per ms
        if (slope >= -1e-4) return null
        return min(dropDb / -slope, 20_000.0)
    }
}
