package com.tkleisas.multituner.audio

internal class MetronomeAudioEngine : AutoCloseable {

    private var handle: Long = MetronomeNative.nativeCreate()

    val isReady: Boolean
        get() = handle != 0L && MetronomeNative.nativeIsReady(handle)

    fun setBpm(bpm: Double) {
        if (handle != 0L) MetronomeNative.nativeSetBpm(handle, bpm)
    }

    fun setBeatsPerBar(beats: Int) {
        if (handle != 0L) MetronomeNative.nativeSetBeatsPerBar(handle, beats)
    }

    fun setSubdivision(subdivision: Int) {
        if (handle != 0L) MetronomeNative.nativeSetSubdivision(handle, subdivision)
    }

    fun setVolume(volume: Float) {
        if (handle != 0L) MetronomeNative.nativeSetVolume(handle, volume)
    }

    fun setAccentEnabled(enabled: Boolean) {
        if (handle != 0L) MetronomeNative.nativeSetAccentEnabled(handle, enabled)
    }

    fun setMetronomePlaying(playing: Boolean) {
        if (handle != 0L) MetronomeNative.nativeSetMetronomePlaying(handle, playing)
    }

    fun setDrumsPlaying(playing: Boolean) {
        if (handle != 0L) MetronomeNative.nativeSetDrumsPlaying(handle, playing)
    }

    fun isPlaying(): Boolean =
        handle != 0L && MetronomeNative.nativeIsPlaying(handle)

    fun isMetronomePlaying(): Boolean =
        handle != 0L && MetronomeNative.nativeIsMetronomePlaying(handle)

    fun isDrumsPlaying(): Boolean =
        handle != 0L && MetronomeNative.nativeIsDrumsPlaying(handle)

    fun currentDrumStep(): Int =
        if (handle != 0L) MetronomeNative.nativeCurrentDrumStep(handle) else 0

    fun setDrumStep(track: Int, step: Int, level: Int) {
        if (handle != 0L) {
            MetronomeNative.nativeSetDrumStep(handle, track, step, level)
        }
    }

    fun setDrumPattern(levels: ByteArray) {
        if (handle != 0L) MetronomeNative.nativeSetDrumPattern(handle, levels)
    }

    fun setDrumSwing(swing: Float) {
        if (handle != 0L) MetronomeNative.nativeSetDrumSwing(handle, swing)
    }

    fun setDrumVolume(volume: Float) {
        if (handle != 0L) MetronomeNative.nativeSetDrumVolume(handle, volume)
    }

    fun currentBeat(): Int =
        if (handle != 0L) MetronomeNative.nativeCurrentBeat(handle) else 0

    fun beatPhase(): Float =
        if (handle != 0L) MetronomeNative.nativeBeatPhase(handle) else 0.0f

    fun sampleRate(): Int =
        if (handle != 0L) MetronomeNative.nativeSampleRate(handle) else 0

    override fun close() {
        if (handle != 0L) {
            MetronomeNative.nativeDestroy(handle)
            handle = 0L
        }
    }
}
