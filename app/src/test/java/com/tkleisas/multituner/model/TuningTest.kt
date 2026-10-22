package com.tkleisas.multituner.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TuningTest {

    @Test
    fun targetFrequencyUsesReferencePitch() {
        assertEquals(440.0, TuningString(69).targetFrequency(440.0), 1e-9)
        assertEquals(432.0, TuningString(69).targetFrequency(432.0), 1e-9)
    }

    @Test
    fun targetFrequencyAppliesCentsOffset() {
        val offset = TuningString(69, centsOffset = 100)
        assertEquals(
            440.0 * Math.pow(2.0, 100.0 / 1200.0),
            offset.targetFrequency(440.0),
            1e-9,
        )

        val negative = TuningString(69, centsOffset = -100)
        assertEquals(
            440.0 * Math.pow(2.0, -100.0 / 1200.0),
            negative.targetFrequency(440.0),
            1e-9,
        )
    }

    @Test
    fun octavePairedCourseCombinesDistinctNotes() {
        val course = Course(
            label = "C",
            strings = listOf(TuningString(48), TuningString(60)),
        )
        assertEquals("C3·C4", course.noteLabel)
    }

    @Test
    fun unisonCourseShowsOneNote() {
        val course = Course(
            label = "A",
            strings = listOf(TuningString(57), TuningString(57)),
        )
        assertEquals("A3", course.noteLabel)
    }

    @Test
    fun displayLabelUsesOverrideOrNoteName() {
        assertEquals("A↑", TuningString(69, label = "A↑").displayLabel())
        assertEquals("A4", TuningString(69).displayLabel())
        assertEquals("Eb4", TuningString(63).displayLabel(preferFlats = true))
        assertEquals("D#4", TuningString(63).displayLabel(preferFlats = false))
    }

    @Test
    fun courseNoteLabelUsesCustomLabels() {
        val course = Course(
            label = "1",
            strings = listOf(TuningString(69, label = "A↑")),
        )
        assertEquals("A↑", course.noteLabel)
    }

    @Test
    fun tuningReportsCourseCount() {
        val tuning = Tuning(
            id = "test",
            name = "Test",
            family = InstrumentFamily.GUITAR,
            courses = listOf(
                Course("1", listOf(TuningString(40))),
                Course("2", listOf(TuningString(45))),
            ),
        )
        assertEquals(2, tuning.courseCount)
    }
}
