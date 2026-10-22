package com.tkleisas.multituner.model

import java.io.File
import java.io.RandomAccessFile

object CassetteFlipper {
    internal fun flip(
        cassette: Cassette,
        tapeFrames: Long = Cassette.TAPE_FRAMES,
    ): Cassette? {
        if (tapeFrames <= 0L) return null

        val tracks = cassette.audioTracks
        val staged = mutableListOf<StagedFile>()

        for (track in tracks) {
            val path = track.filePath ?: continue
            val file = File(path)
            if (!file.isFile) return null
            val info = CassetteMixdown.wavInfo(file) ?: return null
            if (info.sampleRate != Cassette.SAMPLE_RATE) return null
            if (track.startSample < 0L ||
                track.startSample + info.frames > tapeFrames
            ) {
                return null
            }
            val temp = File(file.parentFile, file.name + ".flip")
            val backup = File(file.parentFile, file.name + ".flipbak")
            temp.delete()
            backup.delete()
            if (!reverseWav(
                    sourceFile = file,
                    dataOffset = info.dataOffset,
                    frames = info.frames,
                    startSample = track.startSample,
                    tapeFrames = tapeFrames,
                    destination = temp,
                )
            ) {
                staged.forEach { it.temp.delete() }
                temp.delete()
                return null
            }
            staged += StagedFile(file, temp, backup)
        }

        val committed = mutableListOf<StagedFile>()
        for (item in staged) {
            if (!item.file.renameTo(item.backup)) {
                restore(committed)
                staged.forEach { it.temp.delete() }
                return null
            }
            if (!item.temp.renameTo(item.file)) {
                item.backup.renameTo(item.file)
                restore(committed)
                staged.forEach { it.temp.delete() }
                return null
            }
            committed += item
        }
        committed.forEach { it.backup.delete() }

        val flippedTracks = tracks.asReversed().map { it.copy(startSample = 0L) }
        return cassette.copy(audioTracks = flippedTracks)
    }

    private fun restore(committed: List<StagedFile>) {
        committed.asReversed().forEach { item ->
            item.file.delete()
            item.backup.renameTo(item.file)
        }
    }

    private fun reverseWav(
        sourceFile: File,
        dataOffset: Long,
        frames: Long,
        startSample: Long,
        tapeFrames: Long,
        destination: File,
    ): Boolean {
        return try {
            val dataBytes = tapeFrames * 2L
            if (dataBytes > 0xFFFFFFFFL) return false
            RandomAccessFile(sourceFile, "r").use { source ->
                RandomAccessFile(destination, "rw").use { output ->
                    output.setLength(0)
                    writeHeader(output, dataBytes)

                    val leadingFrames = tapeFrames - (startSample + frames)
                    writeZeros(output, leadingFrames)

                    val blockFrames = 32 * 1024
                    val buffer = ByteArray(blockFrames * 2)
                    var remaining = frames
                    while (remaining > 0) {
                        val count = minOf(blockFrames.toLong(), remaining).toInt()
                        source.seek(dataOffset + (remaining - count) * 2L)
                        source.readFully(buffer, 0, count * 2)
                        reversePairs(buffer, count * 2)
                        output.write(buffer, 0, count * 2)
                        remaining -= count
                    }
                    writeZeros(output, startSample)
                }
            }
            true
        } catch (_: Throwable) {
            destination.delete()
            false
        }
    }

    private fun writeZeros(output: RandomAccessFile, frames: Long) {
        if (frames <= 0L) return
        val zeros = ByteArray(64 * 1024)
        var remaining = frames * 2L
        while (remaining > 0L) {
            val count = minOf(zeros.size.toLong(), remaining).toInt()
            output.write(zeros, 0, count)
            remaining -= count
        }
    }

    private fun reversePairs(bytes: ByteArray, byteCount: Int) {
        var left = 0
        var right = byteCount - 2
        while (left < right) {
            val b0 = bytes[left]
            val b1 = bytes[left + 1]
            bytes[left] = bytes[right]
            bytes[left + 1] = bytes[right + 1]
            bytes[right] = b0
            bytes[right + 1] = b1
            left += 2
            right -= 2
        }
    }

    private fun writeHeader(output: RandomAccessFile, dataBytes: Long) {
        output.seek(0)
        output.write("RIFF".toByteArray())
        writeU32(output, 36L + dataBytes)
        output.write("WAVE".toByteArray())
        output.write("fmt ".toByteArray())
        writeU32(output, 16)
        writeU16(output, 1)
        writeU16(output, 1)
        writeU32(output, Cassette.SAMPLE_RATE.toLong())
        writeU32(output, Cassette.SAMPLE_RATE * 2L)
        writeU16(output, 2)
        writeU16(output, 16)
        output.write("data".toByteArray())
        writeU32(output, dataBytes)
    }

    private fun writeU16(output: RandomAccessFile, value: Int) {
        output.write(value and 0xFF)
        output.write((value shr 8) and 0xFF)
    }

    private fun writeU32(output: RandomAccessFile, value: Long) {
        output.write((value and 0xFF).toInt())
        output.write(((value shr 8) and 0xFF).toInt())
        output.write(((value shr 16) and 0xFF).toInt())
        output.write(((value shr 24) and 0xFF).toInt())
    }

    private data class StagedFile(
        val file: File,
        val temp: File,
        val backup: File,
    )

}
