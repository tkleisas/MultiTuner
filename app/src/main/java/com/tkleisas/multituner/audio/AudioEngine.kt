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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

sealed interface AudioStatus {
    data object Idle : AudioStatus
    data object Starting : AudioStatus
    data class Running(val sampleRate: Int, val source: String) : AudioStatus
    data object Stopped : AudioStatus
    data class Error(val message: String) : AudioStatus
}

class AudioEngine(context: Context) {

    private val appContext = context.applicationContext
    private val _frame = MutableStateFlow<PitchFrame?>(null)
    val frame: StateFlow<PitchFrame?> = _frame.asStateFlow()

    private val _status = MutableStateFlow<AudioStatus>(AudioStatus.Idle)
    val status: StateFlow<AudioStatus> = _status.asStateFlow()

    @Volatile
    private var running = false

    @Volatile
    private var activeRecord: AudioRecord? = null
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        _status.value = AudioStatus.Starting
        thread = Thread({ captureLoop() }, "MultiTunerAudio").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        running = false
        try {
            activeRecord?.stop()
        } catch (_: IllegalStateException) {
        }
        thread?.join(2000)
        thread = null
        _frame.value = null
        _status.value = AudioStatus.Stopped
    }

    private fun captureLoop() {
        if (!hasRecordPermission()) {
            running = false
            _status.value = AudioStatus.Error("Microphone permission is required")
            return
        }

        val sampleRates = listOf(48_000, 44_100)
        var source = preferredSource()
        var record = openRecord(sampleRates, source)

        if (record == null && source != MediaRecorder.AudioSource.MIC) {
            source = MediaRecorder.AudioSource.MIC
            record = openRecord(sampleRates, source)
        }

        if (record == null) {
            running = false
            _status.value = AudioStatus.Error("Could not open the microphone")
            return
        }

        val actualRate = record.sampleRate
        val native = NativeTuner(actualRate)
        val buffer = ShortArray(1024)

        try {
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                _status.value = AudioStatus.Error("Microphone did not start")
                return
            }

            activeRecord = record
            _status.value = AudioStatus.Running(
                sampleRate = actualRate,
                source = sourceName(source),
            )

            while (running) {
                val count = record.read(buffer, 0, buffer.size)
                if (count > 0) {
                    native.process(buffer, count)?.let { _frame.value = it }
                } else if (count == AudioRecord.ERROR_DEAD_OBJECT) {
                    _status.value = AudioStatus.Error("Audio input was lost")
                    break
                }
            }
        } catch (error: SecurityException) {
            _status.value = AudioStatus.Error("Microphone permission is required")
        } catch (error: Throwable) {
            _status.value = AudioStatus.Error(error.message ?: "Audio input failed")
        } finally {
            running = false
            try {
                record.stop()
            } catch (_: IllegalStateException) {
            }
            record.release()
            native.close()
            activeRecord = null
        }
    }

    @SuppressLint("MissingPermission")
    private fun openRecord(sampleRates: List<Int>, source: Int): AudioRecord? {
        if (!hasRecordPermission()) return null
        for (rate in sampleRates) {
            val minBuffer = AudioRecord.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBuffer <= 0) continue

            val bufferSize = max(minBuffer, 8192)
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(rate)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()

            val record = try {
                AudioRecord.Builder()
                    .setAudioSource(source)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferSize)
                    .build()
            } catch (_: Throwable) {
                null
            }

            if (record != null && record.state == AudioRecord.STATE_INITIALIZED) {
                return record
            }
            record?.release()
        }
        return null
    }

    private fun hasRecordPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

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

    private fun sourceName(source: Int): String = when (source) {
        MediaRecorder.AudioSource.UNPROCESSED -> "unprocessed"
        MediaRecorder.AudioSource.MIC -> "microphone"
        else -> "audio input"
    }
}
