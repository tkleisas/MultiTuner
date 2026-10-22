package com.tkleisas.multituner.model

import org.json.JSONObject
import java.io.InputStream

object TuningLibrary {

    private const val CATALOG_RESOURCE = "/tunings.json"

    private val catalog: List<Tuning> by lazy { loadFromResource() }

    val all: List<Tuning> get() = catalog

    fun byId(id: String?): Tuning =
        all.firstOrNull { it.id == id } ?: all.first()

    fun forFamily(family: InstrumentFamily): List<Tuning> =
        all.filter { it.family == family }

    internal fun parse(input: InputStream): List<Tuning> {
        val root = JSONObject(input.bufferedReader().use { it.readText() })
        val version = root.optInt("version", 1)
        require(version >= 1) { "Unsupported tuning catalog version: $version" }

        val array = root.getJSONArray("tunings")
        require(array.length() > 0) { "Tuning catalog is empty" }

        val ids = mutableSetOf<String>()
        val tunings = ArrayList<Tuning>(array.length())

        for (index in 0 until array.length()) {
            val objectValue = array.getJSONObject(index)
            val id = objectValue.getString("id")
            require(ids.add(id)) { "Duplicate tuning id: $id" }

            val familyName = objectValue.getString("family")
            val family = InstrumentFamily.entries.firstOrNull { it.name == familyName }
                ?: throw IllegalArgumentException(
                    "Unknown instrument family '$familyName' for tuning '$id'",
                )

            val coursesArray = objectValue.getJSONArray("courses")
            require(coursesArray.length() > 0) { "Tuning '$id' has no courses" }

            val courses = ArrayList<Course>(coursesArray.length())
            for (courseIndex in 0 until coursesArray.length()) {
                val courseValue = coursesArray.getJSONObject(courseIndex)
                val courseLabel = courseValue.optString("label", "")
                val stringsArray = courseValue.getJSONArray("strings")
                require(stringsArray.length() > 0) {
                    "Tuning '$id' course '$courseLabel' has no strings"
                }

                val strings = ArrayList<TuningString>(stringsArray.length())
                for (stringIndex in 0 until stringsArray.length()) {
                    val stringValue = stringsArray.getJSONObject(stringIndex)
                    val midi = stringValue.getInt("midi")
                    require(midi in 0..127) {
                        "Tuning '$id' course '$courseLabel' has invalid MIDI note $midi"
                    }
                    val centsOffset = stringValue.optInt("centsOffset", 0)
                    require(centsOffset in -1200..1200) {
                        "Tuning '$id' course '$courseLabel' has invalid cents offset $centsOffset"
                    }
                    val label = if (stringValue.has("label") && !stringValue.isNull("label")) {
                        stringValue.getString("label")
                    } else {
                        null
                    }
                    strings.add(TuningString(midi, centsOffset, label))
                }
                courses.add(Course(courseLabel, strings))
            }

            tunings.add(
                Tuning(
                    id = id,
                    name = objectValue.getString("name"),
                    family = family,
                    courses = courses,
                    description = objectValue.optString("description", ""),
                    preferFlats = objectValue.optBoolean("preferFlats", false),
                ),
            )
        }

        return tunings
    }

    private fun loadFromResource(): List<Tuning> {
        val stream = TuningLibrary::class.java.getResourceAsStream(CATALOG_RESOURCE)
            ?: error("Missing tuning catalog resource $CATALOG_RESOURCE")
        return stream.use { parse(it) }
    }
}
