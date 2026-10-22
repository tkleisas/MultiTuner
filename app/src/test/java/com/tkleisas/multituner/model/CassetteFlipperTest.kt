package com.tkleisas.multituner.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

class CassetteFlipperTest {

    @Test
    fun flipSwapsTracksAndMirrorsThemOverTheWholeTape() {
        val samples = listOf(
            shortArrayOf(1, 2, 3),
            shortArrayOf(10, 20),
            shortArrayOf(7),
            shortArrayOf(100, 200, 300, 400),
        )
        val files = samples.map(::writeWav)
        var cassette = Cassette.empty("id", "Cassette", CassetteColors.default(0))
        files.forEachIndexed { index, file ->
            cassette = cassette.withAudioTrack(
                index,
                CassetteAudioTrack(file.absolutePath, 0L),
            )
        }

        val flipped = CassetteFlipper.flip(cassette, TEST_TAPE_FRAMES)

        assertNotNull(flipped)
        assertEquals(cassette.audioTracks[3].filePath, flipped!!.audioTracks[0].filePath)
        assertEquals(cassette.audioTracks[0].filePath, flipped.audioTracks[3].filePath)
        assertTapeFrames(files[3], 6, shortArrayOf(400, 300, 200, 100))
        assertTapeFrames(files[2], 9, shortArrayOf(7))
        assertTapeFrames(files[1], 8, shortArrayOf(20, 10))
        assertTapeFrames(files[0], 7, shortArrayOf(3, 2, 1))
        assertTapeFrames(files[0], 0, shortArrayOf(0, 0))
    }

    @Test
    fun flipMirrorsAcrossMultipleBlocks() {
        val frames = 70_000L
        val tapeFrames = 100_000L
        val samples = ShortArray(frames.toInt()) { index ->
            ((index * 31) % 30000).toShort()
        }
        val file = writeWav(samples)
        val original = Cassette.empty("id", "Cassette", CassetteColors.default(0))
            .withAudioTrack(0, CassetteAudioTrack(file.absolutePath, 0L))

        val flipped = CassetteFlipper.flip(original, tapeFrames)

        assertNotNull(flipped)
        val info = CassetteMixdown.wavInfo(file)
        assertNotNull(info)
        assertEquals(tapeFrames, info!!.frames)
        val destinationStart = tapeFrames - frames
        assertArrayEquals(
            shortArrayOf(samples[69999], samples[69998], samples[69997]),
            readWavAt(file, info, destinationStart, 3),
        )
        assertArrayEquals(
            shortArrayOf(samples[2], samples[1], samples[0]),
            readWavAt(file, info, tapeFrames - 3, 3),
        )
        assertArrayEquals(
            shortArrayOf(0, 0),
            readWavAt(file, info, destinationStart - 2, 2),
        )
    }

    @Test
    fun flippingAgainRestoresTheOriginalAudio() {
        val file = writeWav(shortArrayOf(1, 2, 3, 4, 5))
        val original = Cassette.empty("id", "Cassette", CassetteColors.default(0))
            .withAudioTrack(0, CassetteAudioTrack(file.absolutePath, 0L))

        val flipped = CassetteFlipper.flip(original, TEST_TAPE_FRAMES)
        assertNotNull(flipped)
        assertTapeFrames(file, 5, shortArrayOf(5, 4, 3, 2, 1))

        val restored = CassetteFlipper.flip(flipped!!, TEST_TAPE_FRAMES)
        assertNotNull(restored)
        assertEquals(original.audioTracks[0].filePath, restored!!.audioTracks[0].filePath)
        assertEquals(0L, restored.audioTracks[0].startSample)
        assertTapeFrames(file, 0, shortArrayOf(1, 2, 3, 4, 5))
        assertTapeFrames(file, 5, shortArrayOf(0, 0))
    }

    @Test
    fun flipAccountsForTrackStartPosition() {
        val file = writeWav(shortArrayOf(1, 2, 3))
        val original = Cassette.empty("id", "Cassette", CassetteColors.default(0))
            .withAudioTrack(0, CassetteAudioTrack(file.absolutePath, 2L))

        val flipped = CassetteFlipper.flip(original, TEST_TAPE_FRAMES)

        assertNotNull(flipped)
        assertEquals(0L, flipped!!.audioTracks[0].startSample)
        assertTapeFrames(file, 4, shortArrayOf(0))
        assertTapeFrames(file, 5, shortArrayOf(3, 2, 1))
    }

    @Test
    fun trackLongerThanTapeFailsWithoutChangingTheFile() {
        val file = writeWav(ShortArray(TEST_TAPE_FRAMES.toInt() + 1))
        val original = Cassette.empty("id", "Cassette", CassetteColors.default(0))
            .withAudioTrack(0, CassetteAudioTrack(file.absolutePath, 0L))

        assertNull(CassetteFlipper.flip(original, TEST_TAPE_FRAMES))
        assertEquals(44L + (TEST_TAPE_FRAMES + 1) * 2L, file.length())
    }

    @Test
    fun missingTrackFileFailsWithoutChangingMetadata() {
        val cassette = Cassette.empty("id", "Cassette", CassetteColors.default(0))
            .withAudioTrack(
                0,
                CassetteAudioTrack(
                    File.createTempFile("missing", ".wav").absolutePath,
                    0L,
                ),
            )

        assertTrue(CassetteFlipper.flip(cassette, TEST_TAPE_FRAMES) == null)
    }

    private fun assertTapeFrames(file: File, startFrame: Long, expected: ShortArray) {
        val info = CassetteMixdown.wavInfo(file)
        assertNotNull(info)
        assertEquals(TEST_TAPE_FRAMES, info!!.frames)
        assertEquals(48000, info.sampleRate)
        assertArrayEquals(expected, readWavAt(file, info, startFrame, expected.size))
    }

    private fun readWavAt(
        file: File,
        info: WavInfo,
        startFrame: Long,
        count: Int,
    ): ShortArray {
        RandomAccessFile(file, "r").use { input ->
            input.seek(info.dataOffset + startFrame * 2L)
            val data = ByteArray(count * 2)
            input.readFully(data)
            return ShortArray(count) { index ->
                val value = (data[index * 2].toInt() and 0xFF) or
                    ((data[index * 2 + 1].toInt() and 0xFF) shl 8)
                value.toShort()
            }
        }
    }

    private fun writeWav(samples: ShortArray): File {
        val file = File.createTempFile("multituner-flip-", ".wav")
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
