package com.drumtuner.app.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import com.drumtuner.app.dsp.DecayTracker
import com.drumtuner.app.dsp.SnareWire
import com.drumtuner.app.dsp.SpectrumAnalyzer
import com.drumtuner.app.dsp.TransientGate
import com.drumtuner.app.tuning.Band
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Microphone capture + DSP pipeline.
 *
 *  AudioRecord (44.1 kHz mono PCM16, UNPROCESSED or VOICE_RECOGNITION)
 *    → TransientGate (threshold, 25 ms skip, 8192-sample capture, 400 ms cooldown)
 *    → SpectrumAnalyzer (Hann + FFT + parabolic peak in the active band) + sizzle ratio
 *    → DecayTracker (20 dB sustain)
 *
 * Runs on its own URGENT_AUDIO thread; results are published through [events].
 */
class AudioTunerEngine(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<EngineEvent> = _events.asSharedFlow()

    private val _inputLevelDb = MutableStateFlow(SILENCE_DB.toFloat())
    /** Peak input level (dBFS) of the latest buffer, for the live meter. */
    val inputLevelDb: StateFlow<Float> = _inputLevelDb.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _sourceLabel = MutableStateFlow("")
    /** Human-readable capture source, e.g. "Unprocessed mic" or "USB: Scarlett 2i2". */
    val sourceLabel: StateFlow<String> = _sourceLabel.asStateFlow()

    @Volatile
    var searchBand: Band = Band(45.0, 650.0)

    @Volatile
    var thresholdDb: Double = -30.0

    @Volatile
    private var stopRequested = false
    private var thread: Thread? = null
    @Volatile
    private var record: AudioRecord? = null
    private var baseSourceLabel = ""

    private val mainHandler = Handler(Looper.getMainLooper())

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = routeToExternalInput()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = routeToExternalInput()
    }

    fun isUnprocessedSupported(): Boolean =
        audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"

    /** Starts capture. The caller must already hold RECORD_AUDIO. Returns false on failure. */
    @SuppressLint("MissingPermission")
    @Synchronized
    fun start(): Boolean {
        if (thread != null) return true

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) return false
        val bufferBytes = max(minBuf * 4, READ_SAMPLES * 2 * 4)

        val sources = buildList {
            if (isUnprocessedSupported()) add(MediaRecorder.AudioSource.UNPROCESSED to "Unprocessed mic")
            add(MediaRecorder.AudioSource.VOICE_RECOGNITION to "Voice-recognition mic (no AGC)")
            add(MediaRecorder.AudioSource.MIC to "Default mic")
        }

        var created: AudioRecord? = null
        for ((source, label) in sources) {
            val r = try {
                AudioRecord(source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferBytes)
            } catch (e: Exception) {
                Log.w(TAG, "AudioRecord source $source failed", e)
                null
            }
            if (r != null && r.state == AudioRecord.STATE_INITIALIZED) {
                created = r
                baseSourceLabel = label
                break
            }
            r?.release()
        }
        val rec = created ?: return false
        record = rec
        routeToExternalInput()
        audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler)

        try {
            rec.startRecording()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "startRecording failed", e)
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
            releaseRecord()
            return false
        }

        stopRequested = false
        thread = Thread({ captureLoop(rec) }, "DrumTunerAudio").also { it.start() }
        _running.value = true
        return true
    }

    @Synchronized
    fun stop() {
        stopRequested = true
        thread?.let {
            try {
                it.join(1_000)
            } catch (ignored: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        thread = null
        try {
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
        } catch (ignored: Exception) {
        }
        releaseRecord()
        _running.value = false
        _inputLevelDb.value = SILENCE_DB.toFloat()
    }

    private fun releaseRecord() {
        record?.let {
            try {
                it.stop()
            } catch (ignored: IllegalStateException) {
            }
            it.release()
        }
        record = null
    }

    /**
     * Prefers a USB audio interface / USB headset / wired (piezo or headset) input when one is
     * plugged in; otherwise lets the system use the built-in microphone.
     */
    private fun routeToExternalInput() {
        val rec = record ?: return
        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val priority = listOf(
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
        )
        val external = priority.firstNotNullOfOrNull { type -> inputs.firstOrNull { it.type == type } }
        rec.setPreferredDevice(external)
        _sourceLabel.value = if (external != null) {
            val name = external.productName?.toString()?.takeIf { it.isNotBlank() } ?: "External input"
            val kind = if (external.type == AudioDeviceInfo.TYPE_WIRED_HEADSET) "Wired" else "USB"
            "$kind: $name"
        } else {
            baseSourceLabel
        }
    }

    private fun captureLoop(rec: AudioRecord) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

        val analyzer = SpectrumAnalyzer(SAMPLE_RATE, FFT_SIZE)
        val decay = DecayTracker(SAMPLE_RATE)
        val pcm = ShortArray(READ_SAMPLES)
        val samples = FloatArray(READ_SAMPLES)

        var nextId = 1L
        var currentId = 0L
        var bufferStart = 0L
        var triggerOffsetInBuffer = -1
        var noiseFloorDb = -90.0

        val gate = TransientGate(
            sampleRate = SAMPLE_RATE,
            captureSize = FFT_SIZE,
            thresholdDb = thresholdDb,
            listener = object : TransientGate.Listener {
                override fun onTrigger(sampleIndex: Long) {
                    // A new hit interrupts the previous sustain measurement
                    if (decay.isActive) {
                        decay.finish()?.let { _events.tryEmit(EngineEvent.Decay(currentId, it)) }
                    }
                    currentId = nextId++
                    triggerOffsetInBuffer = (sampleIndex - bufferStart).toInt()
                    decay.start(noiseFloorDb)
                }

                override fun onCapture(buffer: FloatArray, triggerSampleIndex: Long) {
                    val band = searchBand
                    analyzer.analyze(buffer)
                    val peak = analyzer.findPeak(band.lowHz, band.highHz)
                    // Reject peaks that are barely above the noise (e.g. a dead band)
                    val valid = peak != null && peak.levelDb > MIN_PEAK_DB
                    val sizzle = SnareWire.sizzleRatio(analyzer)
                    _events.tryEmit(
                        EngineEvent.Hit(
                            id = currentId,
                            frequencyHz = if (valid) peak!!.frequencyHz else null,
                            peakLevelDb = peak?.levelDb,
                            sizzleRatio = sizzle,
                            band = band,
                        )
                    )
                }
            },
        )

        while (!stopRequested) {
            val n = rec.read(pcm, 0, READ_SAMPLES)
            if (n <= 0) {
                if (n < 0) Log.w(TAG, "AudioRecord.read error $n")
                try {
                    Thread.sleep(5)
                } catch (ignored: InterruptedException) {
                    break
                }
                continue
            }

            var peak = 0f
            var sumSq = 0.0
            for (i in 0 until n) {
                val v = pcm[i] / 32768f
                samples[i] = v
                val a = abs(v)
                if (a > peak) peak = a
                sumSq += (v * v).toDouble()
            }
            _inputLevelDb.value = TransientGate.ampToDb(peak.toDouble()).toFloat()

            gate.thresholdDb = thresholdDb
            triggerOffsetInBuffer = -1
            gate.process(samples, n)

            if (decay.isActive) {
                val from = if (triggerOffsetInBuffer in 0 until n) triggerOffsetInBuffer else 0
                val result = if (from == 0) {
                    decay.process(samples, n)
                } else {
                    val tail = samples.copyOfRange(from, n)
                    decay.process(tail, tail.size)
                }
                if (result != null) _events.tryEmit(EngineEvent.Decay(currentId, result))
            } else if (gate.state == TransientGate.State.IDLE) {
                // Track the room noise floor while nothing is ringing
                val rms = sqrt(sumSq / n)
                val db = if (rms <= 1e-9) -120.0 else 20.0 * log10(rms)
                noiseFloorDb = 0.95 * noiseFloorDb + 0.05 * db
            }
            bufferStart += n
        }
    }

    companion object {
        private const val TAG = "AudioTunerEngine"
        const val SAMPLE_RATE = 44_100
        const val FFT_SIZE = 8_192
        const val READ_SAMPLES = 1_024
        const val SILENCE_DB = -120.0
        /** Peaks quieter than this (dB re full-scale sine) are treated as "no tone". */
        const val MIN_PEAK_DB = -80.0
    }
}
