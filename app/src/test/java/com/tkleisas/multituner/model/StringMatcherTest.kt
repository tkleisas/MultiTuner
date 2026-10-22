package com.tkleisas.multituner.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StringMatcherTest {

    private val standard = TuningLibrary.byId("guitar_standard")

    @Test
    fun exactA2SelectsTheAString() {
        val match = StringMatcher.closest(110.0, standard, 440.0)
        assertNotNull(match)
        assertEquals(1, match!!.courseIndex)
        assertEquals(0, match.stringIndex)
        assertEquals(45, match.midi)
        assertEquals(0.0, match.cents, 1e-9)
    }

    @Test
    fun detunedNoteReportsCents() {
        val frequency = 110.0 * Math.pow(2.0, 10.0 / 1200.0)
        val match = StringMatcher.closest(frequency, standard, 440.0)
        assertNotNull(match)
        assertEquals(45, match!!.midi)
        assertEquals(10.0, match.cents, 1e-9)
    }

    @Test
    fun autoMatchRejectsNotesOutsideWindow() {
        val frequency = 110.0 * Math.pow(2.0, 100.0 / 1200.0)
        assertNull(StringMatcher.closest(frequency, standard, 440.0))
    }

    @Test
    fun explicitUnlimitedWindowReturnsNearestString() {
        val frequency = 110.0 * Math.pow(2.0, 100.0 / 1200.0)
        val match = StringMatcher.closest(
            frequency = frequency,
            tuning = standard,
            referenceA4 = 440.0,
            maxCents = null,
        )
        assertNotNull(match)
        assertEquals(45, match!!.midi)
        assertEquals(100.0, match.cents, 1e-9)
    }

    @Test
    fun lockedCourseIgnoresOtherStrings() {
        val match = StringMatcher.closest(
            frequency = 110.0,
            tuning = standard,
            referenceA4 = 440.0,
            lockedCourseIndex = 0,
            maxCents = null,
        )
        assertNotNull(match)
        assertEquals(0, match!!.courseIndex)
        assertEquals(40, match.midi)
        assertEquals(500.0, match.cents, 1e-9)
    }

    @Test
    fun invalidLockedCourseReturnsNull() {
        assertNull(
            StringMatcher.closest(
                frequency = 110.0,
                tuning = standard,
                referenceA4 = 440.0,
                lockedCourseIndex = 99,
            ),
        )
    }

    @Test
    fun zeroFrequencyReturnsNull() {
        assertNull(StringMatcher.closest(0.0, standard, 440.0))
        assertNull(StringMatcher.closest(-10.0, standard, 440.0))
    }

    @Test
    fun bouzoukiOctavePairSelectsTheRightString() {
        val cfad = TuningLibrary.byId("bouzouki_cfad")

        val low = StringMatcher.closest(130.8127826502993, cfad, 440.0)
        assertNotNull(low)
        assertEquals(0, low!!.courseIndex)
        assertEquals(0, low.stringIndex)
        assertEquals(48, low.midi)

        val high = StringMatcher.closest(261.6255653005986, cfad, 440.0)
        assertNotNull(high)
        assertEquals(0, high!!.courseIndex)
        assertEquals(1, high.stringIndex)
        assertEquals(60, high.midi)
    }

    @Test
    fun referencePitchChangesCents() {
        val match = StringMatcher.closest(110.0, standard, 432.0)
        assertNotNull(match)
        assertEquals(45, match!!.midi)
        assertEquals(31.766653633429414, match.cents, 1e-9)
    }

    @Test
    fun farChromaticNoteHasNoPresetMatch() {
        assertNull(StringMatcher.closest(500.0, standard, 440.0))
    }

    @Test
    fun matchCarriesCustomLabel() {
        val tuning = Tuning(
            id = "custom",
            name = "Custom",
            family = InstrumentFamily.GUITAR,
            courses = listOf(
                Course("1", listOf(TuningString(69, label = "A↑"))),
            ),
        )
        val match = StringMatcher.closest(440.0, tuning, 440.0)
        assertNotNull(match)
        assertEquals("A↑", match!!.label)
    }

    @Test
    fun centsOffsetIsRespected() {
        val tuning = Tuning(
            id = "custom",
            name = "Custom",
            family = InstrumentFamily.GUITAR,
            courses = listOf(
                Course("1", listOf(TuningString(69, centsOffset = 20))),
            ),
        )
        val frequency = 440.0 * Math.pow(2.0, 20.0 / 1200.0)
        val match = StringMatcher.closest(frequency, tuning, 440.0)
        assertNotNull(match)
        assertEquals(0.0, match!!.cents, 1e-9)
    }
}
