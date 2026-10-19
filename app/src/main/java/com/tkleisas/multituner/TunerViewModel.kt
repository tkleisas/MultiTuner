package com.tkleisas.multituner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tkleisas.multituner.audio.AudioEngine
import com.tkleisas.multituner.audio.AudioStatus
import com.tkleisas.multituner.audio.PitchFrame
import com.tkleisas.multituner.model.Course
import com.tkleisas.multituner.model.NoteNames
import com.tkleisas.multituner.model.PitchMath
import com.tkleisas.multituner.model.Tuning
import com.tkleisas.multituner.model.TuningLibrary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToInt

data class TunerUiState(
    val tuning: Tuning,
    val referenceA4: Double = 440.0,
    val lockedCourseIndex: Int? = null,
    val activeCourseIndex: Int? = null,
    val activeStringIndex: Int? = null,
    val displayNote: String = "—",
    val displayMidi: Int? = null,
    val frequency: Double = 0.0,
    val cents: Double = 0.0,
    val confidence: Double = 0.0,
    val level: Double = 0.0,
    val inTune: Boolean = false,
    val hasPitch: Boolean = false,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val audioStatus: AudioStatus = AudioStatus.Idle,
) {
    val activeCourse: Course?
        get() = activeCourseIndex?.let { tuning.courses.getOrNull(it) }
}

class TunerViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("multituner", 0)
    private val engine = AudioEngine(application)

    private val initialTuning = TuningLibrary.byId(prefs.getString(KEY_TUNING, null))
    private val _uiState = MutableStateFlow(
        TunerUiState(
            tuning = initialTuning,
            referenceA4 = prefs.getFloat(KEY_REFERENCE, 440.0f).toDouble()
                .coerceIn(415.0, 466.0),
            lockedCourseIndex = prefs.getInt(KEY_LOCKED_COURSE, -1)
                .takeIf { it in initialTuning.courses.indices },
            isPaused = false,
        ),
    )
    val uiState: StateFlow<TunerUiState> = _uiState.asStateFlow()

    private var permissionGranted = false
    private var inForeground = false
    private var userPaused = false

    private var smoothedFrequency = 0.0
    private var smoothedConfidence = 0.0
    private var smoothedLevel = 0.0
    private var pitchActive = false
    private var inTuneSinceMs = 0L

    init {
        viewModelScope.launch {
            engine.frame.collect { frame ->
                if (frame != null) handleFrame(frame)
            }
        }
        viewModelScope.launch {
            engine.status.collect { status ->
                if (status !is AudioStatus.Running) {
                    resetAnalysis(clearLevel = true)
                }
                _uiState.update {
                    it.copy(
                        audioStatus = status,
                        isRunning = status is AudioStatus.Running,
                        isPaused = userPaused,
                    )
                }
            }
        }
    }

    fun onPermissionGranted() {
        permissionGranted = true
        startIfAllowed()
    }

    fun onPermissionDenied() {
        permissionGranted = false
        engine.stop()
        _uiState.update { it.copy(audioStatus = AudioStatus.Error("Microphone permission denied")) }
    }

    fun onAppForeground() {
        inForeground = true
        startIfAllowed()
    }

    fun onAppBackground() {
        inForeground = false
        engine.stop()
    }

    fun togglePause() {
        userPaused = !userPaused
        if (userPaused) {
            engine.stop()
        } else {
            startIfAllowed()
        }
        _uiState.update { it.copy(isPaused = userPaused) }
    }

    fun selectTuning(tuning: Tuning) {
        val locked = _uiState.value.lockedCourseIndex
            ?.takeIf { it in tuning.courses.indices }
        prefs.edit()
            .putString(KEY_TUNING, tuning.id)
            .putInt(KEY_LOCKED_COURSE, locked ?: -1)
            .apply()
        _uiState.update {
            it.copy(
                tuning = tuning,
                lockedCourseIndex = locked,
                activeCourseIndex = null,
                activeStringIndex = null,
            )
        }
        resetAnalysis(clearLevel = false)
        updateDisplay()
    }

    fun toggleCourseLock(index: Int) {
        val tuning = _uiState.value.tuning
        if (index !in tuning.courses.indices) return
        val newLock = if (_uiState.value.lockedCourseIndex == index) null else index
        prefs.edit().putInt(KEY_LOCKED_COURSE, newLock ?: -1).apply()
        _uiState.update { it.copy(lockedCourseIndex = newLock) }
        updateDisplay()
    }

    fun clearCourseLock() {
        prefs.edit().putInt(KEY_LOCKED_COURSE, -1).apply()
        _uiState.update { it.copy(lockedCourseIndex = null) }
        updateDisplay()
    }

    fun setReferencePitch(value: Double) {
        val clamped = value.coerceIn(415.0, 466.0)
        prefs.edit().putFloat(KEY_REFERENCE, clamped.toFloat()).apply()
        _uiState.update { it.copy(referenceA4 = clamped) }
        updateDisplay()
    }

    override fun onCleared() {
        engine.stop()
        super.onCleared()
    }

    private fun startIfAllowed() {
        if (inForeground && permissionGranted && !userPaused) {
            engine.start()
        }
    }

    private fun handleFrame(frame: PitchFrame) {
        smoothedLevel = smooth(smoothedLevel, rmsToLevel(frame.rms), 0.5)

        if (!frame.hasPitch || frame.confidence < MIN_CONFIDENCE || frame.rms < MIN_RMS) {
            resetAnalysis(clearLevel = false)
            _uiState.update { it.copy(level = smoothedLevel) }
            return
        }

        if (!pitchActive || smoothedFrequency <= 0.0) {
            smoothedFrequency = frame.frequency
            smoothedConfidence = frame.confidence
            pitchActive = true
        } else {
            val jumpCents = abs(PitchMath.centsBetween(frame.frequency, smoothedFrequency))
            val alpha = if (jumpCents > 90.0) 0.9 else 0.32
            smoothedFrequency = smooth(smoothedFrequency, frame.frequency, alpha)
            smoothedConfidence = smooth(smoothedConfidence, frame.confidence, 0.35)
        }

        updateDisplay(frame.timestampMs)
    }

    private fun updateDisplay(nowMs: Long = android.os.SystemClock.elapsedRealtime()) {
        val state = _uiState.value
        if (!pitchActive || smoothedFrequency <= 0.0) {
            _uiState.update {
                it.copy(
                    hasPitch = false,
                    inTune = false,
                    displayNote = "—",
                    displayMidi = null,
                    frequency = 0.0,
                    cents = 0.0,
                    confidence = 0.0,
                    activeCourseIndex = null,
                    activeStringIndex = null,
                    level = smoothedLevel,
                )
            }
            inTuneSinceMs = 0L
            return
        }

        val reference = state.referenceA4
        val match = findClosestString(
            frequency = smoothedFrequency,
            tuning = state.tuning,
            referenceA4 = reference,
            lockedCourseIndex = state.lockedCourseIndex,
        )

        val matched = if (state.lockedCourseIndex != null) {
            match
        } else {
            match?.takeIf { abs(it.cents) <= COURSE_MATCH_WINDOW }
        }
        val exactMidi = PitchMath.frequencyToMidi(smoothedFrequency, reference)
        val detectedMidi = exactMidi.roundToInt()
        val displayMidi = matched?.midi ?: detectedMidi
        val cents = matched?.cents ?: ((exactMidi - detectedMidi) * 100.0)

        val inTuneNow = matched != null && abs(cents) <= IN_TUNE_CENTS &&
            smoothedConfidence >= IN_TUNE_CONFIDENCE
        if (inTuneNow) {
            if (inTuneSinceMs == 0L) inTuneSinceMs = nowMs
        } else {
            inTuneSinceMs = 0L
        }
        val inTune = inTuneNow && (nowMs - inTuneSinceMs) >= IN_TUNE_HOLD_MS

        _uiState.update {
            it.copy(
                hasPitch = true,
                inTune = inTune,
                displayMidi = displayMidi,
                displayNote = NoteNames.label(displayMidi, state.tuning.preferFlats),
                frequency = smoothedFrequency,
                cents = cents,
                confidence = smoothedConfidence,
                level = smoothedLevel,
                activeCourseIndex = matched?.courseIndex,
                activeStringIndex = matched?.stringIndex,
            )
        }
    }

    private fun findClosestString(
        frequency: Double,
        tuning: Tuning,
        referenceA4: Double,
        lockedCourseIndex: Int?,
    ): StringMatch? {
        var best: StringMatch? = null
        val indices = lockedCourseIndex?.let { listOf(it) } ?: tuning.courses.indices
        for (courseIndex in indices) {
            val course = tuning.courses.getOrNull(courseIndex) ?: continue
            course.strings.forEachIndexed { stringIndex, string ->
                val target = string.targetFrequency(referenceA4)
                if (target <= 0.0) return@forEachIndexed
                val cents = PitchMath.centsBetween(frequency, target)
                val current = best
                if (current == null || abs(cents) < abs(current.cents)) {
                    best = StringMatch(
                        courseIndex = courseIndex,
                        stringIndex = stringIndex,
                        midi = string.midi,
                        targetFrequency = target,
                        cents = cents,
                    )
                }
            }
        }
        return best
    }

    private fun resetAnalysis(clearLevel: Boolean) {
        smoothedFrequency = 0.0
        smoothedConfidence = 0.0
        pitchActive = false
        inTuneSinceMs = 0L
        if (clearLevel) smoothedLevel = 0.0
        _uiState.update {
            it.copy(
                hasPitch = false,
                inTune = false,
                displayNote = "—",
                displayMidi = null,
                frequency = 0.0,
                cents = 0.0,
                confidence = 0.0,
                activeCourseIndex = null,
                activeStringIndex = null,
                level = smoothedLevel,
            )
        }
    }

    private fun smooth(current: Double, target: Double, alpha: Double): Double =
        current + alpha * (target - current)

    private fun rmsToLevel(rms: Double): Double {
        if (rms <= 0.0) return 0.0
        val db = 20.0 * log10(rms)
        return ((db + 60.0) / 60.0).coerceIn(0.0, 1.0)
    }

    private data class StringMatch(
        val courseIndex: Int,
        val stringIndex: Int,
        val midi: Int,
        val targetFrequency: Double,
        val cents: Double,
    )

    private companion object {
        const val KEY_TUNING = "tuning_id"
        const val KEY_REFERENCE = "reference_a4"
        const val KEY_LOCKED_COURSE = "locked_course"

        const val MIN_CONFIDENCE = 0.3
        const val MIN_RMS = 0.0035
        const val COURSE_MATCH_WINDOW = 90.0
        const val IN_TUNE_CENTS = 4.5
        const val IN_TUNE_CONFIDENCE = 0.4
        const val IN_TUNE_HOLD_MS = 180L
    }
}
