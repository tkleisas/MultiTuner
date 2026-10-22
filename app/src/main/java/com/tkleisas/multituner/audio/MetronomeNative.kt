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
    external fun nativeSetPlaying(handle: Long, playing: Boolean)
    external fun nativeIsPlaying(handle: Long): Boolean
    external fun nativeIsReady(handle: Long): Boolean
    external fun nativeCurrentBeat(handle: Long): Int
    external fun nativeBeatPhase(handle: Long): Float
    external fun nativeSampleRate(handle: Long): Int
}
