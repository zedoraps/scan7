package net.moesli.scan7.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class DebugTapCounterTest {
    @Test
    fun fiveQuickTaps_countdownAfterThird_thenOpen() {
        val c = DebugTapCounter()
        assertEquals(DebugTapResult.None, c.tap(0))
        assertEquals(DebugTapResult.None, c.tap(300))
        val third = c.tap(600)
        assertEquals(DebugTapResult.Countdown(2), third)
        assertEquals("2 more taps for debug…", (third as DebugTapResult.Countdown).message)
        assertEquals("1 more tap for debug…", (c.tap(900) as DebugTapResult.Countdown).message)
        assertEquals(DebugTapResult.Open, c.tap(1200))
        // Starts over.
        assertEquals(DebugTapResult.None, c.tap(1300))
    }

    @Test
    fun slowTaps_neverOpen() {
        val c = DebugTapCounter()
        val results = (0 until 10).map { c.tap(it * 1_000L) }
        assertEquals(false, results.contains(DebugTapResult.Open)) // at most 4 taps within 3 s
    }

    @Test
    fun oldTapsDropOutOfWindow() {
        val c = DebugTapCounter()
        c.tap(0)
        c.tap(100)
        c.tap(200)
        assertEquals(DebugTapResult.None, c.tap(3_150)) // 0 and 100 are older than 3 s → 200, 3150
        assertEquals(DebugTapResult.Countdown(2), c.tap(3_200)) // 200 is exactly 3 s old: still in
        assertEquals(DebugTapResult.Countdown(2), c.tap(3_250)) // 200 drops out → 3 taps
        assertEquals(DebugTapResult.Countdown(1), c.tap(3_300))
        assertEquals(DebugTapResult.Open, c.tap(3_350))
    }
}
