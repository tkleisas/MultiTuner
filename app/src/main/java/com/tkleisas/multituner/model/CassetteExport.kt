package com.tkleisas.multituner.model

import java.io.File
import java.io.RandomAccessFile

enum class CassetteExportFormat(
    val extension: String,
    val mimeType: String,
    val title: String,
) {
    WAV("wav", "audio/wav", "WAV · lossless stereo"),
    OGG("ogg", "audio/ogg", "OGG Opus · smaller file"),
}

data class WavInfo(
    val sampleRate: Int,
    val frames: Long,
    val dataOffset: Long,
)

object CassetteMixdown {
    fun totalFrames(cassette: Cassette): Long {
        var end = 0L
        cassette.audioTracks.forEach { track ->
            val path = track.filePath ?: return@forEach
            val info = wavInfo(File(path)) ?: return@forEach
            val frames = if (info.sampleRate > 0) {
                info.frames * Cassette.SAMPLE_RATE / info.sampleRate
            } else {
                info.frames
            }
            end = maxOf(end, track.startSample + frames)
        }
        return end.coerceIn(0L, Cassette.TAPE_FRAMES)
    }

    fun wavInfo(file: File): WavInfo? {
        if (!file.isFile || file.length() < 44L) return null
        return runCatching {
            RandomAccessFile(file, "r").use { input ->
                val header = ByteArray(12)
                input.readFully(header)
                if (String(header, 0, 4) != "RIFF" ||
                    String(header, 8, 4) != "WAVE"
                ) {
                    return@use null
                }

                var offset = 12L
                var sampleRate = 0
                while (offset + 8 <= input.length()) {
                    input.seek(offset)
                    val id = ByteArray(4)
                    input.readFully(id)
                    val size = readU32(input)
                    val chunkData = offset + 8
                    if (chunkData + size > input.length()) return@use null

                    when (String(id)) {
                        "fmt " -> {
                            if (size >= 8) {
                                input.seek(chunkData + 4)
                                sampleRate = readU32(input).toInt()
                            }
                        }

                        "data" -> {
                            val frames = size / 2L
                            if (frames <= 0) return@use null
                            return@use WavInfo(
                                sampleRate = if (sampleRate > 0) sampleRate else 48000,
                                frames = frames,
                                dataOffset = chunkData,
                            )
                        }
                    }
                    offset = chunkData + size + (size and 1L)
                }
                null
            }
        }.getOrNull()
    }

    fun safeName(name: String): String {
        val cleaned = name.trim()
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_', '.')
        return cleaned.ifBlank { "cassette" }
    }

    private fun readU32(input: RandomAccessFile): Long {
        val b0 = input.read()
        val b1 = input.read()
        val b2 = input.read()
        val b3 = input.read()
        if (b0 < 0 || b1 < 0 || b2 < 0 || b3 < 0) return 0L
        return (b0.toLong()) or
            (b1.toLong() shl 8) or
            (b2.toLong() shl 16) or
            (b3.toLong() shl 24)
    }
}
