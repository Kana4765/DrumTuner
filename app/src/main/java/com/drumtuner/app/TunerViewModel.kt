package com.drumtuner.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drumtuner.app.audio.AudioTunerEngine
import com.drumtuner.app.audio.EngineEvent
import com.drumtuner.app.data.KitProfile
import com.drumtuner.app.data.KitRepository
import com.drumtuner.app.session.Readout
import com.drumtuner.app.session.SessionState
import com.drumtuner.app.session.TunerSession
import com.drumtuner.app.tuning.DrumType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Thin Android wrapper around [TunerSession]: wires the audio engine's events in,
 * republishes state for Compose, persists kits, and handles SAF import/export.
 * All session access happens on the main thread.
 */
class TunerViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = KitRepository(app)
    private val engine = AudioTunerEngine(app)
    private val session = TunerSession(repo.loadKits(), repo.activeKitId())

    private val _state = MutableStateFlow(session.state)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    val inputLevelDb: StateFlow<Float> = engine.inputLevelDb
    val listening: StateFlow<Boolean> = engine.running
    val sourceLabel: StateFlow<String> = engine.sourceLabel

    private var savedKits: List<KitProfile>? = null
    private var savedActiveId: String? = null

    init {
        session.setThreshold(repo.loadThreshold(-30f))
        publish()
        viewModelScope.launch {
            engine.events.collect { event ->
                when (event) {
                    is EngineEvent.Hit -> session.onHit(event.id, event.frequencyHz, event.sizzleRatio)
                    is EngineEvent.Decay -> session.onDecay(event.id, event.decayMs)
                }
                publish()
            }
        }
    }

    private fun publish() {
        val s = session.state
        _state.value = s
        engine.searchBand = session.searchBand(s)
        engine.thresholdDb = s.thresholdDb.toDouble()
        if (s.kits != savedKits || s.activeKitId != savedActiveId) {
            repo.save(s.kits, s.activeKitId)
            savedKits = s.kits
            savedActiveId = s.activeKitId
        }
    }

    /** Runs a session action and republishes. */
    fun act(action: TunerSession.() -> Unit) {
        session.action()
        publish()
    }

    fun setThreshold(db: Float) {
        act { setThreshold(db) }
        repo.saveThreshold(session.state.thresholdDb)
    }

    // Derived values for the UI (pure functions of the given state)
    fun readout(s: SessionState): Readout = session.readout(s)
    fun lugReadout(lug: Int, s: SessionState): Readout = session.lugReadout(lug, s)
    fun deadLugs(s: SessionState): List<Int> = session.deadLugs(s)
    fun referenceHz(s: SessionState): Double? = session.referenceHz(s)
    fun lineCheckReferenceHz(drum: DrumType, s: SessionState): Double? = session.lineCheckReferenceHz(drum, s)

    fun startListening(): Boolean {
        val ok = engine.start()
        if (!ok) act { showMessage("Microphone unavailable — is another app recording?") }
        return ok
    }

    fun stopListening() = engine.stop()

    fun exportTo(uri: Uri) {
        val json = session.exportJson()
        val resolver = getApplication<Application>().contentResolver
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val out = try {
                        resolver.openOutputStream(uri, "wt")
                    } catch (e: Exception) {
                        resolver.openOutputStream(uri)
                    }
                    out?.use { it.write(json.toByteArray(Charsets.UTF_8)) } != null
                } catch (e: Exception) {
                    false
                }
            }
            act { showMessage(if (ok) "Exported ${state.kits.size} kit(s)" else "Export failed") }
        }
    }

    fun importFrom(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                try {
                    resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                } catch (e: Exception) {
                    null
                }
            }
            act {
                if (text == null) {
                    showMessage("Could not read file")
                } else {
                    val count = try {
                        importJson(text)
                    } catch (e: Exception) {
                        -1
                    }
                    when {
                        count < 0 -> showMessage("Not a valid DrumTuner kit file")
                        count == 0 -> showMessage("No kits found in file")
                    }
                }
            }
        }
    }

    override fun onCleared() {
        engine.stop()
        super.onCleared()
    }
}
