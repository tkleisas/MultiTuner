package com.tkleisas.multituner.model

import kotlin.math.abs

data class StringMatch(
    val courseIndex: Int,
    val stringIndex: Int,
    val midi: Int,
    val cents: Double,
)

object StringMatcher {
    const val COURSE_MATCH_WINDOW = 90.0

    fun closest(
        frequency: Double,
        tuning: Tuning,
        referenceA4: Double,
        lockedCourseIndex: Int? = null,
        maxCents: Double? = COURSE_MATCH_WINDOW,
    ): StringMatch? {
        if (frequency <= 0.0) return null

        val indices = lockedCourseIndex?.let { listOf(it) } ?: tuning.courses.indices
        var best: StringMatch? = null

        for (courseIndex in indices) {
            val course = tuning.courses.getOrNull(courseIndex) ?: continue
            course.strings.forEachIndexed { stringIndex, string ->
                val target = string.targetFrequency(referenceA4)
                if (target <= 0.0) return@forEachIndexed
                val cents = PitchMath.centsBetween(frequency, target)
                val current = best
                if (current == null || abs(cents) < abs(current.cents)) {
                    best = StringMatch(
                        courseIndex = courseIndex,
                        stringIndex = stringIndex,
                        midi = string.midi,
                        cents = cents,
                    )
                }
            }
        }

        return best?.takeIf { maxCents == null || abs(it.cents) <= maxCents }
    }
}
