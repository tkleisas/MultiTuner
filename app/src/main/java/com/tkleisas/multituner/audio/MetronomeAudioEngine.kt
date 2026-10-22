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

    fun setCassettePlaying(playing: Boolean) {
        if (handle != 0L) MetronomeNative.nativeSetCassettePlaying(handle, playing)
    }

    fun isCassettePlaying(): Boolean =
        handle != 0L && MetronomeNative.nativeIsCassettePlaying(handle)

    fun setVarispeed(varispeed: Float) {
        if (handle != 0L) MetronomeNative.nativeSetVarispeed(handle, varispeed)
    }

    fun getVarispeed(): Float =
        if (handle != 0L) MetronomeNative.nativeGetVarispeed(handle) else 1.0f

    fun setFlip(flipped: Boolean) {
        if (handle != 0L) MetronomeNative.nativeSetFlip(handle, flipped)
    }

    fun isFlipped(): Boolean =
        handle != 0L && MetronomeNative.nativeIsFlipped(handle)

    fun positionSamples(): Long =
        if (handle != 0L) MetronomeNative.nativeGetPositionSamples(handle) else 0L

    fun setPositionSamples(position: Long) {
        if (handle != 0L) {
            MetronomeNative.nativeSetPositionSamples(handle, position.coerceAtLeast(0L))
        }
    }

    fun loadCassetteTrack(index: Int, path: String, startSample: Long): Boolean =
        handle != 0L && MetronomeNative.nativeLoadCassetteTrack(
            handle, index, path, startSample,
        )

    fun clearCassetteTracks() {
        if (handle != 0L) MetronomeNative.nativeClearCassetteTracks(handle)
    }

    fun setDeckGain(deck: Int, gain: Float) {
        if (handle != 0L) MetronomeNative.nativeSetDeckGain(handle, deck, gain)
    }

    fun setDeckPan(deck: Int, pan: Float) {
        if (handle != 0L) MetronomeNative.nativeSetDeckPan(handle, deck, pan)
    }

    fun setDeckMute(deck: Int, muted: Boolean) {
        if (handle != 0L) MetronomeNative.nativeSetDeckMute(handle, deck, muted)
    }

    fun setDeckSolo(deck: Int, soloed: Boolean) {
        if (handle != 0L) MetronomeNative.nativeSetDeckSolo(handle, deck, soloed)
    }

    fun setDeckReverbSend(deck: Int, send: Float) {
        if (handle != 0L) {
            MetronomeNative.nativeSetDeckReverbSend(handle, deck, send)
        }
    }

    fun setDeckDelaySend(deck: Int, send: Float) {
        if (handle != 0L) {
            MetronomeNative.nativeSetDeckDelaySend(handle, deck, send)
        }
    }

    fun setDeckBusSend(deck: Int, send: Float) {
        if (handle != 0L) {
            MetronomeNative.nativeSetDeckBusSend(handle, deck, send)
        }
    }

    fun setCassetteVolume(volume: Float) {
        if (handle != 0L) MetronomeNative.nativeSetCassetteVolume(handle, volume)
    }

    fun setTapeEnabled(enabled: Boolean) {
        if (handle != 0L) MetronomeNative.nativeSetTapeEnabled(handle, enabled)
    }

    fun setTapeParameter(index: Int, value: Float) {
        if (handle != 0L) MetronomeNative.nativeSetTapeParameter(handle, index, value)
    }

    fun setReverbReturn(value: Float) {
        if (handle != 0L) MetronomeNative.nativeSetReverbReturn(handle, value)
    }

    fun setReverbSize(value: Float) {
        if (handle != 0L) MetronomeNative.nativeSetReverbSize(handle, value)
    }

    fun setReverbDamping(value: Float) {
        if (handle != 0L) MetronomeNative.nativeSetReverbDamping(handle, value)
    }

    fun setDelayReturn(value: Float) {
        if (handle != 0L) MetronomeNative.nativeSetDelayReturn(handle, value)
    }

    fun setDelayTimeMs(value: Float) {
        if (handle != 0L) MetronomeNative.nativeSetDelayTimeMs(handle, value)
    }

    fun setDelayFeedback(value: Float) {
        if (handle != 0L) MetronomeNative.nativeSetDelayFeedback(handle, value)
    }

    fun setDelayTone(value: Float) {
        if (handle != 0L) MetronomeNative.nativeSetDelayTone(handle, value)
    }

    fun cassettePeakLevel(): Float =
        if (handle != 0L) MetronomeNative.nativeCassettePeakLevel(handle) else 0.0f

    fun startInternalRecording(source: Int, path: String): Boolean =
        handle != 0L && MetronomeNative.nativeStartInternalRecording(
            handle, source, path,
        )

    fun stopInternalRecording(source: Int) {
        if (handle != 0L) MetronomeNative.nativeStopInternalRecording(handle, source)
    }

    fun isInternalRecording(source: Int): Boolean =
        handle != 0L && MetronomeNative.nativeIsInternalRecording(handle, source)

    fun internalPeakLevel(source: Int): Float =
        if (handle != 0L) MetronomeNative.nativeInternalPeakLevel(handle, source) else 0.0f

    fun internalRecordingFailed(source: Int): Boolean =
        handle != 0L && MetronomeNative.nativeInternalRecordingFailed(handle, source)

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
