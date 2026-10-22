package com.tkleisas.multituner.model

import java.io.File
import java.io.RandomAccessFile

object CassetteTrackMerger {
    internal fun overlay(
        existingFile: File?,
        existingStartSample: Long,
        takeFile: File,
        takeStartSample: Long,
        outputFile: File,
        tapeFrames: Long = Cassette.TAPE_FRAMES,
    ): Boolean {
        if (tapeFrames <= 0L) return false
        val takeInfo = CassetteMixdown.wavInfo(takeFile) ?: return false
        if (takeInfo.sampleRate != Cassette.SAMPLE_RATE || takeInfo.frames <= 0) return false

        val existingInfo = existingFile
            ?.takeIf { it.isFile }
            ?.let { CassetteMixdown.wavInfo(it) }
        if (existingInfo != null && existingInfo.sampleRate != Cassette.SAMPLE_RATE) {
            return false
        }
        if (existingStartSample < 0L ||
            existingInfo != null && existingStartSample + existingInfo.frames > tapeFrames
        ) {
            return false
        }
        if (takeStartSample < 0L || takeStartSample + takeInfo.frames > tapeFrames) {
            return false
        }

        val dataBytes = tapeFrames * 2L
        if (dataBytes > 0xFFFFFFFFL) return false

        outputFile.parentFile?.mkdirs()
        val mergeFile = File(outputFile.parentFile, outputFile.name + ".merge")
        mergeFile.delete()

        try {
            RandomAccessFile(mergeFile, "rw").use { output ->
                output.setLength(0)
                writeHeader(output, dataBytes)
                writeZeros(output, tapeFrames)
                if (existingInfo != null) {
                    RandomAccessFile(existingFile, "r").use { source ->
                        copyPcm(
                            source = source,
                            sourceOffset = existingInfo.dataOffset,
                            destination = output,
                            destinationOffset = 44L + existingStartSample * 2L,
                            byteCount = existingInfo.frames * 2L,
                        )
                    }
                }
                RandomAccessFile(takeFile, "r").use { source ->
                    copyPcm(
                        source = source,
                        sourceOffset = takeInfo.dataOffset,
                        destination = output,
                        destinationOffset = 44L + takeStartSample * 2L,
                        byteCount = takeInfo.frames * 2L,
                    )
                }
            }

            val backup = File(outputFile.parentFile, outputFile.name + ".bak")
            backup.delete()
            if (outputFile.exists() && !outputFile.renameTo(backup)) {
                mergeFile.delete()
                return false
            }
            if (!mergeFile.renameTo(outputFile)) {
                backup.renameTo(outputFile)
                mergeFile.delete()
                return false
            }
            backup.delete()
            return true
        } catch (_: Throwable) {
            mergeFile.delete()
            return false
        }
    }

    private fun copyPcm(
        source: RandomAccessFile,
        sourceOffset: Long,
        destination: RandomAccessFile,
        destinationOffset: Long,
        byteCount: Long,
    ) {
        source.seek(sourceOffset)
        destination.seek(destinationOffset)
        val buffer = ByteArray(64 * 1024)
        var remaining = byteCount
        while (remaining > 0) {
            val count = minOf(buffer.size.toLong(), remaining).toInt()
            source.readFully(buffer, 0, count)
            destination.write(buffer, 0, count)
            remaining -= count
        }
    }

    private fun writeZeros(output: RandomAccessFile, frames: Long) {
        val zeros = ByteArray(64 * 1024)
        var remaining = frames * 2L
        while (remaining > 0L) {
            val count = minOf(zeros.size.toLong(), remaining).toInt()
            output.write(zeros, 0, count)
            remaining -= count
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
}
