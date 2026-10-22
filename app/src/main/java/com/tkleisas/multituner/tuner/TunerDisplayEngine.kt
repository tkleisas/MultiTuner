package com.tkleisas.multituner.tuner

import com.tkleisas.multituner.audio.PitchFrame
import com.tkleisas.multituner.model.NoteNames
import com.tkleisas.multituner.model.PitchMath
import com.tkleisas.multituner.model.StringMatcher
import com.tkleisas.multituner.model.Tuning
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToInt

class TunerDisplayEngine(
    private val minConfidence: Double = 0.3,
    private val minRms: Double = 0.0035,
    private val inTuneCents: Double = 4.5,
    private val inTuneConfidence: Double = 0.4,
    private val inTuneHoldMs: Long = 180L,
    private val noteJumpCents: Double = 90.0,
    private val frequencyAlpha: Double = 0.32,
    private val confidenceAlpha: Double = 0.35,
    private val levelAlpha: Double = 0.5,
) {
    private var smoothedFrequency = 0.0
    private var smoothedConfidence = 0.0
    private var smoothedLevel = 0.0
    private var pitchActive = false
    private var inTuneSinceMs = 0L

    fun onFrame(
        frame: PitchFrame,
        tuning: Tuning,
        referenceA4: Double,
        lockedCourseIndex: Int?,
    ): DisplayUpdate {
        smoothedLevel = smooth(smoothedLevel, rmsToLevel(frame.rms), levelAlpha)

        if (!frame.hasPitch || frame.confidence < minConfidence || frame.rms < minRms) {
            return clearPitch()
        }

        if (!pitchActive || smoothedFrequency <= 0.0) {
            smoothedFrequency = frame.frequency
            smoothedConfidence = frame.confidence
            pitchActive = true
        } else {
            val jumpCents = abs(PitchMath.centsBetween(frame.frequency, smoothedFrequency))
            val alpha = if (jumpCents > noteJumpCents) 0.9 else frequencyAlpha
            smoothedFrequency = smooth(smoothedFrequency, frame.frequency, alpha)
            smoothedConfidence = smooth(smoothedConfidence, frame.confidence, confidenceAlpha)
        }

        return computeDisplay(
            tuning = tuning,
            referenceA4 = referenceA4,
            lockedCourseIndex = lockedCourseIndex,
            nowMs = frame.timestampMs,
        )
    }

    fun refresh(
        tuning: Tuning,
        referenceA4: Double,
        lockedCourseIndex: Int?,
        nowMs: Long,
    ): DisplayUpdate {
        if (!pitchActive || smoothedFrequency <= 0.0) return idle()
        return computeDisplay(tuning, referenceA4, lockedCourseIndex, nowMs)
    }

    fun reset(clearLevel: Boolean = true): DisplayUpdate {
        smoothedFrequency = 0.0
        smoothedConfidence = 0.0
        pitchActive = false
        inTuneSinceMs = 0L
        if (clearLevel) smoothedLevel = 0.0
        return idle()
    }

    fun idle(): DisplayUpdate = DisplayUpdate(
        hasPitch = false,
        inTune = false,
        displayMidi = null,
        displayNote = "—",
        frequency = 0.0,
        cents = 0.0,
        confidence = 0.0,
        level = smoothedLevel,
        activeCourseIndex = null,
        activeStringIndex = null,
    )

    private fun clearPitch(): DisplayUpdate {
        smoothedFrequency = 0.0
        smoothedConfidence = 0.0
        pitchActive = false
        inTuneSinceMs = 0L
        return idle()
    }

    private fun computeDisplay(
        tuning: Tuning,
        referenceA4: Double,
        lockedCourseIndex: Int?,
        nowMs: Long,
    ): DisplayUpdate {
        val matched = StringMatcher.closest(
            frequency = smoothedFrequency,
            tuning = tuning,
            referenceA4 = referenceA4,
            lockedCourseIndex = lockedCourseIndex,
            maxCents = if (lockedCourseIndex != null) null else StringMatcher.COURSE_MATCH_WINDOW,
        )

        val exactMidi = PitchMath.frequencyToMidi(smoothedFrequency, referenceA4)
        val detectedMidi = exactMidi.roundToInt()
        val displayMidi = matched?.midi ?: detectedMidi
        val cents = matched?.cents ?: ((exactMidi - detectedMidi) * 100.0)

        val inTuneNow = matched != null &&
            abs(cents) <= inTuneCents &&
            smoothedConfidence >= inTuneConfidence

        if (inTuneNow) {
            if (inTuneSinceMs == 0L) inTuneSinceMs = nowMs
        } else {
            inTuneSinceMs = 0L
        }
        val inTune = inTuneNow && (nowMs - inTuneSinceMs) >= inTuneHoldMs

        return DisplayUpdate(
            hasPitch = true,
            inTune = inTune,
            displayMidi = displayMidi,
            displayNote = NoteNames.label(displayMidi, tuning.preferFlats),
            frequency = smoothedFrequency,
            cents = cents,
            confidence = smoothedConfidence,
            level = smoothedLevel,
            activeCourseIndex = matched?.courseIndex,
            activeStringIndex = matched?.stringIndex,
        )
    }

    private fun smooth(current: Double, target: Double, alpha: Double): Double =
        current + alpha * (target - current)

    private fun rmsToLevel(rms: Double): Double {
        if (rms <= 0.0) return 0.0
        val db = 20.0 * log10(rms)
        return ((db + 60.0) / 60.0).coerceIn(0.0, 1.0)
    }
}
