package com.tkleisas.multituner.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TuningLibraryTest {

    @Test
    fun presetIdsAreUnique() {
        val ids = TuningLibrary.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun presetsHaveCoursesAndStrings() {
        for (tuning in TuningLibrary.all) {
            assertTrue("${tuning.id} has no courses", tuning.courses.isNotEmpty())
            for (course in tuning.courses) {
                assertTrue("${tuning.id} has an empty course", course.strings.isNotEmpty())
                for (string in course.strings) {
                    assertTrue("${tuning.id} has an invalid MIDI note", string.midi in 0..127)
                }
            }
        }
    }

    @Test
    fun presetTargetFrequenciesAreInInstrumentRange() {
        for (tuning in TuningLibrary.all) {
            for (course in tuning.courses) {
                for (string in course.strings) {
                    val frequency = string.targetFrequency(440.0)
                    assertTrue(
                        "${tuning.id} target frequency out of range: $frequency",
                        frequency in 30.0..1000.0,
                    )
                }
            }
        }
    }

    @Test
    fun tambourasDefaultsToDgc() {
        val tambouras = TuningLibrary.forFamily(InstrumentFamily.TAMBOURAS)
        assertEquals("tambouras_dgc", tambouras.first().id)
        assertEquals(
            listOf(50, 55, 60),
            tambouras.first().courses.map { it.strings.first().midi },
        )
    }

    @Test
    fun guitarStandardTuningIsCorrect() {
        val standard = TuningLibrary.byId("guitar_standard")
        assertEquals(
            listOf(40, 45, 50, 55, 59, 64),
            standard.courses.map { it.strings.first().midi },
        )
    }

    @Test
    fun bouzoukiCfadHasOctavePairs() {
        val cfad = TuningLibrary.byId("bouzouki_cfad")
        assertEquals(listOf(48, 60), cfad.courses.first().strings.map { it.midi })
        assertEquals(4, cfad.courseCount)
    }

    @Test
    fun sazBozukLowAHasExpectedNotes() {
        val bozuk = TuningLibrary.byId("saz_bozuk_low")
        assertEquals(
            listOf(45, 50, 55),
            bozuk.courses.map { it.strings.first().midi },
        )
    }

    @Test
    fun familyPresetCountsAreStable() {
        assertEquals(12, TuningLibrary.forFamily(InstrumentFamily.GUITAR).size)
        assertEquals(4, TuningLibrary.forFamily(InstrumentFamily.BOUZOUKI).size)
        assertEquals(4, TuningLibrary.forFamily(InstrumentFamily.SAZ).size)
        assertEquals(3, TuningLibrary.forFamily(InstrumentFamily.TAMBOURAS).size)
    }

    @Test
    fun ebTuningUsesFlatNames() {
        assertTrue(TuningLibrary.byId("guitar_eb").preferFlats)
    }

    @Test
    fun unknownTuningFallsBackToFirstPreset() {
        assertEquals(TuningLibrary.all.first(), TuningLibrary.byId("missing"))
        assertEquals(TuningLibrary.all.first(), TuningLibrary.byId(null))
    }

    @Test
    fun presetsAreNotNull() {
        assertNotNull(TuningLibrary.byId("guitar_standard"))
    }
}
