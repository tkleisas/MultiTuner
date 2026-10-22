package com.tkleisas.multituner.audio

internal object MetronomeNative {
    init {
        System.loadLibrary("multituner")
    }

    external fun nativeCreate(): Long
    external fun nativeDestroy(handle: Long)
    external fun nativeSetBpm(handle: Long, bpm: Double)
    external fun nativeSetBeatsPerBar(handle: Long, beats: Int)
    external fun nativeSetSubdivision(handle: Long, subdivision: Int)
    external fun nativeSetVolume(handle: Long, volume: Float)
    external fun nativeSetAccentEnabled(handle: Long, enabled: Boolean)
    external fun nativeSetMetronomePlaying(handle: Long, playing: Boolean)
    external fun nativeSetDrumsPlaying(handle: Long, playing: Boolean)
    external fun nativeIsPlaying(handle: Long): Boolean
    external fun nativeIsMetronomePlaying(handle: Long): Boolean
    external fun nativeIsDrumsPlaying(handle: Long): Boolean
    external fun nativeIsReady(handle: Long): Boolean
    external fun nativeCurrentBeat(handle: Long): Int
    external fun nativeBeatPhase(handle: Long): Float
    external fun nativeCurrentDrumStep(handle: Long): Int
    external fun nativeSampleRate(handle: Long): Int
    external fun nativeSetDrumStep(handle: Long, track: Int, step: Int, level: Int)
    external fun nativeSetDrumPattern(handle: Long, levels: ByteArray)
    external fun nativeSetDrumSwing(handle: Long, swing: Float)
    external fun nativeSetDrumVolume(handle: Long, volume: Float)
}
