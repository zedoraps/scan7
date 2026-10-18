package net.moesli.scan7.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomPresetsTest {
    @Test
    fun ultraWideMainAndTele() {
        assertEquals(listOf(0.5f, 1f, 2f, 5f), zoomPresets(0.5f, 30f))
    }

    @Test
    fun noUltraWide() {
        assertEquals(listOf(1f, 2f, 5f), zoomPresets(1f, 10f))
    }

    @Test
    fun smallZoomRange() {
        assertEquals(listOf(1f, 2f), zoomPresets(1f, 4f))
    }

    @Test
    fun formatting() {
        assertEquals("0.5×", formatZoom(0.5f))
        assertEquals("2×", formatZoom(2f))
    }
}
