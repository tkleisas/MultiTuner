package com.tkleisas.multituner.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CassetteMixdownTest {

    @Test
    fun readsWavInfoFromMonoPcm16() {
        val file = tempWav(frames = 4800, sampleRate = 48000)

        val info = CassetteMixdown.wavInfo(file)

        assertEquals(48000, info?.sampleRate)
        assertEquals(4800L, info?.frames)
        assertEquals(44L, info?.dataOffset)
    }

    @Test
    fun totalFramesUsesTheLastTakeEnd() {
        val file = tempWav(frames = 4800, sampleRate = 48000)
        val cassette = Cassette.empty("id", "Demo", CassetteColors.default(0))
            .withAudioTrack(1, CassetteAudioTrack(file.absolutePath, 48000L))

        assertEquals(52800L, CassetteMixdown.totalFrames(cassette))
    }

    @Test
    fun totalFramesIsClampedToTheTapeLength() {
        val file = tempWav(frames = 4800, sampleRate = 48000)
        val cassette = Cassette.empty("id", "Demo", CassetteColors.default(0))
            .withAudioTrack(
                0,
                CassetteAudioTrack(file.absolutePath, Cassette.TAPE_FRAMES),
            )

        assertEquals(Cassette.TAPE_FRAMES, CassetteMixdown.totalFrames(cassette))
    }

    @Test
    fun emptyCassetteHasNoExportFrames() {
        val cassette = Cassette.empty("id", "Demo", CassetteColors.default(0))

        assertEquals(0L, CassetteMixdown.totalFrames(cassette))
    }

    @Test
    fun invalidFilesAreRejected() {
        val file = File.createTempFile("multituner-invalid", ".wav")
        file.writeText("not a wav")

        assertNull(CassetteMixdown.wavInfo(file))

        file.delete()
    }

    @Test
    fun safeNameRemovesUnsafeCharacters() {
        assertEquals("My_Cassette_1", CassetteMixdown.safeName(" My/Cassette:1 "))
        assertEquals("cassette", CassetteMixdown.safeName("///"))
    }

    private fun tempWav(frames: Int, sampleRate: Int): File {
        val file = File.createTempFile("multituner-export", ".wav")
        val dataBytes = frames * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(36 + dataBytes)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1)
        header.putShort(1)
        header.putInt(sampleRate)
        header.putInt(sampleRate * 2)
        header.putShort(2)
        header.putShort(16)
        header.put("data".toByteArray())
        header.putInt(dataBytes)
        RandomAccessFile(file, "rw").use { output ->
            output.write(header.array())
            output.write(ByteArray(dataBytes))
        }
        assertTrue(file.length() > 44L)
        return file
    }
}
