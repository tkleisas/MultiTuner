package com.tkleisas.multituner.model

import kotlin.math.ln
import kotlin.math.pow

enum class InstrumentFamily(val displayName: String) {
    GUITAR("Guitar"),
    BOUZOUKI("Bouzouki"),
    SAZ("Saz"),
    TAMBOURAS("Tambouras"),
}

data class TuningString(
    val midi: Int,
    val centsOffset: Int = 0,
) {
    fun targetFrequency(referenceA4: Double): Double =
        referenceA4 * 2.0.pow((midi - 69) / 12.0) * 2.0.pow(centsOffset / 1200.0)
}

data class Course(
    val label: String,
    val strings: List<TuningString>,
) {
    val noteLabel: String
        get() = strings
            .distinctBy { it.midi }
            .joinToString("·") { NoteNames.label(it.midi) }
}

data class Tuning(
    val id: String,
    val name: String,
    val family: InstrumentFamily,
    val courses: List<Course>,
    val description: String = "",
    val preferFlats: Boolean = false,
) {
    val courseCount: Int get() = courses.size
}

object NoteNames {
    private val sharps = arrayOf(
        "C", "C#", "D", "D#", "E", "F",
        "F#", "G", "G#", "A", "A#", "B",
    )
    private val flats = arrayOf(
        "C", "Db", "D", "Eb", "E", "F",
        "Gb", "G", "Ab", "A", "Bb", "B",
    )

    fun name(midi: Int, preferFlats: Boolean = false): String {
        val index = ((midi % 12) + 12) % 12
        return if (preferFlats) flats[index] else sharps[index]
    }

    fun octave(midi: Int): Int = midi / 12 - 1

    fun label(midi: Int, preferFlats: Boolean = false): String =
        "${name(midi, preferFlats)}${octave(midi)}"
}

object PitchMath {
    fun frequencyToMidi(frequency: Double, referenceA4: Double): Double =
        69.0 + 12.0 * log2(frequency / referenceA4)

    fun midiToFrequency(midi: Double, referenceA4: Double): Double =
        referenceA4 * 2.0.pow((midi - 69.0) / 12.0)

    fun centsBetween(frequency: Double, targetFrequency: Double): Double =
        1200.0 * log2(frequency / targetFrequency)

    private fun log2(value: Double): Double = ln(value) / ln(2.0)
}
