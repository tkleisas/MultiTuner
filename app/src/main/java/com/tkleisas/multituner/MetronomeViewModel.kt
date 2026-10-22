package com.tkleisas.multituner

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tkleisas.multituner.audio.MetronomeAudioEngine
import com.tkleisas.multituner.tuner.TapTempo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MetronomeUiState(
    val bpm: Int = 120,
    val beatsPerBar: Int = 4,
    val subdivision: Int = 1,
    val volume: Float = 0.7f,
    val accent: Boolean = true,
    val isPlaying: Boolean = false,
    val currentBeat: Int = 0,
    val beatPhase: Float = 0.0f,
    val sampleRate: Int = 0,
    val error: String? = null,
)

class MetronomeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("multituner", 0)
    private val tapTempo = TapTempo()
    private var engine: MetronomeAudioEngine? = null

    private val _uiState = MutableStateFlow(
        MetronomeUiState(
            bpm = prefs.getInt(KEY_BPM, 120).coerceIn(MIN_BPM, MAX_BPM),
            beatsPerBar = prefs.getInt(KEY_BEATS, 4).takeIf { it in SUPPORTED_BEATS } ?: 4,
            subdivision = prefs.getInt(KEY_SUBDIVISION, 1)
                .takeIf { it in SUPPORTED_SUBDIVISIONS } ?: 1,
            volume = prefs.getFloat(KEY_VOLUME, 0.7f).coerceIn(0.0f, 1.0f),
            accent = prefs.getBoolean(KEY_ACCENT, true),
        ),
    )
    val uiState: StateFlow<MetronomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                val state = _uiState.value
                val audio = engine
                if (state.isPlaying && audio != null) {
                    if (!audio.isReady || !audio.isPlaying()) {
                        audio.close()
                        engine = null
                        _uiState.update {
                            it.copy(
                                isPlaying = false,
                                currentBeat = 0,
                                beatPhase = 0.0f,
                                sampleRate = 0,
                                error = "Audio output stopped",
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                currentBeat = audio.currentBeat(),
                                beatPhase = audio.beatPhase(),
                            )
                        }
                    }
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun setBpm(value: Int) {
        val clamped = value.coerceIn(MIN_BPM, MAX_BPM)
        if (clamped == _uiState.value.bpm) return
        prefs.edit().putInt(KEY_BPM, clamped).apply()
        _uiState.update { it.copy(bpm = clamped) }
        engine?.setBpm(clamped.toDouble())
    }

    fun adjustBpm(delta: Int) = setBpm(_uiState.value.bpm + delta)

    fun setBeatsPerBar(value: Int) {
        if (value !in SUPPORTED_BEATS) return
        prefs.edit().putInt(KEY_BEATS, value).apply()
        _uiState.update { it.copy(beatsPerBar = value) }
        engine?.setBeatsPerBar(value)
    }

    fun setSubdivision(value: Int) {
        if (value !in SUPPORTED_SUBDIVISIONS) return
        prefs.edit().putInt(KEY_SUBDIVISION, value).apply()
        _uiState.update { it.copy(subdivision = value) }
        engine?.setSubdivision(value)
    }

    fun setVolume(value: Float) {
        val clamped = value.coerceIn(0.0f, 1.0f)
        prefs.edit().putFloat(KEY_VOLUME, clamped).apply()
        _uiState.update { it.copy(volume = clamped) }
        engine?.setVolume(clamped)
    }

    fun toggleAccent() {
        val enabled = !_uiState.value.accent
        prefs.edit().putBoolean(KEY_ACCENT, enabled).apply()
        _uiState.update { it.copy(accent = enabled) }
        engine?.setAccentEnabled(enabled)
    }

    fun tapTempo() {
        tapTempo.tap(SystemClock.elapsedRealtime())?.let(::setBpm)
    }

    fun togglePlaying() {
        val state = _uiState.value
        if (state.isPlaying) {
            engine?.setPlaying(false)
            _uiState.update {
                it.copy(
                    isPlaying = false,
                    currentBeat = 0,
                    beatPhase = 0.0f,
                )
            }
            return
        }

        val audio = ensureEngine() ?: return
        applySettings(audio, state)
        audio.setPlaying(true)
        if (!audio.isPlaying()) {
            audio.close()
            engine = null
            _uiState.update { it.copy(error = "Could not start audio output") }
            return
        }

        _uiState.update {
            it.copy(
                isPlaying = true,
                currentBeat = 0,
                beatPhase = 0.0f,
                sampleRate = audio.sampleRate(),
                error = null,
            )
        }
    }

    fun onAppBackground() {
        engine?.setPlaying(false)
        engine?.close()
        engine = null
        _uiState.update {
            it.copy(
                isPlaying = false,
                currentBeat = 0,
                beatPhase = 0.0f,
                sampleRate = 0,
            )
        }
    }

    override fun onCleared() {
        engine?.close()
        engine = null
        super.onCleared()
    }

    private fun ensureEngine(): MetronomeAudioEngine? {
        val existing = engine
        if (existing != null) {
            if (existing.isReady) return existing
            existing.close()
            engine = null
        }

        val created = MetronomeAudioEngine()
        if (!created.isReady) {
            created.close()
            _uiState.update { it.copy(error = "Could not open audio output") }
            return null
        }

        engine = created
        applySettings(created, _uiState.value)
        _uiState.update { it.copy(sampleRate = created.sampleRate()) }
        return created
    }

    private fun applySettings(audio: MetronomeAudioEngine, state: MetronomeUiState) {
        audio.setBpm(state.bpm.toDouble())
        audio.setBeatsPerBar(state.beatsPerBar)
        audio.setSubdivision(state.subdivision)
        audio.setVolume(state.volume)
        audio.setAccentEnabled(state.accent)
    }

    private companion object {
        const val KEY_BPM = "metronome_bpm"
        const val KEY_BEATS = "metronome_beats"
        const val KEY_SUBDIVISION = "metronome_subdivision"
        const val KEY_VOLUME = "metronome_volume"
        const val KEY_ACCENT = "metronome_accent"

        const val MIN_BPM = 30
        const val MAX_BPM = 300
        const val POLL_INTERVAL_MS = 33L

        val SUPPORTED_BEATS = listOf(2, 3, 4, 6)
        val SUPPORTED_SUBDIVISIONS = listOf(1, 2, 4)
    }
}
