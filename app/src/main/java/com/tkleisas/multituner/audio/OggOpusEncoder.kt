package com.tkleisas.multituner.audio

import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import androidx.annotation.RequiresApi
import com.tkleisas.multituner.model.CassetteMixdown
import java.io.File
import java.io.FileInputStream

object OggOpusEncoder {
    private const val SAMPLE_RATE = 48000
    private const val CHANNELS = 2
    private const val BYTES_PER_FRAME = CHANNELS * 2
    private const val BIT_RATE = 128_000

    fun isAvailable(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { codec ->
                codec.isEncoder && codec.supportedTypes.any { type ->
                    type.equals(MediaFormat.MIMETYPE_AUDIO_OPUS, ignoreCase = true)
                }
            }
        }.getOrDefault(false)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun encode(wav: File, output: File): Boolean {
        val info = CassetteMixdown.wavInfo(wav) ?: return false
        if (info.sampleRate != SAMPLE_RATE || info.frames <= 0) return false
        output.parentFile?.mkdirs()
        if (output.exists()) output.delete()

        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        return try {
            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_OPUS,
                SAMPLE_RATE,
                CHANNELS,
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 65_536)
            }
            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
            codec = encoder
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            var trackIndex = -1
            var inputDone = false
            var outputDone = false
            var framesRead = 0L
            val bufferInfo = MediaCodec.BufferInfo()
            val pcm = ByteArray(65_536)
            var remainingBytes = info.frames * BYTES_PER_FRAME

            fun handleOutput(outputIndex: Int): Boolean {
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val activeMuxer = muxer ?: MediaMuxer(
                        output.absolutePath,
                        MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG,
                    ).also { muxer = it }
                    trackIndex = activeMuxer.addTrack(encoder.outputFormat)
                    activeMuxer.start()
                    muxerStarted = true
                    return false
                }
                if (outputIndex < 0) return false

                val outputBuffer = encoder.getOutputBuffer(outputIndex)
                if (outputBuffer != null && bufferInfo.size > 0 && muxerStarted) {
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    muxer?.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                }
                encoder.releaseOutputBuffer(outputIndex, false)
                return bufferInfo.flags and
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            }

            FileInputStream(wav).use { input ->
                input.channel.position(info.dataOffset)
                while (!outputDone) {
                    var queuedInput = false
                    while (!inputDone) {
                        val inputIndex = encoder.dequeueInputBuffer(0L)
                        if (inputIndex < 0) break
                        val inputBuffer = encoder.getInputBuffer(inputIndex)
                        if (inputBuffer == null) {
                            encoder.queueInputBuffer(
                                inputIndex, 0, 0, presentationTime(framesRead),
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                            queuedInput = true
                            break
                        }

                        inputBuffer.clear()
                        val toRead = minOf(
                            inputBuffer.remaining(),
                            pcm.size,
                            remainingBytes.coerceAtMost(Int.MAX_VALUE.toLong())
                                .toInt(),
                        )
                        val count = if (toRead >= 4) {
                            input.read(pcm, 0, toRead)
                        } else {
                            -1
                        }
                        if (count <= 0) {
                            encoder.queueInputBuffer(
                                inputIndex, 0, 0, presentationTime(framesRead),
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            val aligned = count - (count % BYTES_PER_FRAME)
                            inputBuffer.put(pcm, 0, aligned)
                            remainingBytes -= aligned.toLong()
                            val finish = remainingBytes <= 0
                            encoder.queueInputBuffer(
                                inputIndex, 0, aligned,
                                presentationTime(framesRead),
                                if (finish) {
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                } else {
                                    0
                                },
                            )
                            framesRead += aligned / BYTES_PER_FRAME
                            inputDone = finish
                        }
                        queuedInput = true
                    }

                    var drainedOutput = false
                    while (true) {
                        val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 0L)
                        if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) break
                        if (outputIndex < 0 &&
                            outputIndex != MediaCodec.INFO_OUTPUT_FORMAT_CHANGED
                        ) {
                            break
                        }
                        drainedOutput = true
                        if (handleOutput(outputIndex)) {
                            outputDone = true
                            break
                        }
                    }

                    if (!queuedInput && !drainedOutput && !outputDone) {
                        val outputIndex =
                            encoder.dequeueOutputBuffer(bufferInfo, 10_000L)
                        if (handleOutput(outputIndex)) outputDone = true
                    }
                }
            }
            muxerStarted
        } catch (_: Throwable) {
            if (output.exists()) output.delete()
            false
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
        }
    }

    private fun presentationTime(frames: Long): Long =
        frames * 1_000_000L / SAMPLE_RATE
}
