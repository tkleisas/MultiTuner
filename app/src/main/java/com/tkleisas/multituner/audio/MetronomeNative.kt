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
    external fun nativeSetCassettePlaying(handle: Long, playing: Boolean)
    external fun nativeIsCassettePlaying(handle: Long): Boolean
    external fun nativeSetVarispeed(handle: Long, varispeed: Float)
    external fun nativeGetVarispeed(handle: Long): Float
    external fun nativeSetFlip(handle: Long, flipped: Boolean)
    external fun nativeIsFlipped(handle: Long): Boolean
    external fun nativeGetPositionSamples(handle: Long): Long
    external fun nativeSetPositionSamples(handle: Long, position: Long)
    external fun nativeLoadCassetteTrack(
        handle: Long,
        index: Int,
        path: String,
        startSample: Long,
    ): Boolean
    external fun nativeClearCassetteTracks(handle: Long)
    external fun nativeSetDeckGain(handle: Long, deck: Int, gain: Float)
    external fun nativeSetDeckPan(handle: Long, deck: Int, pan: Float)
    external fun nativeSetDeckMute(handle: Long, deck: Int, muted: Boolean)
    external fun nativeSetDeckSolo(handle: Long, deck: Int, soloed: Boolean)
    external fun nativeSetDeckReverbSend(handle: Long, deck: Int, send: Float)
    external fun nativeSetDeckDelaySend(handle: Long, deck: Int, send: Float)
    external fun nativeSetCassetteVolume(handle: Long, volume: Float)
    external fun nativeSetTapeEnabled(handle: Long, enabled: Boolean)
    external fun nativeSetTapeParameter(handle: Long, index: Int, value: Float)
    external fun nativeSetReverbReturn(handle: Long, value: Float)
    external fun nativeSetReverbSize(handle: Long, value: Float)
    external fun nativeSetReverbDamping(handle: Long, value: Float)
    external fun nativeSetDelayReturn(handle: Long, value: Float)
    external fun nativeSetDelayTimeMs(handle: Long, value: Float)
    external fun nativeSetDelayFeedback(handle: Long, value: Float)
    external fun nativeSetDelayTone(handle: Long, value: Float)
    external fun nativeCassettePeakLevel(handle: Long): Float
}
