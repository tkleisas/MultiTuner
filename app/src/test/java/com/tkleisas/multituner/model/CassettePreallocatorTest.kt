package com.tkleisas.multituner.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

class CassettePreallocatorTest {

    @Test
    fun createsACompleteZeroedTapeFile() {
        val file = File.createTempFile("multituner-prealloc-", ".wav")
        file.delete()

        assertTrue(CassettePreallocator.ensureFile(file, TEST_TAPE_FRAMES))

        val info = CassetteMixdown.wavInfo(file)
        assertNotNull(info)
        assertEquals(TEST_TAPE_FRAMES, info!!.frames)
        assertEquals(48000, info.sampleRate)
        assertEquals(44L + TEST_TAPE_FRAMES * 2L, file.length())
        assertArrayEquals(ShortArray(TEST_TAPE_FRAMES.toInt()), readSamples(file))
    }

    @Test
    fun preservesExistingAudioAndPadsWithZeros() {
        val file = writeWav(shortArrayOf(1, 2, 3))

        assertTrue(CassettePreallocator.ensureFile(file, TEST_TAPE_FRAMES))

        assertArrayEquals(
            shortArrayOf(1, 2, 3, 0, 0, 0, 0, 0, 0, 0),
            readSamples(file),
        )
    }

    @Test
    fun truncatesAudioBeyondTheTapeEnd() {
        val file = writeWav(ShortArray(12) { (it + 1).toShort() })

        assertTrue(CassettePreallocator.ensureFile(file, TEST_TAPE_FRAMES))

        assertArrayEquals(
            shortArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
            readSamples(file),
        )
    }

    @Test
    fun leavesAnAlreadyPreparedFileUnchanged() {
        val samples = ShortArray(TEST_TAPE_FRAMES.toInt()) { (it * 7).toShort() }
        val file = writeWav(samples)

        assertTrue(CassettePreallocator.ensureFile(file, TEST_TAPE_FRAMES))

        assertArrayEquals(samples, readSamples(file))
    }

    private fun readSamples(file: File): ShortArray {
        val info = CassetteMixdown.wavInfo(file)
        assertNotNull(info)
        RandomAccessFile(file, "r").use { input ->
            input.seek(info!!.dataOffset)
            val data = ByteArray((info.frames * 2).toInt())
            input.readFully(data)
            return ShortArray(info.frames.toInt()) { index ->
                val value = (data[index * 2].toInt() and 0xFF) or
                    ((data[index * 2 + 1].toInt() and 0xFF) shl 8)
                value.toShort()
            }
        }
    }

    private fun writeWav(samples: ShortArray): File {
        val file = File.createTempFile("multituner-prealloc-", ".wav")
        val dataBytes = samples.size * 2
        val header = ByteArray(44)
        "RIFF".toByteArray().copyInto(header, 0)
        putU32(header, 4, 36 + dataBytes)
        "WAVE".toByteArray().copyInto(header, 8)
        "fmt ".toByteArray().copyInto(header, 12)
        putU32(header, 16, 16)
        putU16(header, 20, 1)
        putU16(header, 22, 1)
        putU32(header, 24, 48000)
        putU32(header, 28, 96000)
        putU16(header, 32, 2)
        putU16(header, 34, 16)
        "data".toByteArray().copyInto(header, 36)
        putU32(header, 40, dataBytes)

        RandomAccessFile(file, "rw").use { output ->
            output.write(header)
            val data = ByteArray(dataBytes)
            samples.forEachIndexed { index, sample ->
                data[index * 2] = (sample.toInt() and 0xFF).toByte()
                data[index * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
            }
            output.write(data)
        }
        return file
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
        bytes[offset + 2] = ((value shr 16) and 0xFF).toByte()
        bytes[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    companion object {
        private const val TEST_TAPE_FRAMES = 10L
    }
}
