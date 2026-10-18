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
    fun pillsAboveTheMaxZoomAreHidden() {
        assertEquals(listOf(1f, 2f), zoomPresets(1f, 4.9f))
        assertEquals(listOf(1f), zoomPresets(1f, 1.8f)) // one pill: the caller hides the row
    }

    @Test
    fun noPillsWithoutRealZoom() {
        assertEquals(emptyList<Float>(), zoomPresets(1f, 1f))
        assertEquals(emptyList<Float>(), zoomPresets(1f, 1.4f))
        // An ultra-wide alone doesn't bring the row back below 1.5× max.
        assertEquals(emptyList<Float>(), zoomPresets(0.5f, 1.2f))
    }

    @Test
    fun ultraWideOnlyWhenReallyWide() {
        assertEquals(listOf(0.6f, 1f, 2f), zoomPresets(0.6f, 4f))
        assertEquals(listOf(1f, 2f), zoomPresets(0.8f, 4f))
    }

    @Test
    fun unsupportedZoomFallsBackToOne() {
        assertEquals(5f, supportedZoom(5f, 0.5f, 10f), 0f)
        assertEquals(1f, supportedZoom(5f, 1f, 4f), 0f)
        assertEquals(1f, supportedZoom(0.5f, 1f, 4f), 0f)
        assertEquals(1.2f, supportedZoom(5f, 1.2f, 3f), 0f) // 1× itself unsupported: nearest
    }

    @Test
    fun formatting() {
        assertEquals("0.5×", formatZoom(0.5f))
        assertEquals("2×", formatZoom(2f))
    }

    @Test
    fun pinchMultipliesAndClamps() {
        assertEquals(3f, pinchZoom(2f, 1.5f, 0.5f, 10f), 1e-4f)
        assertEquals(10f, pinchZoom(8f, 2f, 0.5f, 10f), 1e-4f)
        assertEquals(0.5f, pinchZoom(1f, 0.1f, 0.5f, 10f), 1e-4f)
    }

    @Test
    fun activePresetFollowsTheZoom() {
        val presets = listOf(0.5f, 1f, 2f, 5f)
        assertEquals(1f, activePreset(presets, 1f))
        assertEquals(1f, activePreset(presets, 1.6f))
        assertEquals(2f, activePreset(presets, 1.995f)) // float noise
        assertEquals(5f, activePreset(presets, 7.3f))
        assertEquals(0.5f, activePreset(presets, 0.4f))
        assertEquals(null, activePreset(emptyList(), 1f))
    }

    @Test
    fun zoomLabelRoundsToOneDecimal() {
        assertEquals("1.6×", zoomLabel(1.63f))
        assertEquals("2×", zoomLabel(1.99f))
    }
}
