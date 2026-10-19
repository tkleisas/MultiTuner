package com.tkleisas.multituner.audio

internal class NativeTuner(
    private val sampleRate: Int,
    private val windowSize: Int = 4096,
    private val hopSize: Int = 2048,
) : AutoCloseable {

    private val handle: Long =
        TunerNative.nativeCreate(sampleRate, windowSize, hopSize)
    private val result = FloatArray(3)

    val analysisWindowMs: Double
        get() = 1000.0 * windowSize / sampleRate

    fun process(samples: ShortArray, count: Int): PitchFrame? {
        if (handle == 0L) return null
        val status = TunerNative.nativeProcess(handle, samples, count, result)
        if (status == 0) return null
        return PitchFrame(
            frequency = result[0].toDouble(),
            confidence = result[1].toDouble(),
            rms = result[2].toDouble(),
            hasPitch = status == 2,
        )
    }

    fun reset() {
        if (handle != 0L) TunerNative.nativeReset(handle)
    }

    override fun close() {
        if (handle != 0L) {
            TunerNative.nativeDestroy(handle)
        }
    }
}
