package com.tkleisas.multituner.audio

internal object TunerNative {
    init {
        System.loadLibrary("multituner")
    }

    external fun nativeCreate(sampleRate: Int, windowSize: Int, hopSize: Int): Long
    external fun nativeDestroy(handle: Long)
    external fun nativeReset(handle: Long)

    external fun nativeProcess(
        handle: Long,
        samples: ShortArray,
        count: Int,
        out: FloatArray,
    ): Int
}
