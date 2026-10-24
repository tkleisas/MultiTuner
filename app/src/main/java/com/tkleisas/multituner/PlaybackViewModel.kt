package com.tkleisas.multituner

import android.app.Application
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tkleisas.multituner.audio.CassetteNative
import com.tkleisas.multituner.audio.CassetteRecorder
import com.tkleisas.multituner.audio.MetronomeAudioEngine
import com.tkleisas.multituner.audio.OggOpusEncoder
import com.tkleisas.multituner.model.Cassette
import com.tkleisas.multituner.model.CassetteAudioTrack
import com.tkleisas.multituner.model.CassetteDeck
import com.tkleisas.multituner.model.CassetteExportFormat
import com.tkleisas.multituner.model.CassetteFlipper
import com.tkleisas.multituner.model.CassetteLibrary
import com.tkleisas.multituner.model.CassetteRecordSource
import com.tkleisas.multituner.model.CassetteMixdown
import com.tkleisas.multituner.model.CassettePreallocator
import com.tkleisas.multituner.model.CassetteStore
import com.tkleisas.multituner.model.CassetteTrackMerger
import com.tkleisas.multituner.model.DrumPattern
import com.tkleisas.multituner.model.DrumPatterns
import com.tkleisas.multituner.tuner.TapTempo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong

data class CassetteUiState(
    val library: CassetteLibrary,
    val playing: Boolean = false,
    val recording: Boolean = false,
    val winding: Boolean = false,
    val recordingTracks: Set<Int> = emptySet(),
    val armedTracks: Set<Int> = emptySet(),
    val positionSamples: Long = 0L,
    val varispeed: Float = 1.0f,
    val recordLevel: Float = 1.0f,
    val inputLevel: Float = 0.0f,
    val outputLevel: Float = 0.0f,
    val inputClipping: Boolean = false,
    val flipped: Boolean = false,
    val tapePreparing: Boolean = false,
    val flipping: Boolean = false,
    val tapeEnabled: Boolean = false,
    val tapeDrive: Float = 0.3f,
    val tapeTone: Float = 0.8f,
    val tapeWow: Float = 0.15f,
    val tapeFlutter: Float = 0.1f,
    val tapeHiss: Float = 0.05f,
    val tapeMix: Float = 1.0f,
    val reverbReturn: Float = 0.0f,
    val reverbSize: Float = 0.5f,
    val reverbDamping: Float = 0.35f,
    val delayReturn: Float = 0.0f,
    val delayTimeMs: Float = 350.0f,
    val delayFeedback: Float = 0.35f,
    val delayTone: Float = 0.7f,
    val exporting: Boolean = false,
    val exportProgress: Float = 0.0f,
    val exportUri: Uri? = null,
    val exportMime: String? = null,
    val exportError: String? = null,
) {
    val cassette: Cassette get() = library.selected
}

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
    val cassette: CassetteUiState,
    val sampleRate: Int = 0,
    val error: String? = null,
)

class PlaybackViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("multituner", 0)
    private val tapTempo = TapTempo()
    private val cassetteStore = CassetteStore(application)
    private val recorder = CassetteRecorder(application)
    private var cassetteLibrary = cassetteStore.load()
    private var engine: MetronomeAudioEngine? = null
    private var windJob: Job? = null

    private data class PendingTake(
        val deck: Int,
        val source: CassetteRecordSource,
        val finalFile: File,
        val tempFile: File,
    )

    private var recordStartSample = 0L
    private var recordingTakes: List<PendingTake> = emptyList()
    private var recordingMicDeck: Int? = null
    private var recordingInternalSources: Set<Int> = emptySet()
    private var recordingCassetteId: String? = null

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
            cassette = CassetteUiState(
                library = cassetteLibrary,
                varispeed = prefs.getFloat(KEY_VARISPEED, 1.0f).coerceIn(0.5f, 2.0f),
                recordLevel = prefs.getFloat(KEY_RECORD_LEVEL, 1.0f).coerceIn(0.0f, 2.0f),
                flipped = prefs.getBoolean(KEY_SIDE_B, false),
                tapeEnabled = prefs.getBoolean(KEY_TAPE_ENABLED, false),
                tapeDrive = prefs.getFloat(KEY_TAPE_DRIVE, 0.3f).coerceIn(0.0f, 1.0f),
                tapeTone = prefs.getFloat(KEY_TAPE_TONE, 0.8f).coerceIn(0.0f, 1.0f),
                tapeWow = prefs.getFloat(KEY_TAPE_WOW, 0.15f).coerceIn(0.0f, 1.0f),
                tapeFlutter = prefs.getFloat(KEY_TAPE_FLUTTER, 0.1f).coerceIn(0.0f, 1.0f),
                tapeHiss = prefs.getFloat(KEY_TAPE_HISS, 0.05f).coerceIn(0.0f, 1.0f),
                tapeMix = prefs.getFloat(KEY_TAPE_MIX, 1.0f).coerceIn(0.0f, 1.0f),
                reverbReturn = prefs.getFloat(KEY_REVERB_RETURN, 0.0f).coerceIn(0.0f, 1.0f),
                reverbSize = prefs.getFloat(KEY_REVERB_SIZE, 0.5f).coerceIn(0.0f, 1.0f),
                reverbDamping = prefs.getFloat(KEY_REVERB_DAMPING, 0.35f)
                    .coerceIn(0.0f, 1.0f),
                delayReturn = prefs.getFloat(KEY_DELAY_RETURN, 0.0f).coerceIn(0.0f, 1.0f),
                delayTimeMs = prefs.getFloat(KEY_DELAY_TIME, 350.0f).coerceIn(40.0f, 1200.0f),
                delayFeedback = prefs.getFloat(KEY_DELAY_FEEDBACK, 0.35f)
                    .coerceIn(0.0f, 0.9f),
                delayTone = prefs.getFloat(KEY_DELAY_TONE, 0.7f).coerceIn(0.0f, 1.0f),
            ),
        ),
    )
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    init {
        recorder.setInputGain(_uiState.value.cassette.recordLevel)
        viewModelScope.launch {
            while (isActive) {
                val state = _uiState.value
                val audio = engine
                val anyPlaying = state.metronomePlaying ||
                    state.drumsPlaying || state.cassette.playing ||
                    state.cassette.recording
                if (anyPlaying && audio != null) {
                    if (!audio.isReady ||
                        (!audio.isMetronomePlaying() && !audio.isDrumsPlaying() &&
                            !audio.isCassettePlaying())
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
                                cassette = it.cassette.copy(
                                    playing = false,
                                    recording = false,
                                    recordingTracks = emptySet(),
                                    positionSamples = 0,
                                ),
                                sampleRate = 0,
                                error = "Audio output stopped",
                            )
                        }
                    } else {
                        val cassetteActive = state.cassette.playing ||
                            state.cassette.recording
                        val position = audio.positionSamples()
                        val sampleRate = if (state.sampleRate > 0) {
                            state.sampleRate
                        } else {
                            audio.sampleRate()
                        }
                        val tapeEnd = sampleRate.toLong() * CASSETTE_SECONDS
                        val cassetteEnded = cassetteActive && sampleRate > 0 &&
                            position >= tapeEnd
                        if (cassetteEnded) {
                            if (state.cassette.recording) finishRecording()
                            audio.setCassettePlaying(false)
                            if (!state.metronomePlaying && !state.drumsPlaying) {
                                audio.setPositionSamples(0)
                            }
                        }
                        val resetPosition = cassetteEnded &&
                            !state.metronomePlaying && !state.drumsPlaying
                        _uiState.update {
                            val cassette = if (cassetteActive) {
                                it.cassette.copy(
                                    playing = if (cassetteEnded) {
                                        false
                                    } else {
                                        it.cassette.playing
                                    },
                                    positionSamples = if (resetPosition) {
                                        0
                                    } else {
                                        position
                                    },
                                )
                            } else {
                                it.cassette
                            }
                            it.copy(
                                currentBeat = audio.currentBeat(),
                                beatPhase = audio.beatPhase(),
                                currentDrumStep = audio.currentDrumStep(),
                                cassette = cassette,
                            )
                        }
                    }
                }
                if (state.cassette.recording && recordingComplete()) {
                    finishRecording()
                }
                updateMeters()
                delay(POLL_INTERVAL_MS)
            }
        }
        prepareSelectedTape()
    }

    private fun prepareSelectedTape() {
        if (_uiState.value.cassette.tapePreparing || _uiState.value.cassette.flipping) return
        val cassette = cassetteLibrary.selected
        _uiState.update {
            it.copy(cassette = it.cassette.copy(tapePreparing = true))
        }
        viewModelScope.launch {
            val prepared = withContext(Dispatchers.IO) {
                CassettePreallocator.prepare(cassette, cassetteStore)
            }
            if (prepared == null) {
                _uiState.update {
                    it.copy(
                        cassette = it.cassette.copy(tapePreparing = false),
                        error = "Could not prepare the tape files",
                    )
                }
                return@launch
            }

            val current = cassetteLibrary.cassettes
                .firstOrNull { it.id == cassette.id }
            if (current == null) {
                _uiState.update {
                    it.copy(cassette = it.cassette.copy(tapePreparing = false))
                }
                return@launch
            }
            val updated = current.copy(audioTracks = prepared.audioTracks)
            cassetteLibrary = cassetteStore.update(cassetteLibrary, updated)
            _uiState.update {
                it.copy(
                    cassette = it.cassette.copy(
                        library = cassetteLibrary,
                        tapePreparing = false,
                    ),
                    error = null,
                )
            }
            if (cassetteLibrary.selectedId == updated.id) {
                engine?.let { loadCassetteTracks(it, updated) }
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
        applyPerformanceSettings(audio, state)
        audio.setMetronomePlaying(true)
        if (!audio.isMetronomePlaying()) {
            _uiState.update { it.copy(error = "Could not start audio output") }
            return
        }

        val reset = !state.drumsPlaying && !state.cassette.playing
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
        applyPerformanceSettings(audio, state)
        audio.setDrumsPlaying(true)
        if (!audio.isDrumsPlaying()) {
            _uiState.update { it.copy(error = "Could not start audio output") }
            return
        }

        val reset = !state.metronomePlaying && !state.cassette.playing
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

    fun selectCassette(id: String) {
        val state = _uiState.value
        if (state.cassette.recording || state.cassette.tapePreparing || state.cassette.flipping) return
        if (state.cassette.playing) engine?.setCassettePlaying(false)
        cassetteLibrary = cassetteStore.select(cassetteLibrary, id)
        val next = state.cassette.copy(
            library = cassetteLibrary,
            playing = false,
            recording = false,
            recordingTracks = emptySet(),
            armedTracks = emptySet(),
            positionSamples = 0,
        )
        _uiState.update { it.copy(cassette = next) }
        engine?.let {
            applyCassetteParameters(it, next)
            loadCassetteTracks(it, cassetteLibrary.selected)
            applyDeckSettings(it, cassetteLibrary.selected)
        }
        prepareSelectedTape()
    }

    fun createCassette() {
        val state = _uiState.value
        if (state.cassette.recording || state.cassette.tapePreparing || state.cassette.flipping) return
        if (state.cassette.playing) engine?.setCassettePlaying(false)
        cassetteLibrary = cassetteStore.create(cassetteLibrary)
        val next = state.cassette.copy(
            library = cassetteLibrary,
            playing = false,
            recording = false,
            recordingTracks = emptySet(),
            armedTracks = emptySet(),
            positionSamples = 0,
        )
        _uiState.update { it.copy(cassette = next) }
        engine?.let {
            applyCassetteParameters(it, next)
            loadCassetteTracks(it, cassetteLibrary.selected)
            applyDeckSettings(it, cassetteLibrary.selected)
        }
        prepareSelectedTape()
    }

    fun renameCassette(id: String, name: String) {
        val trimmed = name.trim().take(40)
        if (trimmed.isEmpty()) return
        val cassette = cassetteLibrary.cassettes.firstOrNull { it.id == id } ?: return
        if (cassette.name == trimmed) return
        cassetteLibrary = cassetteStore.update(
            cassetteLibrary,
            cassette.copy(name = trimmed),
        )
        _uiState.update {
            it.copy(cassette = it.cassette.copy(library = cassetteLibrary))
        }
    }

    fun deleteCassette(id: String) {
        val state = _uiState.value
        if (state.cassette.recording || state.cassette.tapePreparing || state.cassette.flipping) return
        if (state.cassette.playing) engine?.setCassettePlaying(false)
        cassetteLibrary = cassetteStore.delete(cassetteLibrary, id)
        val next = state.cassette.copy(
            library = cassetteLibrary,
            playing = false,
            recording = false,
            recordingTracks = emptySet(),
            armedTracks = emptySet(),
            positionSamples = 0,
        )
        _uiState.update { it.copy(cassette = next) }
        engine?.let {
            applyCassetteParameters(it, next)
            loadCassetteTracks(it, cassetteLibrary.selected)
            applyDeckSettings(it, cassetteLibrary.selected)
        }
        prepareSelectedTape()
    }

    fun setCassetteColor(color: Long) {
        val state = _uiState.value
        val cassette = state.cassette.cassette.copy(color = color)
        cassetteLibrary = cassetteStore.update(cassetteLibrary, cassette)
        _uiState.update {
            it.copy(cassette = it.cassette.copy(library = cassetteLibrary))
        }
    }

    fun armTrack(index: Int) {
        val state = _uiState.value
        if (index !in 0 until Cassette.TRACKS || state.cassette.recording) {
            return
        }
        val armed = state.cassette.armedTracks
        val newArmed = if (index in armed) armed - index else armed + index
        _uiState.update {
            it.copy(cassette = it.cassette.copy(armedTracks = newArmed))
        }
    }

    fun setDeckGain(index: Int, gain: Float) {
        updateDeck(index) { it.copy(gain = gain.coerceIn(0.0f, 2.0f)) }
        engine?.setDeckGain(index, gain)
    }

    fun setDeckPan(index: Int, pan: Float) {
        updateDeck(index) { it.copy(pan = pan.coerceIn(-1.0f, 1.0f)) }
        engine?.setDeckPan(index, pan)
    }

    fun setDeckReverbSend(index: Int, send: Float) {
        val clamped = send.coerceIn(0.0f, 1.0f)
        updateDeck(index) { it.copy(reverbSend = clamped) }
        engine?.setDeckReverbSend(index, clamped)
    }

    fun setDeckDelaySend(index: Int, send: Float) {
        val clamped = send.coerceIn(0.0f, 1.0f)
        updateDeck(index) { it.copy(delaySend = clamped) }
        engine?.setDeckDelaySend(index, clamped)
    }

    fun setDeckBusSend(index: Int, send: Float) {
        val clamped = send.coerceIn(0.0f, 1.0f)
        updateDeck(index) { it.copy(busSend = clamped) }
        engine?.setDeckBusSend(index, clamped)
    }

    fun setDeckRecordSource(index: Int, source: CassetteRecordSource) {
        if (_uiState.value.cassette.recording) return
        updateDeck(index) { it.copy(recordSource = source) }
    }

    fun toggleDeckMute(index: Int) {
        val cassetteState = _uiState.value.cassette
        if (cassetteState.recording && index in cassetteState.recordingTracks) return
        val deck = cassetteState.cassette.decks.getOrNull(index) ?: return
        updateDeck(index) { it.copy(muted = !deck.muted) }
        engine?.setDeckMute(index, !deck.muted)
    }

    fun toggleDeckSolo(index: Int) {
        val deck = _uiState.value.cassette.cassette.decks.getOrNull(index) ?: return
        updateDeck(index) { it.copy(soloed = !deck.soloed) }
        engine?.setDeckSolo(index, !deck.soloed)
    }

    fun setVarispeed(value: Float) {
        val state = _uiState.value
        if (state.cassette.recording) return
        val clamped = value.coerceIn(0.5f, 2.0f)
        prefs.edit().putFloat(KEY_VARISPEED, clamped).apply()
        _uiState.update {
            it.copy(cassette = it.cassette.copy(varispeed = clamped))
        }
        engine?.setVarispeed(clamped)
    }

    fun toggleFlip() {
        val state = _uiState.value
        if (state.cassette.recording || state.cassette.tapePreparing ||
            state.cassette.flipping
        ) {
            return
        }
        if (state.cassette.winding) endWind()

        engine?.setCassettePlaying(false)
        val cassette = state.cassette.cassette
        val flippingToSideB = !state.cassette.flipped
        _uiState.update {
            it.copy(
                cassette = it.cassette.copy(
                    playing = false,
                    flipping = true,
                    winding = false,
                    positionSamples = 0,
                ),
                error = null,
            )
        }
        viewModelScope.launch {
            val transformed = withContext(Dispatchers.IO) {
                CassetteFlipper.flip(cassette)
            }
            if (transformed == null) {
                _uiState.update {
                    it.copy(
                        cassette = it.cassette.copy(flipping = false),
                        error = "Could not flip the cassette",
                    )
                }
                return@launch
            }

            val current = cassetteLibrary.cassettes
                .firstOrNull { it.id == cassette.id }
                ?: cassette
            val updated = current.copy(audioTracks = transformed.audioTracks)
            cassetteLibrary = cassetteStore.update(cassetteLibrary, updated)
            prefs.edit().putBoolean(KEY_SIDE_B, flippingToSideB).apply()
            val next = _uiState.value.cassette.copy(
                library = cassetteLibrary,
                flipped = flippingToSideB,
                playing = false,
                recording = false,
                recordingTracks = emptySet(),
                armedTracks = emptySet(),
                positionSamples = 0,
                winding = false,
                flipping = false,
            )
            _uiState.update {
                it.copy(cassette = next, error = null)
            }
            engine?.let {
                applyCassetteParameters(it, next)
                loadCassetteTracks(it, next.cassette)
                applyDeckSettings(it, next.cassette)
            }
        }
    }

    fun setTapeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TAPE_ENABLED, enabled).apply()
        _uiState.update {
            it.copy(cassette = it.cassette.copy(tapeEnabled = enabled))
        }
        engine?.setTapeEnabled(enabled)
    }

    fun setTapeDrive(value: Float) {
        val clamped = value.coerceIn(0.0f, 1.0f)
        prefs.edit().putFloat(KEY_TAPE_DRIVE, clamped).apply()
        _uiState.update {
            it.copy(cassette = it.cassette.copy(tapeDrive = clamped))
        }
        engine?.setTapeParameter(0, clamped)
    }

    fun setTapeTone(value: Float) {
        val clamped = value.coerceIn(0.0f, 1.0f)
        prefs.edit().putFloat(KEY_TAPE_TONE, clamped).apply()
        _uiState.update {
            it.copy(cassette = it.cassette.copy(tapeTone = clamped))
        }
        engine?.setTapeParameter(4, tapeToneHz(clamped))
    }

    fun setTapeWow(value: Float) {
        val clamped = value.coerceIn(0.0f, 1.0f)
        prefs.edit().putFloat(KEY_TAPE_WOW, clamped).apply()
        _uiState.update {
            it.copy(cassette = it.cassette.copy(tapeWow = clamped))
        }
        engine?.setTapeParameter(1, clamped)
    }

    fun setTapeFlutter(value: Float) {
        val clamped = value.coerceIn(0.0f, 1.0f)
        prefs.edit().putFloat(KEY_TAPE_FLUTTER, clamped).apply()
        _uiState.update {
            it.copy(cassette = it.cassette.copy(tapeFlutter = clamped))
        }
        engine?.setTapeParameter(2, clamped)
    }

    fun setTapeHiss(value: Float) {
        val clamped = value.coerceIn(0.0f, 1.0f)
        prefs.edit().putFloat(KEY_TAPE_HISS, clamped).apply()
        _uiState.update {
            it.copy(cassette = it.cassette.copy(tapeHiss = clamped))
        }
        engine?.setTapeParameter(3, clamped)
    }

    fun setTapeMix(value: Float) {
        val clamped = value.coerceIn(0.0f, 1.0f)
        prefs.edit().putFloat(KEY_TAPE_MIX, clamped).apply()
        _uiState.update {
            it.copy(cassette = it.cassette.copy(tapeMix = clamped))
        }
        engine?.setTapeParameter(5, clamped)
    }

    fun setReverbReturn(value: Float) {
        val clamped = value.coerceIn(0.0f, 1.0f)
        prefs.edit().putFloat(KEY_REVERB_RETURN, clamped).apply()
        _uiState.update {
            it.copy(cassette = it.cassette.copy(reverbReturn = clamped))
        }
        engine?.setReverbReturn(clamped)
    }

    fun setReverbSize(value: Float) {
        val clamped = value.coerceIn(0.0f, 1.0f)
        prefs.edit().putFloat(KEY_REVERB_SIZE, clamped).apply()
        _uiState.update {
            it.copy(cassette = it.cassette.copy(reverbSize = clamped))
        }
        engine?.setReverbSize(clamped)
    }

    fun setReverbDamping(value: Float) {
        val clamped = value.coerceIn(0.0f, 1.0f)
        prefs.edit().putFloat(KEY_REVERB_DAMPING, clamped).apply()
        _uiState.update {
            it.copy(cassette = it.cassette.copy(reverbDamping = clamped))
        }
        engine?.setReverbDamping(clamped)
    }

    fun setDelayReturn(value: Float) {
        val clamped = value.coerceIn(0.0f, 1.0f)
        prefs.edit().putFloat(KEY_DELAY_RETURN, clamped).apply()
        _uiState.update {
            it.copy(cassette = it.cassette.copy(delayReturn = clamped))
        }
        engine?.setDelayReturn(clamped)
    }

    fun setDelayTimeMs(value: Float) {
        val clamped = value.coerceIn(40.0f, 1200.0f)
        prefs.edit().putFloat(KEY_DELAY_TIME, clamped).apply()
        _uiState.update {
            it.copy(cassette = it.cassette.copy(delayTimeMs = clamped))
        }
        engine?.setDelayTimeMs(clamped)
    }

    fun setDelayFeedback(value: Float) {
        val clamped = value.coerceIn(0.0f, 0.9f)
        prefs.edit().putFloat(KEY_DELAY_FEEDBACK, clamped).apply()
        _uiState.update {
            it.copy(cassette = it.cassette.copy(delayFeedback = clamped))
        }
        engine?.setDelayFeedback(clamped)
    }

    fun setDelayTone(value: Float) {
        val clamped = value.coerceIn(0.0f, 1.0f)
        prefs.edit().putFloat(KEY_DELAY_TONE, clamped).apply()
        _uiState.update {
            it.copy(cassette = it.cassette.copy(delayTone = clamped))
        }
        engine?.setDelayTone(clamped)
    }

    fun setRecordLevel(value: Float) {
        val clamped = value.coerceIn(0.0f, 2.0f)
        prefs.edit().putFloat(KEY_RECORD_LEVEL, clamped).apply()
        recorder.setInputGain(clamped)
        _uiState.update {
            it.copy(cassette = it.cassette.copy(recordLevel = clamped))
        }
    }

    fun exportCassette(format: CassetteExportFormat) {
        val state = _uiState.value
        if (state.cassette.exporting || state.cassette.recording ||
            state.cassette.tapePreparing || state.cassette.flipping
        ) {
            return
        }
        if (format == CassetteExportFormat.OGG &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
                !OggOpusEncoder.isAvailable())
        ) {
            _uiState.update {
                it.copy(
                    cassette = it.cassette.copy(
                        exportError = "OGG Opus requires Android 11 or newer",
                    ),
                )
            }
            return
        }

        val cassette = state.cassette.cassette
        val totalFrames = CassetteMixdown.totalFrames(cassette)
        if (totalFrames <= 0L) {
            _uiState.update {
                it.copy(
                    cassette = it.cassette.copy(
                        exportError = "Record at least one take first",
                    ),
                )
            }
            return
        }

        val app = getApplication<Application>()
        val exportDir = File(app.cacheDir, "exports")
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val baseName = CassetteMixdown.safeName(cassette.name) + "-" + stamp
        val wavFile = File(exportDir, "$baseName.wav")
        val oggFile = File(exportDir, "$baseName.ogg")
        val paths = Array(Cassette.TRACKS) {
            cassette.audioTracks[it].filePath
        }
        val starts = LongArray(Cassette.TRACKS) {
            cassette.audioTracks[it].startSample
        }
        val gains = FloatArray(Cassette.TRACKS) {
            cassette.decks[it].gain
        }
        val pans = FloatArray(Cassette.TRACKS) {
            cassette.decks[it].pan
        }
        val muted = BooleanArray(Cassette.TRACKS) {
            cassette.decks[it].muted
        }
        val soloed = BooleanArray(Cassette.TRACKS) {
            cassette.decks[it].soloed
        }
        val reverbSends = FloatArray(Cassette.TRACKS) {
            cassette.decks[it].reverbSend
        }
        val delaySends = FloatArray(Cassette.TRACKS) {
            cassette.decks[it].delaySend
        }
        val audioState = state.cassette

        _uiState.update {
            it.copy(
                cassette = it.cassette.copy(
                    exporting = true,
                    exportProgress = 0.05f,
                    exportUri = null,
                    exportMime = null,
                    exportError = null,
                ),
            )
        }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    exportDir.mkdirs()
                    val cutoff = System.currentTimeMillis() - 6L * 60L * 60L * 1000L
                    exportDir.listFiles()?.forEach { old ->
                        if (old.isFile && old.lastModified() < cutoff) old.delete()
                    }

                    val rendered = CassetteNative.nativeRender(
                        paths = paths,
                        startSamples = starts,
                        gains = gains,
                        pans = pans,
                        muted = muted,
                        soloed = soloed,
                        reverbSends = reverbSends,
                        delaySends = delaySends,
                        totalFrames = totalFrames,
                        outputPath = wavFile.absolutePath,
                        reverbReturn = audioState.reverbReturn,
                        reverbSize = audioState.reverbSize,
                        reverbDamping = audioState.reverbDamping,
                        delayReturn = audioState.delayReturn,
                        delayTimeMs = audioState.delayTimeMs,
                        delayFeedback = audioState.delayFeedback,
                        delayTone = audioState.delayTone,
                        tapeEnabled = audioState.tapeEnabled,
                        tapeDrive = audioState.tapeDrive,
                        tapeTone = audioState.tapeTone,
                        tapeWow = audioState.tapeWow,
                        tapeFlutter = audioState.tapeFlutter,
                        tapeHiss = audioState.tapeHiss,
                        tapeMix = audioState.tapeMix,
                    )
                    check(rendered && wavFile.length() > 44L) {
                        "Could not render the cassette mix"
                    }
                    _uiState.update {
                        it.copy(
                            cassette = it.cassette.copy(exportProgress = 0.8f),
                        )
                    }
                    if (format == CassetteExportFormat.OGG) {
                        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            "OGG Opus requires Android 11 or newer"
                        }
                        val encoded = OggOpusEncoder.encode(wavFile, oggFile)
                        check(encoded && oggFile.length() > 0L) {
                            "OGG Opus encoding failed"
                        }
                        wavFile.delete()
                        oggFile
                    } else {
                        wavFile
                    }
                }.onFailure {
                    wavFile.delete()
                    oggFile.delete()
                }
            }

            result.fold(
                onSuccess = { file ->
                    val uri = FileProvider.getUriForFile(
                        app,
                        "${app.packageName}.fileprovider",
                        file,
                    )
                    _uiState.update {
                        it.copy(
                            cassette = it.cassette.copy(
                                exporting = false,
                                exportProgress = 1.0f,
                                exportUri = uri,
                                exportMime = format.mimeType,
                                exportError = null,
                            ),
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            cassette = it.cassette.copy(
                                exporting = false,
                                exportProgress = 0.0f,
                                exportError = error.message ?: "Export failed",
                            ),
                        )
                    }
                },
            )
        }
    }

    fun consumeExport() {
        _uiState.update {
            it.copy(
                cassette = it.cassette.copy(exportUri = null, exportMime = null),
            )
        }
    }

    fun clearExportError() {
        _uiState.update {
            it.copy(cassette = it.cassette.copy(exportError = null))
        }
    }

    fun beginWind(direction: Int) {
        if (direction == 0) return
        val state = _uiState.value
        if (state.cassette.recording || state.cassette.tapePreparing || state.cassette.flipping ||
            windJob != null
        ) {
            return
        }

        engine?.setCassetteVolume(0f)
        _uiState.update {
            it.copy(
                cassette = it.cassette.copy(
                    winding = true,
                    outputLevel = 0f,
                ),
            )
        }
        windCassette(direction * WIND_HOLD_STEP_SECONDS)
        windJob = viewModelScope.launch {
            while (isActive) {
                delay(WIND_HOLD_INTERVAL_MS)
                windCassette(direction * WIND_HOLD_STEP_SECONDS)
            }
        }
    }

    fun endWind() {
        windJob?.cancel()
        windJob = null
        engine?.setCassetteVolume(0.8f)
        _uiState.update {
            it.copy(cassette = it.cassette.copy(winding = false))
        }
    }

    fun windToStart() {
        if (_uiState.value.cassette.recording ||
            _uiState.value.cassette.tapePreparing || _uiState.value.cassette.flipping
        ) {
            return
        }
        seekCassetteTo(0L)
    }

    fun windToEnd() {
        val state = _uiState.value
        if (state.cassette.recording || state.cassette.tapePreparing || state.cassette.flipping) return
        val sampleRate = state.sampleRate.takeIf { it > 0 }
            ?: engine?.sampleRate()
            ?: 48000
        seekCassetteTo(sampleRate.toLong() * CASSETTE_SECONDS)
    }

    private fun seekCassetteTo(position: Long) {
        val state = _uiState.value
        if (state.cassette.recording || state.cassette.tapePreparing || state.cassette.flipping) return

        val audio = engine
        val sampleRate = state.sampleRate.takeIf { it > 0 }
            ?: audio?.sampleRate()
            ?: 48000
        val next = position.coerceIn(
            0L,
            sampleRate.toLong() * CASSETTE_SECONDS,
        )
        audio?.setPositionSamples(next)
        _uiState.update {
            it.copy(
                cassette = it.cassette.copy(positionSamples = next),
                sampleRate = if (it.sampleRate > 0) it.sampleRate else sampleRate,
            )
        }
    }

    private fun windCassette(seconds: Double) {
        val state = _uiState.value
        if (state.cassette.recording || state.cassette.tapePreparing || state.cassette.flipping) return

        val audio = engine
        val sampleRate = state.sampleRate.takeIf { it > 0 }
            ?: audio?.sampleRate()
            ?: 48000
        if (sampleRate <= 0) return

        val tapeEnd = sampleRate.toLong() * CASSETTE_SECONDS
        val varispeed = state.cassette.varispeed.coerceAtLeast(0.5f)
        val delta = (seconds * sampleRate / varispeed).roundToLong()
        val base = if (state.cassette.playing || state.cassette.recording) {
            audio?.positionSamples() ?: state.cassette.positionSamples
        } else {
            state.cassette.positionSamples
        }
        val next = (base + delta).coerceIn(0L, tapeEnd)
        if (next == base) return
        audio?.setPositionSamples(next)
        _uiState.update {
            it.copy(
                cassette = it.cassette.copy(positionSamples = next),
                sampleRate = if (it.sampleRate > 0) it.sampleRate else sampleRate,
            )
        }
    }

    fun toggleCassettePlay() {
        val state = _uiState.value
        if (state.cassette.tapePreparing || state.cassette.flipping) return
        if (state.cassette.recording) {
            finishRecording()
            engine?.setCassettePlaying(false)
            _uiState.update {
                it.copy(cassette = it.cassette.copy(playing = false))
            }
            return
        }
        if (state.cassette.playing) {
            val position = engine?.positionSamples()
                ?: state.cassette.positionSamples
            engine?.setCassettePlaying(false)
            _uiState.update {
                it.copy(
                    cassette = it.cassette.copy(
                        playing = false,
                        positionSamples = position,
                    ),
                )
            }
            return
        }

        val audio = ensureEngine() ?: return
        val sampleRate = audio.sampleRate()
        val tapeEnd = sampleRate.toLong() * CASSETTE_SECONDS
        val atEnd = tapeEnd > 0 && state.cassette.positionSamples >= tapeEnd
        val resumePosition = if (atEnd) {
            0L
        } else {
            state.cassette.positionSamples.coerceIn(0L, tapeEnd)
        }

        applyPerformanceSettings(audio, state)
        applyCassetteParameters(audio, state.cassette)
        audio.setCassettePlaying(true)
        if (!audio.isCassettePlaying()) {
            _uiState.update { it.copy(error = "Could not start cassette playback") }
            return
        }
        audio.setPositionSamples(resumePosition)
        _uiState.update {
            it.copy(
                cassette = it.cassette.copy(
                    playing = true,
                    positionSamples = resumePosition,
                ),
                sampleRate = sampleRate,
                error = null,
            )
        }
    }

    fun stopAll() {
        endWind()
        if (_uiState.value.cassette.recording) finishRecording()
        val state = _uiState.value
        val cassetteActive = state.cassette.playing || state.cassette.recording
        val position = if (cassetteActive) {
            engine?.positionSamples() ?: state.cassette.positionSamples
        } else {
            state.cassette.positionSamples
        }
        engine?.setMetronomePlaying(false)
        engine?.setDrumsPlaying(false)
        engine?.setCassettePlaying(false)
        _uiState.update {
            it.copy(
                metronomePlaying = false,
                drumsPlaying = false,
                currentBeat = 0,
                beatPhase = 0.0f,
                currentDrumStep = 0,
                cassette = it.cassette.copy(
                    playing = false,
                    winding = false,
                    positionSamples = position,
                ),
            )
        }
    }

    fun stopCassette() {
        val state = _uiState.value
        if (state.cassette.recording) finishRecording()
        val position = engine?.positionSamples()
            ?: _uiState.value.cassette.positionSamples
        engine?.setCassettePlaying(false)
        _uiState.update {
            it.copy(
                cassette = it.cassette.copy(
                    playing = false,
                    positionSamples = position,
                ),
            )
        }
    }

    fun toggleRecord() {
        val state = _uiState.value
        if (state.cassette.tapePreparing || state.cassette.flipping) return
        if (state.cassette.recording) {
            finishRecording()
            return
        }

        if (state.cassette.winding) endWind()

        val armed = state.cassette.armedTracks
        if (armed.isEmpty()) {
            _uiState.update {
                it.copy(error = "Arm at least one track before recording")
            }
            return
        }

        val cassette = state.cassette.cassette
        val takes = armed.sorted().map { deck ->
            val existingPath = cassette.audioTracks[deck].filePath
            val physicalTrack = Cassette.trackIndexForDeck(
                deck,
                state.cassette.flipped,
            )
            val finalFile = existingPath?.let(::File)
                ?: cassetteStore.trackFile(cassette, physicalTrack)
            PendingTake(
                deck = deck,
                source = cassette.decks[deck].recordSource,
                finalFile = finalFile,
                tempFile = File(finalFile.parentFile, finalFile.name + ".rec"),
            )
        }
        val micTakes = takes.filter { it.source == CassetteRecordSource.MIC }
        if (micTakes.size > 1) {
            _uiState.update {
                it.copy(error = "Only one track can record from the microphone")
            }
            return
        }
        val internalTakes = takes.filter { it.source != CassetteRecordSource.MIC }
        if (internalTakes.map { it.source }.distinct().size != internalTakes.size) {
            _uiState.update {
                it.copy(
                    error = "Each internal source can only be recorded to one track",
                )
            }
            return
        }

        val audio = ensureEngine() ?: return
        applyPerformanceSettings(audio, state)
        applyCassetteParameters(audio, state.cassette)

        val sampleRate = audio.sampleRate()
        val tapeEnd = sampleRate.toLong() * CASSETTE_SECONDS
        val tapePosition = if (state.cassette.playing) {
            audio.positionSamples()
        } else {
            state.cassette.positionSamples
        }
        val atEnd = tapeEnd > 0 && tapePosition >= tapeEnd
        val startPosition = if (atEnd) {
            0L
        } else {
            tapePosition.coerceIn(0L, tapeEnd)
        }

        if (state.cassette.varispeed != 1.0f) {
            prefs.edit().putFloat(KEY_VARISPEED, 1.0f).apply()
            _uiState.update {
                it.copy(cassette = it.cassette.copy(varispeed = 1.0f))
            }
            audio.setVarispeed(1.0f)
        }

        val previousMutes = takes.associate { take ->
            take.deck to cassette.decks[take.deck].muted
        }
        takes.forEach { audio.setDeckMute(it.deck, true) }

        fun abortStart(internal: List<Int>, micStarted: Boolean) {
            if (micStarted) recorder.stop()
            internal.forEach { audio.stopInternalRecording(it) }
            takes.forEach { it.tempFile.delete() }
            previousMutes.forEach { (deck, muted) ->
                audio.setDeckMute(deck, muted)
            }
        }

        var micStarted = false
        if (micTakes.isNotEmpty()) {
            recorder.setInputGain(state.cassette.recordLevel)
            if (!recorder.start(micTakes.first().tempFile)) {
                takes.forEach { it.tempFile.delete() }
                previousMutes.forEach { (deck, muted) ->
                    audio.setDeckMute(deck, muted)
                }
                _uiState.update {
                    it.copy(error = recorder.error ?: "Could not start recording")
                }
                return
            }
            micStarted = true
        }

        val startedInternal = mutableListOf<Int>()
        for (take in internalTakes) {
            if (!audio.startInternalRecording(
                    take.source.nativeCode,
                    take.tempFile.absolutePath,
                )
            ) {
                abortStart(startedInternal, micStarted)
                _uiState.update {
                    it.copy(error = "Could not start ${take.source.label} recording")
                }
                return
            }
            startedInternal += take.source.nativeCode
        }

        audio.setCassettePlaying(true)
        if (!audio.isCassettePlaying()) {
            abortStart(startedInternal, micStarted)
            _uiState.update {
                it.copy(error = "Could not start cassette playback")
            }
            return
        }
        audio.setPositionSamples(startPosition)

        recordingTakes = takes
        recordingMicDeck = micTakes.firstOrNull()?.deck
        recordingInternalSources = startedInternal.toSet()
        recordingCassetteId = cassette.id
        recordStartSample = startPosition

        _uiState.update {
            it.copy(
                cassette = it.cassette.copy(
                    playing = true,
                    recording = true,
                    recordingTracks = takes.map { take -> take.deck }.toSet(),
                    positionSamples = startPosition,
                    varispeed = 1.0f,
                ),
                sampleRate = audio.sampleRate(),
                error = null,
            )
        }
    }

    fun onAppBackground() {
        if (_uiState.value.cassette.recording) finishRecording()
        endWind()
        engine?.setMetronomePlaying(false)
        engine?.setDrumsPlaying(false)
        engine?.setCassettePlaying(false)
        engine?.close()
        engine = null
        _uiState.update {
            it.copy(
                metronomePlaying = false,
                drumsPlaying = false,
                currentBeat = 0,
                beatPhase = 0.0f,
                currentDrumStep = 0,
                cassette = it.cassette.copy(
                    playing = false,
                    recording = false,
                    winding = false,
                    recordingTracks = emptySet(),
                    positionSamples = 0,
                ),
                sampleRate = 0,
            )
        }
    }

    override fun onCleared() {
        windJob?.cancel()
        if (_uiState.value.cassette.recording) finishRecording()
        recorder.stop()
        engine?.close()
        engine = null
        super.onCleared()
    }

    private fun updateMeters() {
        val state = _uiState.value.cassette
        if (!state.recording && !state.playing &&
            state.inputLevel <= 0.001f && state.outputLevel <= 0.001f &&
            !state.inputClipping
        ) {
            return
        }

        val internalPeak = if (state.recording && recordingInternalSources.isNotEmpty()) {
            recordingInternalSources.maxOfOrNull { source ->
                engine?.internalPeakLevel(source) ?: 0.0f
            } ?: 0.0f
        } else {
            0.0f
        }
        val inputTarget = if (state.recording) {
            max(recorder.peakLevel, internalPeak)
        } else {
            0.0f
        }
        val outputTarget = if (state.playing) {
            engine?.cassettePeakLevel() ?: 0.0f
        } else {
            0.0f
        }
        val input = max(inputTarget, state.inputLevel * METER_DECAY)
            .let { if (it < 0.005f) 0.0f else it.coerceAtMost(2.0f) }
        val output = max(outputTarget, state.outputLevel * METER_DECAY)
            .let { if (it < 0.005f) 0.0f else it.coerceAtMost(2.0f) }
        val clipping = state.recording && recorder.clipping

        if (input != state.inputLevel || output != state.outputLevel ||
            clipping != state.inputClipping
        ) {
            _uiState.update {
                it.copy(
                    cassette = it.cassette.copy(
                        inputLevel = input,
                        outputLevel = output,
                        inputClipping = clipping,
                    ),
                )
            }
        }
    }

    private fun recordingComplete(): Boolean {
        val micDone = recordingMicDeck == null || !recorder.isRecording
        val internalDone = recordingInternalSources.none { source ->
            engine?.isInternalRecording(source) == true
        }
        return micDone && internalDone
    }

    private fun finishRecording() {
        if (!_uiState.value.cassette.recording) return

        val cassetteId = recordingCassetteId
        val takes = recordingTakes
        val micDeck = recordingMicDeck
        val internalSources = recordingInternalSources
        val startSample = recordStartSample

        internalSources.forEach { engine?.stopInternalRecording(it) }
        val internalFailed = internalSources.associateWith { source ->
            engine?.internalRecordingFailed(source) ?: false
        }
        if (micDeck != null) recorder.stop()
        val recordingError = if (micDeck != null) recorder.error else null

        recordingTakes = emptyList()
        recordingMicDeck = null
        recordingInternalSources = emptySet()
        recordingCassetteId = null
        recordStartSample = 0L

        val state = _uiState.value
        val currentCassette = state.cassette.library.cassettes
            .firstOrNull { it.id == cassetteId }
            ?: state.cassette.cassette

        if (takes.isEmpty()) {
            _uiState.update {
                it.copy(
                    cassette = it.cassette.copy(
                        recording = false,
                        recordingTracks = emptySet(),
                    ),
                    error = "Recording failed",
                )
            }
            return
        }

        var updatedCassette = currentCassette
        var savedCount = 0
        val errors = mutableListOf<String>()

        for (take in takes) {
            val sourceError = when {
                take.source == CassetteRecordSource.MIC -> recordingError
                internalFailed[take.source.nativeCode] == true ->
                    "${take.source.label} recording failed"
                else -> null
            }
            if (sourceError != null) {
                take.tempFile.delete()
                errors += sourceError
                continue
            }
            if (!take.tempFile.exists() || take.tempFile.length() <= 44L) {
                take.tempFile.delete()
                errors += "Track ${take.deck + 1} recording failed"
                continue
            }

            val existingTrack = currentCassette.audioTracks.getOrNull(take.deck)
            val merged = CassetteTrackMerger.overlay(
                existingFile = existingTrack?.filePath?.let(::File),
                existingStartSample = existingTrack?.startSample ?: 0L,
                takeFile = take.tempFile,
                takeStartSample = startSample,
                outputFile = take.finalFile,
            )
            take.tempFile.delete()
            if (!merged) {
                errors += "Could not save track ${take.deck + 1}"
                continue
            }

            updatedCassette = updatedCassette.withAudioTrack(
                take.deck,
                CassetteAudioTrack(
                    filePath = take.finalFile.absolutePath,
                    startSample = 0L,
                ),
            )
            savedCount++
            val loaded = engine?.loadCassetteTrack(
                take.deck,
                take.finalFile.absolutePath,
                0L,
            ) ?: true
            if (!loaded) {
                errors += "Track ${take.deck + 1} saved but could not be loaded"
            }
        }

        takes.forEach { take ->
            val deckState = currentCassette.decks.getOrNull(take.deck)
            if (deckState != null) {
                engine?.setDeckMute(take.deck, deckState.muted)
            }
        }

        if (savedCount > 0) {
            cassetteLibrary = cassetteStore.update(cassetteLibrary, updatedCassette)
        }

        _uiState.update {
            it.copy(
                cassette = it.cassette.copy(
                    library = cassetteLibrary,
                    recording = false,
                    recordingTracks = emptySet(),
                ),
                error = errors.joinToString("; ").ifBlank { null },
            )
        }
    }

    private fun updateDeck(index: Int, transform: (CassetteDeck) -> CassetteDeck) {
        val state = _uiState.value
        val deck = state.cassette.cassette.decks.getOrNull(index) ?: return
        val cassette = state.cassette.cassette.withDeck(index, transform(deck))
        cassetteLibrary = cassetteStore.update(cassetteLibrary, cassette)
        _uiState.update {
            it.copy(cassette = it.cassette.copy(library = cassetteLibrary))
        }
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
        val state = _uiState.value
        applyPerformanceSettings(created, state)
        applyCassetteParameters(created, state.cassette)
        loadCassetteTracks(created, state.cassette.cassette)
        applyDeckSettings(created, state.cassette.cassette)
        _uiState.update { it.copy(sampleRate = created.sampleRate()) }
        return created
    }

    private fun applyPerformanceSettings(audio: MetronomeAudioEngine, state: PlaybackUiState) {
        audio.setBpm(state.bpm.toDouble())
        audio.setBeatsPerBar(state.beatsPerBar)
        audio.setSubdivision(state.subdivision)
        audio.setVolume(state.volume)
        audio.setAccentEnabled(state.accent)
        audio.setDrumPattern(state.drumPattern.toNativeLevels())
        audio.setDrumSwing(state.swing)
        audio.setDrumVolume(state.drumVolume)
    }

    private fun applyCassetteParameters(audio: MetronomeAudioEngine, cassette: CassetteUiState) {
        audio.setVarispeed(cassette.varispeed)
        audio.setFlip(false)
        audio.setTapeEnabled(cassette.tapeEnabled)
        audio.setTapeParameter(0, cassette.tapeDrive)
        audio.setTapeParameter(1, cassette.tapeWow)
        audio.setTapeParameter(2, cassette.tapeFlutter)
        audio.setTapeParameter(3, cassette.tapeHiss)
        audio.setTapeParameter(4, tapeToneHz(cassette.tapeTone))
        audio.setTapeParameter(5, cassette.tapeMix)
        audio.setTapeParameter(6, 1.0f)
        audio.setReverbReturn(cassette.reverbReturn)
        audio.setReverbSize(cassette.reverbSize)
        audio.setReverbDamping(cassette.reverbDamping)
        audio.setDelayReturn(cassette.delayReturn)
        audio.setDelayTimeMs(cassette.delayTimeMs)
        audio.setDelayFeedback(cassette.delayFeedback)
        audio.setDelayTone(cassette.delayTone)
        audio.setCassetteVolume(0.8f)
    }

    private fun loadCassetteTracks(audio: MetronomeAudioEngine, cassette: Cassette) {
        audio.clearCassetteTracks()
        cassette.audioTracks.forEachIndexed { index, track ->
            val path = track.filePath ?: return@forEachIndexed
            if (File(path).exists()) {
                audio.loadCassetteTrack(index, path, track.startSample)
            }
        }
    }

    private fun applyDeckSettings(audio: MetronomeAudioEngine, cassette: Cassette) {
        cassette.decks.forEachIndexed { index, deck ->
            audio.setDeckGain(index, deck.gain)
            audio.setDeckPan(index, deck.pan)
            audio.setDeckMute(index, deck.muted)
            audio.setDeckSolo(index, deck.soloed)
            audio.setDeckReverbSend(index, deck.reverbSend)
            audio.setDeckDelaySend(index, deck.delaySend)
            audio.setDeckBusSend(index, deck.busSend)
        }
    }

    private fun tapeToneHz(normalized: Float): Float =
        (200.0 * 100.0.pow(normalized.toDouble())).toFloat()

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
        const val KEY_VARISPEED = "cassette_varispeed"
        const val KEY_RECORD_LEVEL = "cassette_record_level"
        const val KEY_SIDE_B = "cassette_side_b_buffers"
        const val KEY_TAPE_ENABLED = "cassette_tape_enabled"
        const val KEY_TAPE_DRIVE = "cassette_tape_drive"
        const val KEY_TAPE_TONE = "cassette_tape_tone"
        const val KEY_TAPE_WOW = "cassette_tape_wow"
        const val KEY_TAPE_FLUTTER = "cassette_tape_flutter"
        const val KEY_TAPE_HISS = "cassette_tape_hiss"
        const val KEY_TAPE_MIX = "cassette_tape_mix"
        const val KEY_REVERB_RETURN = "cassette_reverb_return"
        const val KEY_REVERB_SIZE = "cassette_reverb_size"
        const val KEY_REVERB_DAMPING = "cassette_reverb_damping"
        const val KEY_DELAY_RETURN = "cassette_delay_return"
        const val KEY_DELAY_TIME = "cassette_delay_time_ms"
        const val KEY_DELAY_FEEDBACK = "cassette_delay_feedback"
        const val KEY_DELAY_TONE = "cassette_delay_tone"

        const val MIN_BPM = 30
        const val MAX_BPM = 300
        const val POLL_INTERVAL_MS = 33L
        const val CASSETTE_SECONDS = Cassette.TAPE_SECONDS
        const val WIND_HOLD_STEP_SECONDS = 1.0
        const val WIND_HOLD_INTERVAL_MS = 50L
        const val METER_DECAY = 0.86f

        val SUPPORTED_BEATS = listOf(2, 3, 4, 6)
        val SUPPORTED_SUBDIVISIONS = listOf(1, 2, 4)
    }
}
