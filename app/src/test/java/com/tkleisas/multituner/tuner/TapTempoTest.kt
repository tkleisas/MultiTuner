package com.tkleisas.multituner.tuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TapTempoTest {

    @Test
    fun computesBpmFromRegularTaps() {
        val tapTempo = TapTempo()
        assertNull(tapTempo.tap(0))
        assertEquals(120, tapTempo.tap(500))
        assertEquals(120, tapTempo.tap(1000))
    }

    @Test
    fun averagesRecentTaps() {
        val tapTempo = TapTempo()
        tapTempo.tap(0)
        tapTempo.tap(500)
        tapTempo.tap(1000)
        assertEquals(120, tapTempo.tap(1500))
    }

    @Test
    fun resetsAfterALongPause() {
        val tapTempo = TapTempo()
        tapTempo.tap(0)
        tapTempo.tap(500)
        assertNull(tapTempo.tap(3_000))
        assertEquals(120, tapTempo.tap(3_500))
    }

    @Test
    fun clampsFastTapsToMaximum() {
        val tapTempo = TapTempo()
        tapTempo.tap(0)
        assertEquals(300, tapTempo.tap(100))
    }

    @Test
    fun clampsSlowTapsToMinimum() {
        val tapTempo = TapTempo(resetAfterMs = 5_000)
        tapTempo.tap(0)
        assertEquals(30, tapTempo.tap(3_000))
    }

    @Test
    fun resetClearsTapHistory() {
        val tapTempo = TapTempo()
        tapTempo.tap(0)
        tapTempo.tap(500)
        tapTempo.reset()
        assertNull(tapTempo.tap(1_000))
    }
}
