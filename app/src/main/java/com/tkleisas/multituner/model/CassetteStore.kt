package com.tkleisas.multituner.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class CassetteLibrary(
    val cassettes: List<Cassette>,
    val selectedId: String,
) {
    val selected: Cassette
        get() = cassettes.firstOrNull { it.id == selectedId } ?: cassettes.first()
}

class CassetteStore(context: Context) {

    private val appContext = context.applicationContext
    private val metadataFile = File(appContext.filesDir, "cassettes.json")
    private val audioRoot: File =
        (appContext.getExternalFilesDir(null) ?: appContext.filesDir).let {
            File(it, "cassettes")
        }

    fun load(): CassetteLibrary {
        val stored = runCatching { readLibrary() }.getOrNull()
        if (stored != null && stored.cassettes.isNotEmpty()) return stored
        val initial = Cassette.empty(
            id = UUID.randomUUID().toString(),
            name = "Cassette 1",
            color = CassetteColors.default(0),
        )
        val library = CassetteLibrary(listOf(initial), initial.id)
        save(library)
        return library
    }

    fun save(library: CassetteLibrary) {
        metadataFile.parentFile?.mkdirs()
        val root = JSONObject()
        root.put("selected", library.selectedId)
        val array = JSONArray()
        library.cassettes.forEach { cassette ->
            val objectValue = JSONObject()
            objectValue.put("id", cassette.id)
            objectValue.put("name", cassette.name)
            objectValue.put("color", cassette.color)
            val tracks = JSONArray()
            cassette.audioTracks.forEach { track ->
                val trackObject = JSONObject()
                track.filePath?.let { trackObject.put("path", it) }
                trackObject.put("start", track.startSample)
                tracks.put(trackObject)
            }
            objectValue.put("tracks", tracks)
            val decks = JSONArray()
            cassette.decks.forEach { deck ->
                val deckObject = JSONObject()
                deckObject.put("gain", deck.gain.toDouble())
                deckObject.put("pan", deck.pan.toDouble())
                deckObject.put("muted", deck.muted)
                deckObject.put("soloed", deck.soloed)
                deckObject.put("reverbSend", deck.reverbSend.toDouble())
                deckObject.put("delaySend", deck.delaySend.toDouble())
                decks.put(deckObject)
            }
            objectValue.put("decks", decks)
            array.put(objectValue)
        }
        root.put("cassettes", array)
        metadataFile.writeText(root.toString())
    }

    fun create(library: CassetteLibrary): CassetteLibrary {
        val index = library.cassettes.size + 1
        val cassette = Cassette.empty(
            id = UUID.randomUUID().toString(),
            name = "Cassette $index",
            color = CassetteColors.default(index - 1),
        )
        val updated = CassetteLibrary(library.cassettes + cassette, cassette.id)
        save(updated)
        return updated
    }

    fun delete(library: CassetteLibrary, id: String): CassetteLibrary {
        if (library.cassettes.size <= 1) return library
        library.cassettes.firstOrNull { it.id == id }?.let { cassette ->
            audioDirectory(cassette).deleteRecursively()
        }
        val remaining = library.cassettes.filterNot { it.id == id }
        val selected = if (library.selectedId == id) remaining.first().id else library.selectedId
        val updated = CassetteLibrary(remaining, selected)
        save(updated)
        return updated
    }

    fun update(library: CassetteLibrary, cassette: Cassette): CassetteLibrary {
        val updated = CassetteLibrary(
            library.cassettes.map { if (it.id == cassette.id) cassette else it },
            library.selectedId,
        )
        save(updated)
        return updated
    }

    fun select(library: CassetteLibrary, id: String): CassetteLibrary {
        if (library.cassettes.none { it.id == id }) return library
        val updated = library.copy(selectedId = id)
        save(updated)
        return updated
    }

    fun audioDirectory(cassette: Cassette): File =
        File(audioRoot, cassette.id)

    fun trackFile(cassette: Cassette, index: Int): File {
        val directory = audioDirectory(cassette)
        directory.mkdirs()
        return File(directory, "track-${index + 1}.wav")
    }

    private fun readLibrary(): CassetteLibrary {
        val root = JSONObject(metadataFile.readText())
        val array = root.getJSONArray("cassettes")
        val cassettes = ArrayList<Cassette>(array.length())
        for (i in 0 until array.length()) {
            val objectValue = array.getJSONObject(i)
            val tracksArray = objectValue.getJSONArray("tracks")
            val tracks = ArrayList<CassetteAudioTrack>(Cassette.TRACKS)
            for (t in 0 until tracksArray.length()) {
                val trackObject = tracksArray.getJSONObject(t)
                tracks.add(
                    CassetteAudioTrack(
                        filePath = if (trackObject.has("path")) {
                            trackObject.getString("path")
                        } else {
                            null
                        },
                        startSample = trackObject.optLong("start", 0L),
                    ),
                )
            }
            val decksArray = objectValue.getJSONArray("decks")
            val decks = ArrayList<CassetteDeck>(Cassette.TRACKS)
            for (d in 0 until decksArray.length()) {
                val deckObject = decksArray.getJSONObject(d)
                decks.add(
                    CassetteDeck(
                        gain = deckObject.optDouble("gain", 0.8).toFloat(),
                        pan = deckObject.optDouble("pan", 0.0).toFloat(),
                        muted = deckObject.optBoolean("muted", false),
                        soloed = deckObject.optBoolean("soloed", false),
                        reverbSend = deckObject.optDouble("reverbSend", 0.0).toFloat(),
                        delaySend = deckObject.optDouble("delaySend", 0.0).toFloat(),
                    ),
                )
            }
            cassettes.add(
                Cassette(
                    id = objectValue.getString("id"),
                    name = objectValue.getString("name"),
                    color = objectValue.optLong("color", CassetteColors.default(i)),
                    audioTracks = tracks,
                    decks = decks,
                ),
            )
        }
        val selected = root.optString("selected", cassettes.firstOrNull()?.id.orEmpty())
        return CassetteLibrary(cassettes, selected)
    }
}
