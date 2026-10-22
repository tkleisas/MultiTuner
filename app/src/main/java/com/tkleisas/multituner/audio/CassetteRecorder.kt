package com.tkleisas.multituner.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.tkleisas.multituner.model.Cassette
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class CassetteRecorder(context: Context) {

    private val appContext = context.applicationContext
    private var record: AudioRecord? = null
    private var worker: Thread? = null

    @Volatile
    private var running = false

    @Volatile
    var isRecording: Boolean = false
        private set

    @Volatile
    var maxReached: Boolean = false
        private set

    @Volatile
    var error: String? = null
        private set

    @Volatile
    var peakLevel: Float = 0.0f
        private set

    @Volatile
    var clipping: Boolean = false
        private set

    @Volatile
    private var inputGain: Float = 1.0f

    fun setInputGain(gain: Float) {
        inputGain = gain.coerceIn(0.0f, 2.0f)
    }

    fun start(file: File): Boolean {
        stop()
        error = null
        maxReached = false
        peakLevel = 0.0f
        clipping = false

        if (ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.RECORD_AUDIO,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            error = "Microphone permission required"
            return false
        }

        val source = preferredSource()
        val audioRecord = openRecord(source)
            ?: if (source != MediaRecorder.AudioSource.MIC) {
                openRecord(MediaRecorder.AudioSource.MIC)
            } else {
                null
            }

        if (audioRecord == null) {
            error = "Could not open the microphone"
            return false
        }

        val randomAccess = try {
            file.parentFile?.mkdirs()
            RandomAccessFile(file, "rw").apply {
                setLength(0)
                writeWavHeader(this, 0)
            }
        } catch (throwable: Throwable) {
            audioRecord.release()
            error = throwable.message ?: "Could not create recording file"
            return false
        }

        record = audioRecord
        running = true
        isRecording = true
        val started = CountDownLatch(1)

        worker = Thread({
            val buffer = ShortArray(2048)
            val bytes = ByteArray(buffer.size * 2)
            var frames = 0L
            val maxFrames = MAX_SECONDS * SAMPLE_RATE

            try {
                audioRecord.startRecording()
                if (audioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    error = "Microphone did not start"
                    return@Thread
                }
                started.countDown()

                while (running && frames < maxFrames) {
                    val count = audioRecord.read(buffer, 0, buffer.size)
                    if (count <= 0) {
                        if (count == AudioRecord.ERROR_DEAD_OBJECT) break
                        continue
                    }
                    val gain = inputGain
                    var byteIndex = 0
                    var blockPeak = 0.0f
                    var blockClipped = false
                    for (i in 0 until count) {
                        val scaled = buffer[i] * gain
                        val magnitude = abs(scaled)
                        if (magnitude > blockPeak) blockPeak = magnitude
                        if (magnitude > 32767.0f) blockClipped = true
                        val value = scaled.roundToInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        bytes[byteIndex++] = (value and 0xFF).toByte()
                        bytes[byteIndex++] = ((value shr 8) and 0xFF).toByte()
                    }
                    peakLevel = (blockPeak / 32768.0f).coerceAtMost(2.0f)
                    if (blockClipped) clipping = true
                    randomAccess.write(bytes, 0, count * 2)
                    frames += count
                }

                if (frames >= maxFrames) {
                    maxReached = true
                }
            } catch (throwable: Throwable) {
                error = throwable.message ?: "Recording failed"
            } finally {
                started.countDown()
                running = false
                isRecording = false
                try {
                    audioRecord.stop()
                } catch (_: IllegalStateException) {
                }
                audioRecord.release()
                try {
                    randomAccess.seek(4)
                    randomAccess.write(intBytes((36 + frames * 2).toInt()))
                    randomAccess.seek(40)
                    randomAccess.write(intBytes((frames * 2).toInt()))
                    randomAccess.close()
                } catch (throwable: Throwable) {
                    error = throwable.message ?: "Could not finalize recording"
                }
                record = null
            }
        }, "MultiTunerCassetteRecorder").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }

        val didStart = try {
            started.await(1, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        if (!didStart || error != null) {
            if (error == null) error = "Microphone did not start"
            stop()
            return false
        }
        return true
    }

    fun stop() {
        running = false
        try {
            record?.stop()
        } catch (_: IllegalStateException) {
        }
        worker?.join(2000)
        worker = null
        isRecording = false
        peakLevel = 0.0f
    }

    @SuppressLint("MissingPermission")
    private fun openRecord(source: Int): AudioRecord? {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return null

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()

        val audioRecord = try {
            AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(format)
                .setBufferSizeInBytes(max(minBuffer, 8192))
                .build()
        } catch (_: Throwable) {
            return null
        }

        if (audioRecord.state == AudioRecord.STATE_INITIALIZED) return audioRecord
        audioRecord.release()
        return null
    }

    private fun preferredSource(): Int {
        val audioManager = appContext.getSystemService(AudioManager::class.java)
        val supportsUnprocessed = audioManager
            ?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        return if (supportsUnprocessed) {
            MediaRecorder.AudioSource.UNPROCESSED
        } else {
            MediaRecorder.AudioSource.MIC
        }
    }

    private fun writeWavHeader(file: RandomAccessFile, dataBytes: Int) {
        val header = ByteArray(44)
        "RIFF".toByteArray().copyInto(header, 0)
        intBytes(36 + dataBytes).copyInto(header, 4)
        "WAVE".toByteArray().copyInto(header, 8)
        "fmt ".toByteArray().copyInto(header, 12)
        intBytes(16).copyInto(header, 16)
        shortBytes(1).copyInto(header, 20)
        shortBytes(1).copyInto(header, 22)
        intBytes(SAMPLE_RATE).copyInto(header, 24)
        intBytes(SAMPLE_RATE * 2).copyInto(header, 28)
        shortBytes(2).copyInto(header, 32)
        shortBytes(16).copyInto(header, 34)
        "data".toByteArray().copyInto(header, 36)
        intBytes(dataBytes).copyInto(header, 40)
        file.write(header)
        file.seek(44)
    }

    private fun intBytes(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )

    private fun shortBytes(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
    )

    companion object {
        const val SAMPLE_RATE = Cassette.SAMPLE_RATE
        const val MAX_SECONDS = Cassette.TAPE_SECONDS
    }
}
