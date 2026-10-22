package com.tkleisas.multituner.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DrumPatternTest {

    @Test
    fun cycleGoesOffNormalAccentOff() {
        val pattern = DrumPattern.empty()
        val normal = pattern.cycle(0, 0)
        val accent = normal.cycle(0, 0)
        val off = accent.cycle(0, 0)

        assertEquals(0, pattern.level(0, 0))
        assertEquals(1, normal.level(0, 0))
        assertEquals(2, accent.level(0, 0))
        assertEquals(0, off.level(0, 0))
        assertEquals(0, pattern.level(0, 0))
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val pattern = DrumPatterns.funk.pattern
        val decoded = DrumPattern.decode(pattern.encode())
        assertEquals(pattern, decoded)
    }

    @Test
    fun invalidEncodedPatternReturnsNull() {
        assertEquals(null, DrumPattern.decode("123"))
        assertEquals(null, DrumPattern.decode("9".repeat(128)))
    }

    @Test
    fun presetsAreValidAndHaveNames() {
        assertEquals(5, DrumPatterns.all.size)
        for (preset in DrumPatterns.all) {
            assertEquals(DrumPattern.TRACKS, preset.pattern.steps.size)
            assertTrue(preset.pattern.steps.all { it.size == DrumPattern.STEPS })
            assertTrue(preset.name.isNotBlank())
        }
    }

    @Test
    fun presetsHaveExpectedIds() {
        assertEquals("rock", DrumPatterns.default.id)
        assertNotNull(DrumPatterns.byId("funk"))
        assertNotNull(DrumPatterns.byId("house"))
        assertNotNull(DrumPatterns.byId("break"))
        assertNotNull(DrumPatterns.byId("empty"))
        assertEquals(null, DrumPatterns.byId("missing"))
    }

    @Test
    fun nativeLevelsAreRowMajor() {
        val levels = DrumPatterns.rock.pattern.toNativeLevels()
        assertEquals(128, levels.size)
        assertEquals(1, levels[0].toInt())
        assertEquals(0, levels[1].toInt())
        assertEquals(1, levels[4].toInt())
    }

    @Test
    fun rowsRequireEightBySixteen() {
        assertThrows(IllegalArgumentException::class.java) {
            DrumPattern.fromRows(List(8) { "................" }.dropLast(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            DrumPattern.fromRows(List(8) { "..." })
        }
    }
}
