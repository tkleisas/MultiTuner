package com.tkleisas.multituner.tuner

import kotlin.math.roundToInt

class TapTempo(
    private val resetAfterMs: Long = 2_000L,
    private val maxTaps: Int = 5,
) {
    private val taps = ArrayDeque<Long>()

    fun tap(nowMs: Long): Int? {
        if (taps.isNotEmpty() && nowMs - taps.last() > resetAfterMs) {
            taps.clear()
        }

        taps.addLast(nowMs)
        while (taps.size > maxTaps) {
            taps.removeFirst()
        }

        if (taps.size < 2) return null

        val averageIntervalMs = taps
            .zipWithNext { first, second -> (second - first).toDouble() }
            .average()

        if (averageIntervalMs <= 0.0) return null

        return (60_000.0 / averageIntervalMs).roundToInt().coerceIn(30, 300)
    }

    fun reset() {
        taps.clear()
    }
}
