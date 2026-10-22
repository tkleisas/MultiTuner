package com.tkleisas.multituner.model

data class CassetteAudioTrack(
    val filePath: String? = null,
    val startSample: Long = 0L,
)

data class CassetteDeck(
    val gain: Float = 0.8f,
    val pan: Float = 0f,
    val muted: Boolean = false,
    val soloed: Boolean = false,
    val reverbSend: Float = 0f,
    val delaySend: Float = 0f,
)

data class Cassette(
    val id: String,
    val name: String,
    val color: Long,
    val audioTracks: List<CassetteAudioTrack>,
    val decks: List<CassetteDeck>,
) {
    init {
        require(audioTracks.size == TRACKS) { "Expected $TRACKS audio tracks" }
        require(decks.size == TRACKS) { "Expected $TRACKS deck strips" }
    }

    fun withAudioTrack(index: Int, track: CassetteAudioTrack): Cassette {
        if (index !in 0 until TRACKS) return this
        return copy(
            audioTracks = audioTracks.mapIndexed { i, existing ->
                if (i == index) track else existing
            },
        )
    }

    fun withDeck(index: Int, deck: CassetteDeck): Cassette {
        if (index !in 0 until TRACKS) return this
        return copy(
            decks = decks.mapIndexed { i, existing ->
                if (i == index) deck else existing
            },
        )
    }

    companion object {
        const val TRACKS = 4

        fun empty(id: String, name: String, color: Long): Cassette = Cassette(
            id = id,
            name = name,
            color = color,
            audioTracks = List(TRACKS) { CassetteAudioTrack() },
            decks = List(TRACKS) { CassetteDeck() },
        )
    }
}

object CassetteColors {
    val palette: List<Long> = listOf(
        0xFFE56B6F, // coral
        0xFFF4A261, // amber
        0xFF5CE07A, // green
        0xFF4ECDC4, // teal
        0xFF6C9BD2, // blue
        0xFFB28DFF, // violet
    )

    fun default(index: Int): Long = palette[((index % palette.size) + palette.size) % palette.size]
}
