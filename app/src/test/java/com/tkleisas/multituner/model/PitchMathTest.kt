package com.tkleisas.multituner.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PitchMathTest {

    @Test
    fun a4MapsToMidi69() {
        assertEquals(69.0, PitchMath.frequencyToMidi(440.0, 440.0), 1e-9)
    }

    @Test
    fun midi69MapsToA4() {
        assertEquals(440.0, PitchMath.midiToFrequency(69.0, 440.0), 1e-9)
    }

    @Test
    fun a2MapsToMidi45() {
        assertEquals(45.0, PitchMath.frequencyToMidi(110.0, 440.0), 1e-9)
        assertEquals(110.0, PitchMath.midiToFrequency(45.0, 440.0), 1e-9)
    }

    @Test
    fun c4MapsToMidi60() {
        assertEquals(60.0, PitchMath.frequencyToMidi(261.6255653005986, 440.0), 1e-9)
        assertEquals(261.6255653005986, PitchMath.midiToFrequency(60.0, 440.0), 1e-9)
    }

    @Test
    fun centsBetweenOctaveIs1200() {
        assertEquals(1200.0, PitchMath.centsBetween(220.0, 110.0), 1e-9)
    }

    @Test
    fun centsBetweenEqualFrequenciesIsZero() {
        assertEquals(0.0, PitchMath.centsBetween(440.0, 440.0), 1e-9)
    }

    @Test
    fun semitoneIsOneHundredCents() {
        val semitone = 440.0 * Math.pow(2.0, 1.0 / 12.0)
        assertEquals(100.0, PitchMath.centsBetween(semitone, 440.0), 1e-9)
    }

    @Test
    fun referencePitchChangesMidiMapping() {
        assertEquals(69.0, PitchMath.frequencyToMidi(432.0, 432.0), 1e-9)
        assertEquals(432.0, PitchMath.midiToFrequency(69.0, 432.0), 1e-9)
        assertEquals(
            31.766653633429414,
            PitchMath.centsBetween(110.0, PitchMath.midiToFrequency(45.0, 432.0)),
            1e-9,
        )
    }

    @Test
    fun midiRoundTripIsStable() {
        for (midi in 20..110) {
            val frequency = PitchMath.midiToFrequency(midi.toDouble(), 440.0)
            assertEquals(midi.toDouble(), PitchMath.frequencyToMidi(frequency, 440.0), 1e-9)
        }
    }
}
