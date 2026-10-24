package com.tkleisas.multituner.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class CassetteTest {

    @Test
    fun emptyCassetteHasFourTracksAndDecks() {
        val cassette = Cassette.empty("id", "Cassette 1", CassetteColors.default(0))

        assertEquals(Cassette.TRACKS, cassette.audioTracks.size)
        assertEquals(Cassette.TRACKS, cassette.decks.size)
        assertEquals("Cassette 1", cassette.name)
    }

    @Test
    fun withAudioTrackReplacesOnlyTheRequestedTrack() {
        val cassette = Cassette.empty("id", "Cassette 1", CassetteColors.default(0))
        val take = CassetteAudioTrack(filePath = "/tmp/take.wav", startSample = 48000L)

        val updated = cassette.withAudioTrack(2, take)

        assertNotSame(cassette, updated)
        assertEquals(take, updated.audioTracks[2])
        assertEquals(cassette.audioTracks[0], updated.audioTracks[0])
        assertEquals(cassette.audioTracks[3], updated.audioTracks[3])
    }

    @Test
    fun invalidTrackIndexLeavesTheCassetteUnchanged() {
        val cassette = Cassette.empty("id", "Cassette 1", CassetteColors.default(0))

        assertSame(cassette, cassette.withAudioTrack(-1, CassetteAudioTrack()))
        assertSame(cassette, cassette.withAudioTrack(4, CassetteAudioTrack()))
    }

    @Test
    fun withDeckReplacesOnlyTheRequestedDeck() {
        val cassette = Cassette.empty("id", "Cassette 1", CassetteColors.default(0))
        val deck = CassetteDeck(gain = 0.5f, pan = -0.25f, muted = true, soloed = true)

        val updated = cassette.withDeck(1, deck)

        assertEquals(deck, updated.decks[1])
        assertEquals(cassette.decks[0], updated.decks[0])
        assertEquals(cassette.decks[2], updated.decks[2])
    }

    @Test
    fun libraryFallsBackToTheFirstCassetteWhenSelectionIsMissing() {
        val first = Cassette.empty("first", "First", CassetteColors.default(0))
        val second = Cassette.empty("second", "Second", CassetteColors.default(1))
        val library = CassetteLibrary(listOf(first, second), "missing")

        assertSame(first, library.selected)
    }

    @Test
    fun deckRecordingDefaultsToMicrophone() {
        val deck = CassetteDeck()

        assertEquals(CassetteRecordSource.MIC, deck.recordSource)
        assertEquals(0f, deck.busSend)
    }

    @Test
    fun recordSourceNamesRoundTrip() {
        assertEquals(
            CassetteRecordSource.DRUMS,
            CassetteRecordSource.fromName("DRUMS"),
        )
        assertEquals(
            CassetteRecordSource.MIC,
            CassetteRecordSource.fromName("unknown"),
        )
    }

    @Test
    fun sideBTrackIndexMirrorsTheDeckOrder() {
        assertEquals(3, Cassette.trackIndexForDeck(0, flipped = true))
        assertEquals(2, Cassette.trackIndexForDeck(1, flipped = true))
        assertEquals(1, Cassette.trackIndexForDeck(2, flipped = true))
        assertEquals(0, Cassette.trackIndexForDeck(3, flipped = true))
        assertEquals(0, Cassette.trackIndexForDeck(0, flipped = false))
        assertEquals(3, Cassette.trackIndexForDeck(3, flipped = false))
    }

    @Test
    fun paletteWrapsInBothDirections() {
        assertEquals(CassetteColors.palette[0], CassetteColors.default(0))
        assertEquals(CassetteColors.palette[1], CassetteColors.default(1))
        assertEquals(CassetteColors.palette.last(), CassetteColors.default(-1))
        assertEquals(CassetteColors.palette.first(), CassetteColors.default(CassetteColors.palette.size))
    }
}
