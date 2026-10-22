package com.tkleisas.multituner

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tkleisas.multituner.audio.MetronomeAudioEngine
import com.tkleisas.multituner.model.DrumPattern
import com.tkleisas.multituner.model.DrumPatterns
import com.tkleisas.multituner.tuner.TapTempo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackUiState(
    val bpm: Int = 120,
    val beatsPerBar: Int = 4,
    val subdivision: Int = 1,
    val volume: Float = 0.7f,
    val accent: Boolean = true,
    val metronomePlaying: Boolean = false,
    val drumsPlaying: Boolean = false,
    val currentBeat: Int = 0,
    val beatPhase: Float = 0.0f,
    val currentDrumStep: Int = 0,
    val drumPattern: DrumPattern = DrumPatterns.default.pattern,
    val drumPresetId: String = DrumPatterns.default.id,
    val swing: Float = 0.0f,
    val drumVolume: Float = 0.8f,
    val sampleRate: Int = 0,
    val error: String? = null,
)

class PlaybackViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("multituner", 0)
    private val tapTempo = TapTempo()
    private var engine: MetronomeAudioEngine? = null

    private val initialPattern = prefs.getString(KEY_DRUM_PATTERN, null)
        ?.let(DrumPattern::decode)
        ?: DrumPatterns.default.pattern
    private val initialPresetId = prefs.getString(KEY_DRUM_PRESET, DrumPatterns.default.id)
        ?.takeIf { it == "custom" || DrumPatterns.byId(it) != null }
        ?: DrumPatterns.default.id

    private val _uiState = MutableStateFlow(
        PlaybackUiState(
            bpm = prefs.getInt(KEY_BPM, 120).coerceIn(MIN_BPM, MAX_BPM),
            beatsPerBar = prefs.getInt(KEY_BEATS, 4).takeIf { it in SUPPORTED_BEATS } ?: 4,
            subdivision = prefs.getInt(KEY_SUBDIVISION, 1)
                .takeIf { it in SUPPORTED_SUBDIVISIONS } ?: 1,
            volume = prefs.getFloat(KEY_VOLUME, 0.7f).coerceIn(0.0f, 1.0f),
            accent = prefs.getBoolean(KEY_ACCENT, true),
            drumPattern = initialPattern,
            drumPresetId = initialPresetId,
            swing = prefs.getFloat(KEY_SWING, 0.0f).coerceIn(0.0f, 0.6f),
            drumVolume = prefs.getFloat(KEY_DRUM_VOLUME, 0.8f).coerceIn(0.0f, 1.0f),
        ),
    )
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                val state = _uiState.value
                val audio = engine
                val anyPlaying = state.metronomePlaying || state.drumsPlaying
                if (anyPlaying && audio != null) {
                    if (!audio.isReady ||
                        (!audio.isMetronomePlaying() && !audio.isDrumsPlaying())
                    ) {
                        audio.close()
                        engine = null
                        _uiState.update {
                            it.copy(
                                metronomePlaying = false,
                                drumsPlaying = false,
                                currentBeat = 0,
                                beatPhase = 0.0f,
                                currentDrumStep = 0,
                                sampleRate = 0,
                                error = "Audio output stopped",
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                currentBeat = audio.currentBeat(),
                                beatPhase = audio.beatPhase(),
                                currentDrumStep = audio.currentDrumStep(),
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

    fun setDrumVolume(value: Float) {
        val clamped = value.coerceIn(0.0f, 1.0f)
        prefs.edit().putFloat(KEY_DRUM_VOLUME, clamped).apply()
        _uiState.update { it.copy(drumVolume = clamped) }
        engine?.setDrumVolume(clamped)
    }

    fun setSwing(value: Float) {
        val clamped = value.coerceIn(0.0f, 0.6f)
        prefs.edit().putFloat(KEY_SWING, clamped).apply()
        _uiState.update { it.copy(swing = clamped) }
        engine?.setDrumSwing(clamped)
    }

    fun tapTempo() {
        tapTempo.tap(SystemClock.elapsedRealtime())?.let(::setBpm)
    }

    fun toggleMetronome() {
        val state = _uiState.value
        if (state.metronomePlaying) {
            engine?.setMetronomePlaying(false)
            _uiState.update { it.copy(metronomePlaying = false) }
            return
        }

        val audio = ensureEngine() ?: return
        applySettings(audio, state)
        audio.setMetronomePlaying(true)
        if (!audio.isMetronomePlaying()) {
            _uiState.update { it.copy(error = "Could not start audio output") }
            return
        }

        val reset = !state.drumsPlaying
        _uiState.update {
            it.copy(
                metronomePlaying = true,
                currentBeat = if (reset) 0 else it.currentBeat,
                beatPhase = if (reset) 0.0f else it.beatPhase,
                currentDrumStep = if (reset) 0 else it.currentDrumStep,
                sampleRate = audio.sampleRate(),
                error = null,
            )
        }
    }

    fun toggleDrums() {
        val state = _uiState.value
        if (state.drumsPlaying) {
            engine?.setDrumsPlaying(false)
            _uiState.update { it.copy(drumsPlaying = false) }
            return
        }

        val audio = ensureEngine() ?: return
        applySettings(audio, state)
        audio.setDrumsPlaying(true)
        if (!audio.isDrumsPlaying()) {
            _uiState.update { it.copy(error = "Could not start audio output") }
            return
        }

        val reset = !state.metronomePlaying
        _uiState.update {
            it.copy(
                drumsPlaying = true,
                currentBeat = if (reset) 0 else it.currentBeat,
                beatPhase = if (reset) 0.0f else it.beatPhase,
                currentDrumStep = if (reset) 0 else it.currentDrumStep,
                sampleRate = audio.sampleRate(),
                error = null,
            )
        }
    }

    fun cycleDrumStep(track: Int, step: Int) {
        val state = _uiState.value
        val updated = state.drumPattern.cycle(track, step)
        if (updated == state.drumPattern) return
        prefs.edit()
            .putString(KEY_DRUM_PATTERN, updated.encode())
            .putString(KEY_DRUM_PRESET, "custom")
            .apply()
        _uiState.update {
            it.copy(drumPattern = updated, drumPresetId = "custom")
        }
        engine?.setDrumStep(track, step, updated.level(track, step))
    }

    fun selectDrumPreset(id: String) {
        val preset = DrumPatterns.byId(id) ?: return
        prefs.edit()
            .putString(KEY_DRUM_PATTERN, preset.pattern.encode())
            .putString(KEY_DRUM_PRESET, preset.id)
            .apply()
        _uiState.update {
            it.copy(drumPattern = preset.pattern, drumPresetId = preset.id)
        }
        engine?.setDrumPattern(preset.pattern.toNativeLevels())
    }

    fun onAppBackground() {
        engine?.setMetronomePlaying(false)
        engine?.setDrumsPlaying(false)
        engine?.close()
        engine = null
        _uiState.update {
            it.copy(
                metronomePlaying = false,
                drumsPlaying = false,
                currentBeat = 0,
                beatPhase = 0.0f,
                currentDrumStep = 0,
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

    private fun applySettings(audio: MetronomeAudioEngine, state: PlaybackUiState) {
        audio.setBpm(state.bpm.toDouble())
        audio.setBeatsPerBar(state.beatsPerBar)
        audio.setSubdivision(state.subdivision)
        audio.setVolume(state.volume)
        audio.setAccentEnabled(state.accent)
        audio.setDrumPattern(state.drumPattern.toNativeLevels())
        audio.setDrumSwing(state.swing)
        audio.setDrumVolume(state.drumVolume)
    }

    private companion object {
        const val KEY_BPM = "metronome_bpm"
        const val KEY_BEATS = "metronome_beats"
        const val KEY_SUBDIVISION = "metronome_subdivision"
        const val KEY_VOLUME = "metronome_volume"
        const val KEY_ACCENT = "metronome_accent"
        const val KEY_DRUM_PATTERN = "drum_pattern"
        const val KEY_DRUM_PRESET = "drum_preset"
        const val KEY_SWING = "drum_swing"
        const val KEY_DRUM_VOLUME = "drum_volume"

        const val MIN_BPM = 30
        const val MAX_BPM = 300
        const val POLL_INTERVAL_MS = 33L

        val SUPPORTED_BEATS = listOf(2, 3, 4, 6)
        val SUPPORTED_SUBDIVISIONS = listOf(1, 2, 4)
    }
}
