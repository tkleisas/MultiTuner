package com.tkleisas.multituner.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteNamesTest {

    @Test
    fun naturalNotesIncludeOctave() {
        assertEquals("E2", NoteNames.label(40))
        assertEquals("A2", NoteNames.label(45))
        assertEquals("C4", NoteNames.label(60))
        assertEquals("A4", NoteNames.label(69))
        assertEquals("E4", NoteNames.label(64))
    }

    @Test
    fun sharpNamesAreUsedByDefault() {
        assertEquals("C#4", NoteNames.label(61))
        assertEquals("D#4", NoteNames.label(63))
        assertEquals("F#4", NoteNames.label(66))
        assertEquals("G#4", NoteNames.label(68))
        assertEquals("A#4", NoteNames.label(70))
    }

    @Test
    fun flatNamesAreAvailable() {
        assertEquals("Db4", NoteNames.label(61, preferFlats = true))
        assertEquals("Eb4", NoteNames.label(63, preferFlats = true))
        assertEquals("Gb4", NoteNames.label(66, preferFlats = true))
        assertEquals("Ab4", NoteNames.label(68, preferFlats = true))
        assertEquals("Bb4", NoteNames.label(70, preferFlats = true))
    }

    @Test
    fun octaveNumbersFollowMidiConvention() {
        assertEquals(-1, NoteNames.octave(0))
        assertEquals(0, NoteNames.octave(12))
        assertEquals(4, NoteNames.octave(60))
        assertEquals(9, NoteNames.octave(127))
    }
}
