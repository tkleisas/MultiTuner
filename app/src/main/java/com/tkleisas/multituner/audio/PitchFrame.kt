package com.tkleisas.multituner.audio

data class PitchFrame(
    val frequency: Double,
    val confidence: Double,
    val rms: Double,
    val hasPitch: Boolean,
    val timestampMs: Long = android.os.SystemClock.elapsedRealtime(),
)
