package com.tkleisas.multituner.tuner

data class DisplayUpdate(
    val hasPitch: Boolean,
    val inTune: Boolean,
    val displayMidi: Int?,
    val displayNote: String,
    val frequency: Double,
    val cents: Double,
    val confidence: Double,
    val level: Double,
    val activeCourseIndex: Int?,
    val activeStringIndex: Int?,
)
