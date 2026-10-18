package net.moesli.scan7.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FpsCounterTest {
    @Test
    fun reportsFramesPerSecondOncePerWindow() {
        val counter = FpsCounter()
        assertNull(counter.onFrame(0))
        for (i in 1 until 30) assertNull(counter.onFrame(i * 1_000_000_000L / 30))
        assertEquals(30f, counter.onFrame(1_000_000_000L)!!, 0.01f)
    }

    @Test
    fun startsANewWindowAfterReporting() {
        val counter = FpsCounter(windowNanos = 100)
        counter.onFrame(0)
        assertEquals(1e7f, counter.onFrame(100)!!, 1e3f)
        assertNull(counter.onFrame(150))
        assertEquals(2e7f, counter.onFrame(200)!!, 1e3f)
    }
}
