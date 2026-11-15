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
    fun encodeDecodePreservesLaneLengths() {
        val pattern = DrumPattern.fromRows(
            listOf(
                "x.x",
                "x...x",
                "x",
                "x...",
                "x.x.x.x",
                "x...",
                "x...",
                "x...",
            ),
        ).withTrackLength(0, 7)

        val decoded = DrumPattern.decode(pattern.encode())

        assertEquals(pattern, decoded)
        assertEquals(listOf(7, 5, 1, 4, 7, 4, 4, 4), decoded?.lengths)
    }

    @Test
    fun hiddenStepsArePreservedWhenALaneShrinksAndGrows() {
        val pattern = DrumPattern.empty().cycle(0, 10).withTrackLength(0, 3)
        assertEquals(0, pattern.level(0, 10))

        val restored = pattern.withTrackLength(0, 11)
        assertEquals(1, restored.level(0, 10))
    }

    @Test
    fun cyclingOutsideALaneLengthIsIgnored() {
        val pattern = DrumPattern.empty().withTrackLength(0, 4)
        assertEquals(pattern, pattern.cycle(0, 4))
        assertEquals(1, pattern.cycle(0, 3).level(0, 3))
    }

    @Test
    fun commonLengthControlsAllLanesUntilIndependentModeIsEnabled() {
        val common = DrumPattern.empty().withCommonLength(5)
        assertEquals(false, common.independentLengths)
        assertEquals(List(DrumPattern.TRACKS) { 5 }, common.effectiveLengths())
        assertEquals(3, common.withTrackLength(0, 3).length(0))
        assertTrue(common.withTrackLength(0, 3).independentLengths)

        val independent = common.withIndependentLengths(true)
        assertTrue(independent.independentLengths)
        assertEquals(List(DrumPattern.TRACKS) { 5 }, independent.lengths)

        val custom = independent.withTrackLength(0, 3).withTrackLength(1, 7)
        val shared = custom.withIndependentLengths(false)
        assertEquals(5, shared.length(0))
        assertEquals(5, shared.length(1))

        val restored = shared.withIndependentLengths(true)
        assertEquals(3, restored.length(0))
        assertEquals(7, restored.length(1))
    }

    @Test
    fun commonModeEncodeRoundTripPreservesHiddenLaneLengths() {
        val pattern = DrumPattern.empty()
            .withIndependentLengths(true)
            .withTrackLength(0, 3)
            .withTrackLength(1, 7)
            .withCommonLength(5)

        val decoded = DrumPattern.decode(pattern.encode())

        assertEquals(pattern, decoded)
        assertEquals(false, decoded?.independentLengths)
        assertEquals(5, decoded?.commonLength)
    }

    @Test
    fun invalidEncodedPatternReturnsNull() {
        assertEquals(null, DrumPattern.decode("123"))
        assertEquals(null, DrumPattern.decode("9".repeat(128)))
        assertEquals(null, DrumPattern.decode("v2;16,16,16;0000"))
        assertEquals(
            null,
            DrumPattern.decode("v2;0,16,16,16,16,16,16,16;" + "0".repeat(256)),
        )
    }

    @Test
    fun legacyEncodedPatternDefaultsToSixteenSteps() {
        val legacy = List(DrumPattern.TRACKS) { "0".repeat(DrumPattern.DEFAULT_STEPS) }
            .joinToString("")
        val decoded = DrumPattern.decode(legacy)
        assertNotNull(decoded)
        assertEquals(List(DrumPattern.TRACKS) { DrumPattern.DEFAULT_STEPS }, decoded?.lengths)
        assertEquals(DrumPattern.MAX_STEPS, decoded?.steps?.first()?.size)
    }

    @Test
    fun presetsAreValidAndHaveNames() {
        assertEquals(5, DrumPatterns.all.size)
        for (preset in DrumPatterns.all) {
            assertEquals(DrumPattern.TRACKS, preset.pattern.steps.size)
            assertTrue(preset.pattern.steps.all { it.size == DrumPattern.MAX_STEPS })
            assertTrue(preset.pattern.lengths.all { it == DrumPattern.DEFAULT_STEPS })
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
    fun nativeLevelsAndLengthsAreRowMajor() {
        val pattern = DrumPatterns.rock.pattern
        val levels = pattern.toNativeLevels()
        val lengths = pattern.toNativeLengths()
        assertEquals(DrumPattern.TRACKS * DrumPattern.MAX_STEPS, levels.size)
        assertEquals(1, levels[0].toInt())
        assertEquals(0, levels[1].toInt())
        assertEquals(1, levels[4].toInt())
        assertEquals(DrumPattern.TRACKS, lengths.size)
        assertTrue(lengths.all { it.toInt() == DrumPattern.DEFAULT_STEPS })
    }

    @Test
    fun rowsAllowIndependentLengthsFromOneToThirtyTwo() {
        val pattern = DrumPattern.fromRows(
            listOf(
                "x",
                "x.x",
                "x.x.x",
                "x".repeat(32),
                "x...",
                "x...",
                "x...",
                "x...",
            ),
        )

        assertEquals(listOf(1, 3, 5, 32, 4, 4, 4, 4), pattern.lengths)
        assertEquals(DrumPattern.MAX_STEPS, pattern.steps.first().size)
    }

    @Test
    fun rowsRejectInvalidTrackCountsAndLengths() {
        assertThrows(IllegalArgumentException::class.java) {
            DrumPattern.fromRows(List(7) { "x" })
        }
        assertThrows(IllegalArgumentException::class.java) {
            DrumPattern.fromRows(List(8) { "" })
        }
        assertThrows(IllegalArgumentException::class.java) {
            DrumPattern.fromRows(List(8) { "x".repeat(33) })
        }
    }
}
