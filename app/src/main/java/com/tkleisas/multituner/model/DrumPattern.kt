package com.tkleisas.multituner.model

data class DrumPattern(
    val steps: List<List<Int>>,
    val lengths: List<Int>,
    val independentLengths: Boolean = false,
    val commonLength: Int = DEFAULT_STEPS,
) {

    init {
        require(steps.size == TRACKS) { "Expected $TRACKS tracks" }
        require(steps.all { it.size == MAX_STEPS }) { "Expected $MAX_STEPS steps per track" }
        require(lengths.size == TRACKS) { "Expected $TRACKS track lengths" }
        require(lengths.all { it in 1..MAX_STEPS }) { "Track lengths must be 1..$MAX_STEPS" }
        require(commonLength in 1..MAX_STEPS) {
            "Common length must be 1..$MAX_STEPS"
        }
        require(steps.all { row -> row.all { it in 0..2 } }) {
            "Drum step levels must be 0, 1 or 2"
        }
    }

    fun length(track: Int): Int =
        if (independentLengths) lengths.getOrNull(track) ?: 0 else commonLength

    fun effectiveLengths(): List<Int> = List(TRACKS) { length(it) }

    fun level(track: Int, step: Int): Int {
        if (step !in 0 until length(track)) return 0
        return steps.getOrNull(track)?.getOrNull(step) ?: 0
    }

    fun cycle(track: Int, step: Int): DrumPattern {
        if (track !in 0 until TRACKS || step !in 0 until length(track)) return this
        val updated = steps.mapIndexed { rowIndex, row ->
            if (rowIndex != track) {
                row
            } else {
                row.mapIndexed { columnIndex, value ->
                    if (columnIndex == step) (value + 1) % 3 else value
                }
            }
        }
        return copy(steps = updated)
    }

    fun withTrackLength(track: Int, length: Int): DrumPattern {
        if (track !in 0 until TRACKS) return this
        val clamped = length.coerceIn(1, MAX_STEPS)
        if (independentLengths && lengths[track] == clamped) return this
        return copy(
            independentLengths = true,
            lengths = lengths.mapIndexed { index, current ->
                if (index == track) clamped else current
            },
        )
    }

    fun withCommonLength(length: Int): DrumPattern {
        val clamped = length.coerceIn(1, MAX_STEPS)
        if (!independentLengths && commonLength == clamped) return this
        return copy(independentLengths = false, commonLength = clamped)
    }

    fun withIndependentLengths(enabled: Boolean): DrumPattern {
        if (independentLengths == enabled) return this
        if (!enabled) return copy(independentLengths = false)
        val nextLengths = if (lengths.distinct().size == 1) {
            List(TRACKS) { commonLength }
        } else {
            lengths
        }
        return copy(independentLengths = true, lengths = nextLengths)
    }

    fun encode(): String {
        val data = steps.joinToString("") { row -> row.joinToString("") }
        return "v3;${if (independentLengths) 1 else 0};" +
            "$commonLength;${lengths.joinToString(",")};$data"
    }

    fun toNativeLevels(): ByteArray {
        val levels = ByteArray(TRACKS * MAX_STEPS)
        for (track in 0 until TRACKS) {
            for (step in 0 until MAX_STEPS) {
                levels[track * MAX_STEPS + step] = level(track, step).toByte()
            }
        }
        return levels
    }

    fun toNativeLengths(): ByteArray =
        ByteArray(TRACKS) { lengths[it].toByte() }

    companion object {
        const val TRACKS = 8
        const val DEFAULT_STEPS = 16
        const val MAX_STEPS = 32

        fun empty(): DrumPattern = DrumPattern(
            steps = List(TRACKS) { List(MAX_STEPS) { 0 } },
            lengths = List(TRACKS) { DEFAULT_STEPS },
            independentLengths = false,
            commonLength = DEFAULT_STEPS,
        )

        fun fromRows(rows: List<String>): DrumPattern {
            require(rows.size == TRACKS) { "Expected $TRACKS drum rows" }
            val lengths = rows.map { row ->
                require(row.length in 1..MAX_STEPS) {
                    "Expected 1..$MAX_STEPS steps, got ${row.length}"
                }
                row.length
            }
            val steps = rows.map { row ->
                val values = row.map { char ->
                    when (char) {
                        'x' -> 1
                        'X' -> 2
                        else -> 0
                    }
                }
                values + List(MAX_STEPS - values.size) { 0 }
            }
            val independent = lengths.distinct().size > 1
            return DrumPattern(
                steps = steps,
                lengths = lengths,
                independentLengths = independent,
                commonLength = lengths.firstOrNull() ?: DEFAULT_STEPS,
            )
        }

        fun decode(value: String?): DrumPattern? {
            if (value == null) return null
            if (value.startsWith("v3;")) return decodeV3(value)
            if (value.startsWith("v2;")) return decodeV2(value)
            if (value.length != TRACKS * DEFAULT_STEPS) return null
            if (!value.all { it in '0'..'2' }) return null
            val steps = List(TRACKS) { track ->
                val row = List(MAX_STEPS) { 0 }.toMutableList()
                for (step in 0 until DEFAULT_STEPS) {
                    row[step] = value[track * DEFAULT_STEPS + step].digitToInt()
                }
                row
            }
            return DrumPattern(
                steps = steps,
                lengths = List(TRACKS) { DEFAULT_STEPS },
            )
        }

        private fun parseSteps(data: String): List<List<Int>>? {
            if (data.length != TRACKS * MAX_STEPS) return null
            if (!data.all { it in '0'..'2' }) return null
            return List(TRACKS) { track ->
                List(MAX_STEPS) { step ->
                    data[track * MAX_STEPS + step].digitToInt()
                }
            }
        }

        private fun decodeV3(value: String): DrumPattern? {
            val parts = value.split(';', limit = 5)
            if (parts.size != 5) return null
            val independent = when (parts[1]) {
                "1" -> true
                "0" -> false
                else -> return null
            }
            val commonLength = parts[2].toIntOrNull() ?: return null
            if (commonLength !in 1..MAX_STEPS) return null
            val lengths = parts[3].split(',').map { it.toIntOrNull() ?: return null }
            if (lengths.size != TRACKS || lengths.any { it !in 1..MAX_STEPS }) return null
            val steps = parseSteps(parts[4]) ?: return null
            return DrumPattern(
                steps = steps,
                lengths = lengths,
                independentLengths = independent,
                commonLength = commonLength,
            )
        }

        private fun decodeV2(value: String): DrumPattern? {
            val parts = value.split(';', limit = 3)
            if (parts.size != 3) return null
            val lengths = parts[1].split(',').map { it.toIntOrNull() ?: return null }
            if (lengths.size != TRACKS || lengths.any { it !in 1..MAX_STEPS }) return null
            val steps = parseSteps(parts[2]) ?: return null
            val independent = lengths.distinct().size > 1
            return DrumPattern(
                steps = steps,
                lengths = lengths,
                independentLengths = independent,
                commonLength = lengths.first(),
            )
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
