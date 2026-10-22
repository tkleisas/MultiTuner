package com.tkleisas.multituner.model

import java.io.File
import java.io.RandomAccessFile

object CassettePreallocator {
    fun prepare(cassette: Cassette, store: CassetteStore): Cassette? {
        var updated = cassette
        for (index in 0 until Cassette.TRACKS) {
            val track = cassette.audioTracks[index]
            val path = track.filePath
            val file = path?.let(::File) ?: store.trackFile(cassette, index)
            if (!ensureFile(file)) return null
            if (path != null) {
                updated = updated.withAudioTrack(
                    index,
                    CassetteAudioTrack(file.absolutePath, 0L),
                )
            }
        }
        return updated
    }

    internal fun ensureFile(
        file: File,
        tapeFrames: Long = Cassette.TAPE_FRAMES,
    ): Boolean {
        if (tapeFrames <= 0L) return false
        val dataBytes = tapeFrames * 2L
        if (dataBytes > 0xFFFFFFFFL) return false

        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".prealloc")
        val backup = File(file.parentFile, file.name + ".preallocbak")
        temp.delete()
        if (!file.isFile && backup.isFile && !backup.renameTo(file)) {
            return false
        }
        if (file.isFile) backup.delete()

        val info = if (file.isFile) CassetteMixdown.wavInfo(file) else null
        if (file.isFile && file.length() > 0L && info == null) return false
        if (isPrepared(file, info, tapeFrames)) return true

        try {
            RandomAccessFile(temp, "rw").use { output ->
                output.setLength(0)
                writeHeader(output, dataBytes)
                writeZeros(output, tapeFrames)
                if (info != null) {
                    RandomAccessFile(file, "r").use { source ->
                        copyPcm(
                            source = source,
                            sourceOffset = info.dataOffset,
                            destination = output,
                            destinationOffset = 44L,
                            frames = minOf(info.frames, tapeFrames),
                        )
                    }
                }
            }

            backup.delete()
            if (file.exists() && !file.renameTo(backup)) {
                temp.delete()
                return false
            }
            if (!temp.renameTo(file)) {
                backup.renameTo(file)
                temp.delete()
                return false
            }
            backup.delete()
            return true
        } catch (_: Throwable) {
            temp.delete()
            return false
        }
    }

    private fun isPrepared(
        file: File,
        info: WavInfo?,
        tapeFrames: Long,
    ): Boolean =
        file.length() == 44L + tapeFrames * 2L &&
            info != null &&
            info.sampleRate == Cassette.SAMPLE_RATE &&
            info.dataOffset == 44L &&
            info.frames == tapeFrames

    private fun copyPcm(
        source: RandomAccessFile,
        sourceOffset: Long,
        destination: RandomAccessFile,
        destinationOffset: Long,
        frames: Long,
    ) {
        source.seek(sourceOffset)
        destination.seek(destinationOffset)
        val buffer = ByteArray(64 * 1024)
        var remaining = frames * 2L
        while (remaining > 0L) {
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
