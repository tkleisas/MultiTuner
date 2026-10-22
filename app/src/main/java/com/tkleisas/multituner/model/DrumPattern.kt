package com.tkleisas.multituner.model

data class DrumPattern(val steps: List<List<Int>>) {

    init {
        require(steps.size == TRACKS) { "Expected $TRACKS tracks" }
        require(steps.all { it.size == STEPS }) { "Expected $STEPS steps per track" }
        require(steps.all { row -> row.all { it in 0..2 } }) {
            "Drum step levels must be 0, 1 or 2"
        }
    }

    fun level(track: Int, step: Int): Int =
        steps.getOrNull(track)?.getOrNull(step) ?: 0

    fun cycle(track: Int, step: Int): DrumPattern {
        if (track !in 0 until TRACKS || step !in 0 until STEPS) return this
        val updated = steps.mapIndexed { rowIndex, row ->
            if (rowIndex != track) {
                row
            } else {
                row.mapIndexed { columnIndex, value ->
                    if (columnIndex == step) (value + 1) % 3 else value
                }
            }
        }
        return DrumPattern(updated)
    }

    fun encode(): String =
        steps.flatMap { row -> row.map { it.digitToChar() } }.joinToString("")

    fun toNativeLevels(): ByteArray {
        val levels = ByteArray(TRACKS * STEPS)
        for (track in 0 until TRACKS) {
            for (step in 0 until STEPS) {
                levels[track * STEPS + step] = level(track, step).toByte()
            }
        }
        return levels
    }

    companion object {
        const val TRACKS = 8
        const val STEPS = 16

        fun empty(): DrumPattern =
            DrumPattern(List(TRACKS) { List(STEPS) { 0 } })

        fun fromRows(rows: List<String>): DrumPattern {
            require(rows.size == TRACKS) { "Expected $TRACKS drum rows" }
            val steps = rows.map { row ->
                require(row.length == STEPS) { "Expected $STEPS steps, got ${row.length}" }
                row.map { char ->
                    when (char) {
                        'x' -> 1
                        'X' -> 2
                        else -> 0
                    }
                }
            }
            return DrumPattern(steps)
        }

        fun decode(value: String?): DrumPattern? {
            if (value == null || value.length != TRACKS * STEPS) return null
            if (!value.all { it in '0'..'2' }) return null
            val steps = List(TRACKS) { track ->
                List(STEPS) { step ->
                    value[track * STEPS + step].digitToInt()
                }
            }
            return DrumPattern(steps)
        }
    }
}

data class DrumPreset(
    val id: String,
    val name: String,
    val pattern: DrumPattern,
)

object DrumPatterns {
    val rock = DrumPreset(
        id = "rock",
        name = "Rock",
        pattern = DrumPattern.fromRows(
            listOf(
                "x...x...x...x...",
                "....x.......x...",
                "................",
                "................",
                "x.x.x.x.x.x.x.x.",
                "................",
                "................",
                "................",
            ),
        ),
    )

    val funk = DrumPreset(
        id = "funk",
        name = "Funk",
        pattern = DrumPattern.fromRows(
            listOf(
                "x..x..x...x.x...",
                "....x.......x...",
                "................",
                "................",
                "x.xxx.xxx.xxx.xx",
                "..............x.",
                "................",
                "................",
            ),
        ),
    )

    val house = DrumPreset(
        id = "house",
        name = "House",
        pattern = DrumPattern.fromRows(
            listOf(
                "x...x...x...x...",
                "................",
                "....x.......x...",
                "................",
                "..x...x...x...x.",
                "................",
                "................",
                "................",
            ),
        ),
    )

    val breakbeat = DrumPreset(
        id = "break",
        name = "Break",
        pattern = DrumPattern.fromRows(
            listOf(
                "x.....x.x.......",
                "....x.......x...",
                "................",
                "................",
                "x.x.x.x.x.x.x.x.",
                "..............x.",
                "................",
                "................",
            ),
        ),
    )

    val empty = DrumPreset(
        id = "empty",
        name = "Empty",
        pattern = DrumPattern.empty(),
    )

    val all: List<DrumPreset> = listOf(rock, funk, house, breakbeat, empty)
    val default: DrumPreset = rock

    fun byId(id: String?): DrumPreset? = all.firstOrNull { it.id == id }
}
