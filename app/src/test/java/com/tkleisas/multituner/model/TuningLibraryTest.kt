package com.tkleisas.multituner.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

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
        assertEquals(4, TuningLibrary.forFamily(InstrumentFamily.OUD).size)
        assertEquals(2, TuningLibrary.forFamily(InstrumentFamily.LAOUTO).size)
        assertEquals(3, TuningLibrary.forFamily(InstrumentFamily.LUTE).size)
    }

    @Test
    fun catalogContainsAllPresets() {
        assertEquals(32, TuningLibrary.all.size)
    }

    @Test
    fun oudTuningsHaveExpectedNotes() {
        assertEquals(
            listOf(36, 41, 45, 50, 55, 60),
            TuningLibrary.byId("oud_arabic_modern").courses.map { it.strings.first().midi },
        )
        assertEquals(
            listOf(37, 42, 47, 52, 57, 62),
            TuningLibrary.byId("oud_turkish_bolahenk").courses.map { it.strings.first().midi },
        )
    }

    @Test
    fun laoutoTuningsHaveOctavePairs() {
        val mainland = TuningLibrary.byId("laouto_mainland")
        assertEquals(
            listOf(listOf(48, 60), listOf(55, 67), listOf(50, 62), listOf(69)),
            mainland.courses.map { it.strings.map { string -> string.midi } },
        )

        val cretan = TuningLibrary.byId("laouto_cretan")
        assertEquals(
            listOf(listOf(43, 55), listOf(38, 50), listOf(45, 57), listOf(52)),
            cretan.courses.map { it.strings.map { string -> string.midi } },
        )
    }

    @Test
    fun luteTuningsHaveExpectedCourses() {
        val six = TuningLibrary.byId("lute_renaissance_6")
        assertEquals(6, six.courseCount)
        assertEquals(listOf(43, 55), six.courses.first().strings.map { it.midi })
        assertEquals(listOf(67), six.courses.last().strings.map { it.midi })

        val eight = TuningLibrary.byId("lute_renaissance_8")
        assertEquals(8, eight.courseCount)
        assertEquals(listOf(38, 50), eight.courses.first().strings.map { it.midi })

        val ten = TuningLibrary.byId("lute_renaissance_10")
        assertEquals(10, ten.courseCount)
        assertEquals(listOf(36, 48), ten.courses.first().strings.map { it.midi })
        assertTrue(ten.preferFlats)
    }

    @Test
    fun parserSupportsMicrotonalStrings() {
        val json = """
            {
              "version": 1,
              "tunings": [
                {
                  "id": "micro",
                  "name": "Micro",
                  "family": "OUD",
                  "courses": [
                    {
                      "label": "1",
                      "strings": [
                        {"midi": 69, "centsOffset": 50, "label": "A↑"}
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val parsed = TuningLibrary.parse(ByteArrayInputStream(json.toByteArray()))
        val string = parsed.single().courses.single().strings.single()
        assertEquals(50, string.centsOffset)
        assertEquals("A↑", string.label)
        assertEquals(
            440.0 * Math.pow(2.0, 50.0 / 1200.0),
            string.targetFrequency(440.0),
            1e-9,
        )
    }

    @Test
    fun parserRejectsDuplicateIds() {
        val json = """
            {
              "version": 1,
              "tunings": [
                {"id": "same", "name": "One", "family": "GUITAR", "courses": [{"label": "1", "strings": [{"midi": 40}]}]},
                {"id": "same", "name": "Two", "family": "GUITAR", "courses": [{"label": "1", "strings": [{"midi": 45}]}]}
              ]
            }
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            TuningLibrary.parse(ByteArrayInputStream(json.toByteArray()))
        }
    }

    @Test
    fun parserRejectsUnknownFamilies() {
        val json = """
            {
              "version": 1,
              "tunings": [
                {"id": "unknown", "name": "Unknown", "family": "BAGPIPE", "courses": [{"label": "1", "strings": [{"midi": 40}]}]}
              ]
            }
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            TuningLibrary.parse(ByteArrayInputStream(json.toByteArray()))
        }
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
