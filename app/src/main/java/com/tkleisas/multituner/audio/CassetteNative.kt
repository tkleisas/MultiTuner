package com.tkleisas.multituner.audio

internal object CassetteNative {
    init {
        System.loadLibrary("multituner")
    }

    external fun nativeRender(
        paths: Array<String?>,
        startSamples: LongArray,
        gains: FloatArray,
        pans: FloatArray,
        muted: BooleanArray,
        soloed: BooleanArray,
        reverbSends: FloatArray,
        delaySends: FloatArray,
        totalFrames: Long,
        outputPath: String,
        reverbReturn: Float,
        reverbSize: Float,
        reverbDamping: Float,
        delayReturn: Float,
        delayTimeMs: Float,
        delayFeedback: Float,
        delayTone: Float,
        tapeEnabled: Boolean,
        tapeDrive: Float,
        tapeTone: Float,
        tapeWow: Float,
        tapeFlutter: Float,
        tapeHiss: Float,
        tapeMix: Float,
    ): Boolean
}
