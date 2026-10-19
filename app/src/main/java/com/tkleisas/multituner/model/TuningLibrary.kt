package com.tkleisas.multituner.model

object TuningLibrary {

    private fun single(label: String, midi: Int, cents: Int = 0) =
        Course(label, listOf(TuningString(midi, cents)))

    private fun course(label: String, vararg midi: Int) =
        Course(label, midi.map { TuningString(it) })

    private fun guitar(
        id: String,
        name: String,
        notes: List<Int>,
        preferFlats: Boolean = false,
        description: String = "",
    ) = Tuning(
        id = id,
        name = name,
        family = InstrumentFamily.GUITAR,
        courses = notes.mapIndexed { index, midi ->
            single((notes.size - index).toString(), midi)
        },
        description = description,
        preferFlats = preferFlats,
    )

    private val guitarTunings = listOf(
        guitar(
            "guitar_standard",
            "Standard E",
            listOf(40, 45, 50, 55, 59, 64),
            description = "E2 · A2 · D3 · G3 · B3 · E4",
        ),
        guitar(
            "guitar_drop_d",
            "Drop D",
            listOf(38, 45, 50, 55, 59, 64),
            description = "D2 · A2 · D3 · G3 · B3 · E4",
        ),
        guitar(
            "guitar_eb",
            "Eb Standard",
            listOf(39, 44, 49, 54, 58, 63),
            preferFlats = true,
            description = "Eb2 · Ab2 · Db3 · Gb3 · Bb3 · Eb4",
        ),
        guitar(
            "guitar_d_standard",
            "D Standard",
            listOf(38, 43, 48, 53, 57, 62),
            description = "D2 · G2 · C3 · F3 · A3 · D4",
        ),
        guitar(
            "guitar_drop_c",
            "Drop C",
            listOf(36, 43, 48, 53, 57, 62),
            description = "C2 · G2 · C3 · F3 · A3 · D4",
        ),
        guitar(
            "guitar_c_standard",
            "C Standard",
            listOf(36, 41, 46, 51, 55, 60),
            description = "C2 · F2 · Bb2 · Eb3 · G3 · C4",
        ),
        guitar(
            "guitar_dadgad",
            "DADGAD",
            listOf(38, 45, 50, 55, 57, 62),
            description = "D2 · A2 · D3 · G3 · A3 · D4",
        ),
        guitar(
            "guitar_open_g",
            "Open G",
            listOf(38, 43, 50, 55, 59, 62),
            description = "D2 · G2 · D3 · G3 · B3 · D4",
        ),
        guitar(
            "guitar_open_d",
            "Open D",
            listOf(38, 45, 50, 54, 57, 62),
            description = "D2 · A2 · D3 · F#3 · A3 · D4",
        ),
        guitar(
            "guitar_open_e",
            "Open E",
            listOf(40, 47, 52, 56, 59, 64),
            description = "E2 · B2 · E3 · G#3 · B3 · E4",
        ),
        guitar(
            "guitar_baritone",
            "Baritone B",
            listOf(35, 40, 45, 50, 54, 59),
            description = "B1 · E2 · A2 · D3 · F#3 · B3",
        ),
        guitar(
            "guitar_7_standard",
            "7-String Standard",
            listOf(35, 40, 45, 50, 55, 59, 64),
            description = "B1 · E2 · A2 · D3 · G3 · B3 · E4",
        ),
    )

    private val bouzoukiTunings = listOf(
        Tuning(
            id = "bouzouki_cfad",
            name = "Tetrachordo C-F-A-D",
            family = InstrumentFamily.BOUZOUKI,
            courses = listOf(
                course("C", 48, 60),
                course("F", 53, 65),
                course("A", 57),
                course("D", 62),
            ),
            description = "Greek 4-course. Octave pairs on C and F.",
        ),
        Tuning(
            id = "bouzouki_dgbe",
            name = "Tetrachordo D-G-B-E",
            family = InstrumentFamily.BOUZOUKI,
            courses = listOf(
                course("D", 50),
                course("G", 55),
                course("B", 59),
                course("E", 64),
            ),
            description = "Guitar-like 4-course tuning.",
        ),
        Tuning(
            id = "bouzouki_dad",
            name = "Trichordo D-A-D",
            family = InstrumentFamily.BOUZOUKI,
            courses = listOf(
                course("D", 50, 62),
                course("A", 57),
                course("D", 62),
            ),
            description = "Classic 3-course rebetiko tuning. Octave pair on low D.",
        ),
        Tuning(
            id = "bouzouki_dgd",
            name = "Trichordo D-G-D",
            family = InstrumentFamily.BOUZOUKI,
            courses = listOf(
                course("D", 50, 62),
                course("G", 55),
                course("D", 62),
            ),
            description = "Isio / tis psihis tuning.",
        ),
    )

    private val sazTunings = listOf(
        Tuning(
            id = "saz_bozuk_low",
            name = "Bozuk (Kara) — low A",
            family = InstrumentFamily.SAZ,
            courses = listOf(
                course("Alt", 45),
                course("Orta", 50),
                course("Üst", 55),
            ),
            description = "A2 · D3 · G3. Common long-neck saz tuning.",
        ),
        Tuning(
            id = "saz_bozuk_high",
            name = "Bozuk (Kara) — high A",
            family = InstrumentFamily.SAZ,
            courses = listOf(
                course("Alt", 57),
                course("Orta", 50),
                course("Üst", 55),
            ),
            description = "A3 · D3 · G3. Re-entrant variant.",
        ),
        Tuning(
            id = "saz_baglama_low",
            name = "Bağlama — low A",
            family = InstrumentFamily.SAZ,
            courses = listOf(
                course("Alt", 45),
                course("Orta", 50),
                course("Üst", 52),
            ),
            description = "A2 · D3 · E3. Common short-neck bağlama tuning.",
        ),
        Tuning(
            id = "saz_baglama_high",
            name = "Bağlama — high A",
            family = InstrumentFamily.SAZ,
            courses = listOf(
                course("Alt", 57),
                course("Orta", 50),
                course("Üst", 52),
            ),
            description = "A3 · D3 · E3. Re-entrant variant.",
        ),
    )

    private val tambourasTunings = listOf(
        Tuning(
            id = "tambouras_dgc",
            name = "Tambouras D-G-C",
            family = InstrumentFamily.TAMBOURAS,
            courses = listOf(
                course("D", 50),
                course("G", 55),
                course("C", 60),
            ),
            description = "Standard Greek tambouras tuning: D3 · G3 · C4.",
        ),
        Tuning(
            id = "tambouras_dad",
            name = "Tambouras D-A-D",
            family = InstrumentFamily.TAMBOURAS,
            courses = listOf(
                course("D", 50, 62),
                course("A", 57),
                course("D", 62),
            ),
            description = "Three-course Greek tambouras.",
        ),
        Tuning(
            id = "tambouras_da",
            name = "Tambouras D-A",
            family = InstrumentFamily.TAMBOURAS,
            courses = listOf(
                course("D", 50),
                course("A", 57),
            ),
            description = "Two-string tambouras.",
        ),
    )

    val all: List<Tuning> =
        guitarTunings + bouzoukiTunings + sazTunings + tambourasTunings

    fun byId(id: String?): Tuning =
        all.firstOrNull { it.id == id } ?: all.first()

    fun forFamily(family: InstrumentFamily): List<Tuning> =
        all.filter { it.family == family }
}
