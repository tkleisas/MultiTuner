package com.tkleisas.multituner.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class ReferenceTonePlayer {

    private var track: AudioTrack? = null
    private var worker: Thread? = null

    @Volatile
    private var running = false

    fun play(frequency: Double, durationMs: Long = 2600) {
        stop()
        if (frequency <= 0.0) return
        running = true

        val sampleRate = 48_000
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSize = max(minBuffer, 8192)
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferSize)
            .build()
        track = audioTrack
        if (audioTrack.state != AudioTrack.STATE_INITIALIZED) {
            Log.e(TAG, "AudioTrack failed to initialize: state=${audioTrack.state}")
            audioTrack.release()
            track = null
            running = false
            return
        }

        worker = Thread({
            val totalFrames = (sampleRate * durationMs / 1000L).toInt()
            val fadeFrames = sampleRate / 50
            val block = ShortArray(1024)
            val step = 2.0 * PI * frequency / sampleRate
            var phase = 0.0
            var written = 0

            try {
                audioTrack.play()
                Log.d(TAG, "playing ${frequency}Hz for ${durationMs}ms")
                while (running && written < totalFrames) {
                    val frames = min(block.size, totalFrames - written)
                    for (i in 0 until frames) {
                        val frameIndex = written + i
                        val fadeIn = min(1.0, frameIndex.toDouble() / fadeFrames)
                        val fadeOut = min(1.0, (totalFrames - frameIndex).toDouble() / fadeFrames)
                        val envelope = min(fadeIn, fadeOut)
                        val value = (
                            0.55 * sin(phase) +
                                0.25 * sin(2.0 * phase) +
                                0.12 * sin(3.0 * phase)
                            ) * 0.46 * envelope
                        block[i] = (value * Short.MAX_VALUE).toInt().toShort()
                        phase += step
                        if (phase >= 2.0 * PI) phase -= 2.0 * PI
                    }
                    val result = audioTrack.write(block, 0, frames)
                    if (result <= 0) {
                        Log.e(TAG, "AudioTrack.write returned $result")
                        break
                    }
                    written += result
                }
                Log.d(TAG, "tone finished: $written/$totalFrames frames")
            } catch (error: Throwable) {
                Log.e(TAG, "tone playback failed", error)
            } finally {
                try {
                    audioTrack.stop()
                } catch (_: IllegalStateException) {
                }
                audioTrack.release()
                if (track === audioTrack) track = null
            }
        }, "MultiTunerTone").apply {
            priority = Thread.NORM_PRIORITY
            start()
        }
    }

    fun stop() {
        running = false
        try {
            track?.stop()
        } catch (_: IllegalStateException) {
        }
        worker?.join(500)
        worker = null
        track?.release()
        track = null
    }

    private companion object {
        const val TAG = "MultiTunerTone"
    }
}
