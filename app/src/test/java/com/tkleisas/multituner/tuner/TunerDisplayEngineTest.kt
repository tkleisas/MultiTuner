package com.tkleisas.multituner.tuner

import com.tkleisas.multituner.audio.PitchFrame
import com.tkleisas.multituner.model.PitchMath
import com.tkleisas.multituner.model.TuningLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TunerDisplayEngineTest {

    private val standard = TuningLibrary.byId("guitar_standard")
    private val bouzouki = TuningLibrary.byId("bouzouki_cfad")

    private fun frame(
        frequency: Double,
        confidence: Double = 1.0,
        rms: Double = 0.05,
        hasPitch: Boolean = true,
        timestampMs: Long = 1000L,
    ) = PitchFrame(frequency, confidence, rms, hasPitch, timestampMs)

    @Test
    fun exactA2SelectsTheAString() {
        val update = TunerDisplayEngine().onFrame(frame(110.0), standard, 440.0, null)

        assertTrue(update.hasPitch)
        assertEquals("A2", update.displayNote)
        assertEquals(45, update.displayMidi ?: -1)
        assertEquals(1, update.activeCourseIndex ?: -1)
        assertEquals(0, update.activeStringIndex ?: -1)
        assertEquals(0.0, update.cents, 1e-9)
        assertFalse(update.inTune)
        assertTrue(update.level > 0.0)
    }

    @Test
    fun inTuneRequiresTheHoldPeriod() {
        val engine = TunerDisplayEngine()
        val first = engine.onFrame(frame(110.0, timestampMs = 1000L), standard, 440.0, null)
        val second = engine.onFrame(frame(110.0, timestampMs = 1200L), standard, 440.0, null)

        assertFalse(first.inTune)
        assertTrue(second.inTune)
    }

    @Test
    fun detunedNoteIsNotInTune() {
        val detuned = 110.0 * Math.pow(2.0, 10.0 / 1200.0)
        val engine = TunerDisplayEngine()
        engine.onFrame(frame(detuned, timestampMs = 1000L), standard, 440.0, null)
        val update = engine.onFrame(frame(detuned, timestampMs = 1200L), standard, 440.0, null)

        assertEquals(10.0, update.cents, 1e-9)
        assertFalse(update.inTune)
    }

    @Test
    fun lowConfidenceClearsPitch() {
        val update = TunerDisplayEngine().onFrame(
            frame(110.0, confidence = 0.2),
            standard,
            440.0,
            null,
        )

        assertFalse(update.hasPitch)
        assertEquals("—", update.displayNote)
        assertNull(update.displayMidi)
        assertNull(update.activeCourseIndex)
        assertNull(update.activeStringIndex)
    }

    @Test
    fun lowRmsClearsPitch() {
        val update = TunerDisplayEngine().onFrame(
            frame(110.0, rms = 0.001),
            standard,
            440.0,
            null,
        )

        assertFalse(update.hasPitch)
    }

    @Test
    fun noPitchFrameClearsPitchButKeepsLevel() {
        val engine = TunerDisplayEngine()
        val active = engine.onFrame(frame(110.0), standard, 440.0, null)
        val cleared = engine.onFrame(
            frame(110.0, hasPitch = false, rms = 0.0, timestampMs = 1200L),
            standard,
            440.0,
            null,
        )

        assertTrue(active.level > 0.0)
        assertFalse(cleared.hasPitch)
        assertTrue(cleared.level > 0.0)
    }

    @Test
    fun lockedCourseUsesItsTargetEvenWhenFarAway() {
        val update = TunerDisplayEngine().onFrame(
            frame(110.0),
            standard,
            440.0,
            lockedCourseIndex = 0,
        )

        assertTrue(update.hasPitch)
        assertEquals("E2", update.displayNote)
        assertEquals(40, update.displayMidi ?: -1)
        assertEquals(500.0, update.cents, 1e-9)
        assertEquals(0, update.activeCourseIndex ?: -1)
        assertEquals(0, update.activeStringIndex ?: -1)
        assertFalse(update.inTune)
    }

    @Test
    fun autoModeFallsBackToChromaticWhenNoStringIsClose() {
        val update = TunerDisplayEngine().onFrame(frame(500.0), standard, 440.0, null)
        val exactMidi = PitchMath.frequencyToMidi(500.0, 440.0)
        val detectedMidi = Math.round(exactMidi).toInt()

        assertTrue(update.hasPitch)
        assertNull(update.activeCourseIndex)
        assertNull(update.activeStringIndex)
        assertEquals(detectedMidi, update.displayMidi ?: -1)
        assertEquals((exactMidi - detectedMidi) * 100.0, update.cents, 1e-9)
    }

    @Test
    fun bouzoukiOctavePairSelectsThePlayedString() {
        val engine = TunerDisplayEngine()

        val low = engine.onFrame(
            frame(130.8127826502993),
            bouzouki,
            440.0,
            null,
        )
        assertEquals(0, low.activeCourseIndex ?: -1)
        assertEquals(0, low.activeStringIndex ?: -1)
        assertEquals(48, low.displayMidi ?: -1)

        val highEngine = TunerDisplayEngine()
        val high = highEngine.onFrame(
            frame(261.6255653005986),
            bouzouki,
            440.0,
            null,
        )
        assertEquals(0, high.activeCourseIndex ?: -1)
        assertEquals(1, high.activeStringIndex ?: -1)
        assertEquals(60, high.displayMidi ?: -1)
    }

    @Test
    fun referencePitchChangesTheCentsReading() {
        val update = TunerDisplayEngine().onFrame(
            frame(110.0),
            standard,
            432.0,
            null,
        )

        assertEquals(45, update.displayMidi ?: -1)
        assertEquals(31.766653633429414, update.cents, 1e-9)
    }

    @Test
    fun refreshRecomputesTheCurrentPitch() {
        val engine = TunerDisplayEngine()
        engine.onFrame(frame(110.0, timestampMs = 1000L), standard, 440.0, null)

        val early = engine.refresh(standard, 440.0, null, nowMs = 1100L)
        val held = engine.refresh(standard, 440.0, null, nowMs = 1200L)

        assertTrue(early.hasPitch)
        assertFalse(early.inTune)
        assertTrue(held.inTune)
    }

    @Test
    fun refreshChangesTargetWhenLockChanges() {
        val engine = TunerDisplayEngine()
        engine.onFrame(frame(110.0), standard, 440.0, null)
        val locked = engine.refresh(standard, 440.0, lockedCourseIndex = 0, nowMs = 1200L)

        assertEquals(40, locked.displayMidi ?: -1)
        assertEquals(500.0, locked.cents, 1e-9)
        assertEquals(0, locked.activeCourseIndex ?: -1)
    }

    @Test
    fun resetClearsPitchAndLevel() {
        val engine = TunerDisplayEngine()
        engine.onFrame(frame(110.0), standard, 440.0, null)
        val update = engine.reset(clearLevel = true)

        assertFalse(update.hasPitch)
        assertEquals("—", update.displayNote)
        assertEquals(0.0, update.level, 1e-9)
        assertNull(update.displayMidi)
    }

    @Test
    fun resetCanKeepInputLevel() {
        val engine = TunerDisplayEngine()
        val active = engine.onFrame(frame(110.0), standard, 440.0, null)
        val update = engine.reset(clearLevel = false)

        assertFalse(update.hasPitch)
        assertTrue(update.level > 0.0)
        assertEquals(active.level, update.level, 1e-9)
    }

    @Test
    fun inTuneRequiresEnoughConfidence() {
        val engine = TunerDisplayEngine()
        engine.onFrame(frame(110.0, confidence = 0.3, timestampMs = 1000L), standard, 440.0, null)
        val update = engine.onFrame(frame(110.0, confidence = 0.3, timestampMs = 1200L), standard, 440.0, null)

        assertFalse(update.inTune)
    }
}
