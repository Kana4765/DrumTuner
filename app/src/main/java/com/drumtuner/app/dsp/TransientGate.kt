package com.drumtuner.app.dsp

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Hit detector that skips the stick impact and captures the clean resonance tail.
 *
 *  IDLE ──(peak ≥ threshold, rising edge)──▶ SKIP 25 ms ──▶ CAPTURE 8192 samples ──▶ COOLDOWN 400 ms ──▶ IDLE
 *
 * A trigger needs a *rising edge*: either the gate is re-armed (the peak-hold envelope fell
 * below threshold − [rearmHysteresisDb] since the last hit) or the block peak jumped at least
 * [onsetJumpDb] above the envelope. The envelope is a peak follower with a fixed release of
 * [releaseDbPerSecond], so it is independent of how the audio is chunked. This stops a
 * long-ringing floor tom from re-triggering itself once the cooldown ends, while still
 * catching a genuine new hit.
 *
 * Pure Kotlin (no Android APIs) so it can be unit tested on the JVM.
 */
class TransientGate(
    val sampleRate: Int = 44_100,
    val skipSamples: Int = (sampleRate * 0.025).toInt(),        // 1,102 samples at 44.1 kHz
    val captureSize: Int = 8_192,                                // ~185.8 ms
    val refractorySamples: Int = (sampleRate * 0.400).toInt(),   // 400 ms
    thresholdDb: Double = -30.0,
    private val listener: Listener,
) {
    interface Listener {
        /** A hit crossed the threshold at absolute sample index [sampleIndex]. */
        fun onTrigger(sampleIndex: Long)

        /** [buffer] holds [captureSize] samples starting [skipSamples] after the trigger. Owned by the receiver. */
        fun onCapture(buffer: FloatArray, triggerSampleIndex: Long)
    }

    enum class State { IDLE, SKIP, CAPTURE, COOLDOWN }

    var thresholdDb: Double = thresholdDb
        set(value) {
            field = value
            thresholdAmp = dbToAmp(value)
        }

    private var thresholdAmp = dbToAmp(thresholdDb)

    var state: State = State.IDLE
        private set

    /** Peak level of the most recent processed block, in dBFS. Drives the input meter. */
    @Volatile
    var lastBlockPeakDb: Double = SILENCE_DB
        private set

    private var armed = true
    private var envelopeAmp = 0.0
    private val releasePerSample = dbToAmp(-RELEASE_DB_PER_SECOND / sampleRate)
    private var skipRemaining = 0
    private var cooldownRemaining = 0
    private var capturePos = 0
    private var captureBuffer = FloatArray(captureSize)
    private var triggerIndex = 0L
    private var totalSamples = 0L

    val rearmHysteresisDb = 3.0
    val onsetJumpDb = 9.0
    val releaseDbPerSecond = RELEASE_DB_PER_SECOND

    fun reset() {
        state = State.IDLE
        armed = true
        envelopeAmp = 0.0
        skipRemaining = 0
        cooldownRemaining = 0
        capturePos = 0
    }

    /** Feed [length] samples (range -1..1). */
    fun process(samples: FloatArray, length: Int = samples.size) {
        var i = 0
        while (i < length) {
            when (state) {
                State.IDLE, State.COOLDOWN -> {
                    val n = min(BLOCK, length - i)
                    val peak = blockPeak(samples, i, n)
                    val peakDb = ampToDb(peak)
                    lastBlockPeakDb = peakDb
                    val envelopeBeforeDb = ampToDb(envelopeAmp)

                    if (state == State.COOLDOWN) {
                        updateEnvelope(peak, n)
                        cooldownRemaining -= n
                        if (cooldownRemaining <= 0) state = State.IDLE
                        totalSamples += n
                        i += n
                        continue
                    }

                    val risingEdge = armed || (peakDb - envelopeBeforeDb >= onsetJumpDb)
                    if (peak >= thresholdAmp && risingEdge) {
                        // Sample-accurate onset: first sample at/above threshold inside this block
                        var onset = i
                        while (onset < i + n && abs(samples[onset]) < thresholdAmp) onset++
                        triggerIndex = totalSamples + (onset - i)
                        updateEnvelope(blockPeak(samples, i, onset - i), onset - i)
                        armed = false
                        state = State.SKIP
                        skipRemaining = skipSamples
                        capturePos = 0
                        listener.onTrigger(triggerIndex)
                        totalSamples += (onset - i)
                        i = onset
                        continue
                    }
                    updateEnvelope(peak, n)
                    totalSamples += n
                    i += n
                }

                State.SKIP -> {
                    val n = min(skipRemaining, length - i)
                    updateMeter(samples, i, n)
                    skipRemaining -= n
                    totalSamples += n
                    i += n
                    if (skipRemaining <= 0) state = State.CAPTURE
                }

                State.CAPTURE -> {
                    val n = min(captureSize - capturePos, length - i)
                    System.arraycopy(samples, i, captureBuffer, capturePos, n)
                    updateMeter(samples, i, n)
                    capturePos += n
                    totalSamples += n
                    i += n
                    if (capturePos >= captureSize) {
                        val done = captureBuffer
                        captureBuffer = FloatArray(captureSize)
                        state = State.COOLDOWN
                        cooldownRemaining = refractorySamples
                        listener.onCapture(done, triggerIndex)
                    }
                }
            }
        }
    }

    private fun updateMeter(samples: FloatArray, from: Int, n: Int) {
        if (n <= 0) return
        val peak = blockPeak(samples, from, n)
        lastBlockPeakDb = ampToDb(peak)
        updateEnvelope(peak, n)
    }

    /** Peak-hold envelope with fixed release; re-arms the gate once it falls below threshold − hysteresis. */
    private fun updateEnvelope(peak: Double, n: Int) {
        if (n <= 0) return
        val released = envelopeAmp * releasePerSample.pow(n)
        envelopeAmp = if (peak > released) peak else released
        if (ampToDb(envelopeAmp) < thresholdDb - rearmHysteresisDb) armed = true
    }

    private fun blockPeak(samples: FloatArray, from: Int, n: Int): Double {
        var peak = 0f
        for (j in from until from + n) {
            val a = abs(samples[j])
            if (a > peak) peak = a
        }
        return peak.toDouble()
    }

    companion object {
        /** Level-detection block: 128 samples ≈ 2.9 ms. */
        const val BLOCK = 128
        const val SILENCE_DB = -120.0
        /** Envelope release: 100 dB/s (≈ 0.3 dB per 128-sample block). */
        const val RELEASE_DB_PER_SECOND = 100.0

        fun ampToDb(amp: Double): Double = if (amp <= 1e-6) SILENCE_DB else max(SILENCE_DB, 20.0 * log10(amp))
        fun dbToAmp(db: Double): Double = 10.0.pow(db / 20.0)
    }
}
