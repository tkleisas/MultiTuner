package com.tkleisas.multituner

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tkleisas.multituner.audio.AudioEngine
import com.tkleisas.multituner.audio.AudioStatus
import com.tkleisas.multituner.model.Course
import com.tkleisas.multituner.model.Tuning
import com.tkleisas.multituner.model.TuningLibrary
import com.tkleisas.multituner.tuner.DisplayUpdate
import com.tkleisas.multituner.tuner.TunerDisplayEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    private val displayEngine = TunerDisplayEngine()

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

    init {
        viewModelScope.launch {
            engine.frame.collect { frame ->
                if (frame != null) {
                    val state = _uiState.value
                    val update = displayEngine.onFrame(
                        frame = frame,
                        tuning = state.tuning,
                        referenceA4 = state.referenceA4,
                        lockedCourseIndex = state.lockedCourseIndex,
                    )
                    _uiState.update { it.withDisplay(update) }
                }
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
    }

    fun toggleCourseLock(index: Int) {
        val tuning = _uiState.value.tuning
        if (index !in tuning.courses.indices) return
        val newLock = if (_uiState.value.lockedCourseIndex == index) null else index
        prefs.edit().putInt(KEY_LOCKED_COURSE, newLock ?: -1).apply()
        _uiState.update { it.copy(lockedCourseIndex = newLock) }
        refreshDisplay()
    }

    fun clearCourseLock() {
        prefs.edit().putInt(KEY_LOCKED_COURSE, -1).apply()
        _uiState.update { it.copy(lockedCourseIndex = null) }
        refreshDisplay()
    }

    fun setReferencePitch(value: Double) {
        val clamped = value.coerceIn(415.0, 466.0)
        prefs.edit().putFloat(KEY_REFERENCE, clamped.toFloat()).apply()
        _uiState.update { it.copy(referenceA4 = clamped) }
        refreshDisplay()
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

    private fun refreshDisplay() {
        val state = _uiState.value
        val update = displayEngine.refresh(
            tuning = state.tuning,
            referenceA4 = state.referenceA4,
            lockedCourseIndex = state.lockedCourseIndex,
            nowMs = SystemClock.elapsedRealtime(),
        )
        _uiState.update { it.withDisplay(update) }
    }

    private fun resetAnalysis(clearLevel: Boolean) {
        val update = displayEngine.reset(clearLevel)
        _uiState.update { it.withDisplay(update) }
    }

    private companion object {
        const val KEY_TUNING = "tuning_id"
        const val KEY_REFERENCE = "reference_a4"
        const val KEY_LOCKED_COURSE = "locked_course"
    }
}

private fun TunerUiState.withDisplay(update: DisplayUpdate): TunerUiState = copy(
    hasPitch = update.hasPitch,
    inTune = update.inTune,
    displayMidi = update.displayMidi,
    displayNote = update.displayNote,
    frequency = update.frequency,
    cents = update.cents,
    confidence = update.confidence,
    level = update.level,
    activeCourseIndex = update.activeCourseIndex,
    activeStringIndex = update.activeStringIndex,
)
