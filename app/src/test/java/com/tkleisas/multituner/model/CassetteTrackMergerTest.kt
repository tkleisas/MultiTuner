package com.tkleisas.multituner.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

class CassetteTrackMergerTest {

    @Test
    fun overwritesOnlyTheRecordedRegion() {
        val existing = writeWav(ShortArray(10) { (it * 100).toShort() })
        val take = writeWav(shortArrayOf(900, 901, 902))
        val output = tempFile("merged")

        assertTrue(
            CassetteTrackMerger.overlay(
                existingFile = existing,
                existingStartSample = 0,
                takeFile = take,
                takeStartSample = 4,
                outputFile = output,
                tapeFrames = TEST_TAPE_FRAMES,
            ),
        )

        val samples = readWav(output)
        assertArrayEquals(
            shortArrayOf(0, 100, 200, 300, 900, 901, 902, 700, 800, 900),
            samples,
        )
        assertEquals(TEST_TAPE_FRAMES, CassetteMixdown.wavInfo(output)?.frames)
    }

    @Test
    fun extendsTheTrackWhenTheTakeIsLonger() {
        val existing = writeWav(shortArrayOf(1, 2, 3, 4))
        val take = writeWav(shortArrayOf(20, 21, 22, 23))
        val output = tempFile("extend")

        assertTrue(
            CassetteTrackMerger.overlay(
                existing,
                0,
                take,
                2,
                output,
                tapeFrames = TEST_TAPE_FRAMES,
            ),
        )

        assertArrayEquals(
            shortArrayOf(1, 2, 20, 21, 22, 23, 0, 0, 0, 0),
            readWav(output),
        )
    }

    @Test
    fun writesLeadingSilenceWhenTheTakeStartsLater() {
        val take = writeWav(shortArrayOf(7, 8))
        val output = tempFile("leading")

        assertTrue(
            CassetteTrackMerger.overlay(
                null,
                0,
                take,
                3,
                output,
                tapeFrames = TEST_TAPE_FRAMES,
            ),
        )

        assertArrayEquals(
            shortArrayOf(0, 0, 0, 7, 8, 0, 0, 0, 0, 0),
            readWav(output),
        )
    }

    @Test
    fun normalizesAnExistingOffsetTrackToZero() {
        val existing = writeWav(shortArrayOf(10, 11, 12))
        val take = writeWav(shortArrayOf(90, 91))
        val output = tempFile("offset")

        assertTrue(
            CassetteTrackMerger.overlay(
                existing,
                5,
                take,
                0,
                output,
                tapeFrames = TEST_TAPE_FRAMES,
            ),
        )

        assertArrayEquals(
            shortArrayOf(90, 91, 0, 0, 0, 10, 11, 12, 0, 0),
            readWav(output),
        )
    }

    @Test
    fun reportsMissingTake() {
        val output = tempFile("missing")
        val missing = File(output.parentFile, "does-not-exist.wav")

        assertTrue(!CassetteTrackMerger.overlay(null, 0, missing, 0, output))
    }

    private fun tempFile(name: String): File =
        File.createTempFile("multituner-merger-$name-", ".wav")

    private fun writeWav(samples: ShortArray): File {
        val file = tempFile("input")
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

    private fun readWav(file: File): ShortArray {
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
