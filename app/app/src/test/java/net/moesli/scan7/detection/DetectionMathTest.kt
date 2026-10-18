package net.moesli.scan7.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionMathTest {
    private val labels = listOf("00", "01", "02", "plus2", "x2")

    private fun assertBox(expected: Box, actual: Box, eps: Float = 1e-4f) {
        assertEquals(expected.left, actual.left, eps)
        assertEquals(expected.top, actual.top, eps)
        assertEquals(expected.right, actual.right, eps)
        assertEquals(expected.bottom, actual.bottom, eps)
    }

    @Test
    fun letterboxLandscapeMatchesUltralytics() {
        // 4032×3024 → scale 640/4032, content 640×480, pad top/bottom 80.
        val lb = Letterbox.of(4032, 3024, 640)
        assertEquals(640, lb.contentWidth)
        assertEquals(480, lb.contentHeight)
        assertEquals(0, lb.padX)
        assertEquals(80, lb.padY)
    }

    @Test
    fun letterboxPortraitPadsSides() {
        val lb = Letterbox.of(960, 1280, 640)
        assertEquals(480, lb.contentWidth)
        assertEquals(640, lb.contentHeight)
        assertEquals(80, lb.padX)
        assertEquals(0, lb.padY)
    }

    @Test
    fun letterboxRoundTrip() {
        for ((w, h) in listOf(4032 to 3024, 3024 to 4032, 1280 to 960, 999 to 333, 640 to 640)) {
            val lb = Letterbox.of(w, h, 640)
            val box = Box(0.1f, 0.25f, 0.8f, 0.9f)
            val input = lb.normalizedToInput(box)
            assertBox(box, lb.inputToNormalized(input.left, input.top, input.right, input.bottom))
        }
    }

    @Test
    fun letterboxCornersMapToImageCorners() {
        val lb = Letterbox.of(1280, 960, 640)
        val full = lb.inputToNormalized(
            lb.padX.toFloat(), lb.padY.toFloat(),
            (lb.padX + lb.contentWidth).toFloat(), (lb.padY + lb.contentHeight).toFloat(),
        )
        assertBox(Box(0f, 0f, 1f, 1f), full, 1e-3f)
    }

    @Test
    fun inputToNormalizedClampsPadding() {
        val lb = Letterbox.of(1280, 960, 640) // pad 80 top/bottom
        assertBox(Box(0f, 0f, 1f, 1f), lb.inputToNormalized(-10f, 0f, 700f, 640f))
    }

    @Test
    fun uprightSizeSwapsForQuarterTurns() {
        assertEquals(1280 to 960, uprightSize(1280, 960, 0))
        assertEquals(960 to 1280, uprightSize(1280, 960, 90))
        assertEquals(1280 to 960, uprightSize(1280, 960, 180))
        assertEquals(960 to 1280, uprightSize(1280, 960, 270))
    }

    @Test
    fun bufferMatrixRotatesBeforeLetterboxing() {
        val w = 1280
        val h = 960
        // Expected upright position (in upright pixel coords) of buffer point (x, y) per rotation.
        val cases = mapOf<Int, (Float, Float) -> Pair<Float, Float>>(
            0 to { x, y -> x to y },
            90 to { x, y -> (h - y) to x },
            180 to { x, y -> (w - x) to (h - y) },
            270 to { x, y -> y to (w - x) },
        )
        for ((rotation, toUpright) in cases) {
            val (uw, uh) = uprightSize(w, h, rotation)
            val lb = Letterbox.of(uw, uh, 640)
            val m = bufferToInputMatrix(w, h, rotation, lb)
            for ((x, y) in listOf(0f to 0f, 100f to 50f, w.toFloat() to h.toFloat(), 640f to 480f)) {
                val (ux, uy) = toUpright(x, y)
                val (ix, iy) = m.mapPoint(x, y)
                assertEquals("rot $rotation x", ux * lb.scale + lb.padX, ix, 1e-3f)
                assertEquals("rot $rotation y", uy * lb.scale + lb.padY, iy, 1e-3f)
            }
            // The whole buffer lands exactly on the letterbox content rect.
            val corners = listOf(0f to 0f, w.toFloat() to 0f, 0f to h.toFloat(), w.toFloat() to h.toFloat()).map { (x, y) -> m.mapPoint(x, y) }
            assertEquals(lb.padX.toFloat(), corners.minOf { it.first }, 1e-3f)
            assertEquals(lb.padY.toFloat(), corners.minOf { it.second }, 1e-3f)
            assertEquals((lb.padX + lb.contentWidth).toFloat(), corners.maxOf { it.first }, 1f)
            assertEquals((lb.padY + lb.contentHeight).toFloat(), corners.maxOf { it.second }, 1f)
        }
    }

    @Test
    fun argbToChwSplitsPlanes() {
        val pixels = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFF727272.toInt())
        val out = FloatArray(12)
        argbToChwFloat(pixels, 2, out)
        val g = 114f / 255f
        val expected = floatArrayOf(1f, 0f, 0f, g, 0f, 1f, 0f, g, 0f, 0f, 1f, g)
        for (i in expected.indices) assertEquals("i=$i", expected[i], out[i], 1e-6f)
    }

    @Test
    fun parsesEndToEndOutput() {
        val lb = Letterbox.of(1280, 960, 640) // scale 0.5, padY 80
        val output = floatArrayOf(
            // x1, y1, x2, y2 (input px), score, class
            64f, 80f, 320f, 320f, 0.90f, 3f, // plus2 → (0.1, 0, 0.5, 0.5)
            320f, 400f, 640f, 560f, 0.50f, 1f, // 01 → (0.5, 0.666, 1, 1)
            0f, 0f, 10f, 10f, 0.20f, 0f, // below threshold
            10f, 100f, 20f, 200f, 0.99f, 42f, // unknown class id
            0f, 0f, 640f, 70f, 0.80f, 2f, // entirely inside the top padding → empty after clamping
            100f, 100f, 200f, 200f, 0.70f, 4f, // x2
        )
        val dets = parseEndToEnd(output, labels, lb, threshold = 0.35f)
        assertEquals(listOf("plus2", "x2", "01"), dets.map { it.label })
        assertEquals(listOf(0.90f, 0.70f, 0.50f), dets.map { it.score })
        assertBox(Box(0.1f, 0f, 0.5f, 0.5f), dets[0].box)
        assertBox(Box(0.5f, 320f / 480f, 1f, 1f), dets[2].box)
    }

    @Test
    fun parseIgnoresTrailingPartialRow() {
        val lb = Letterbox.of(640, 640, 640)
        val dets = parseEndToEnd(floatArrayOf(0f, 0f, 64f, 64f, 0.9f, 0f, 1f, 2f), labels, lb, 0.35f)
        assertEquals(1, dets.size)
        assertBox(Box(0f, 0f, 0.1f, 0.1f), dets[0].box)
    }

    @Test
    fun cropMappingMatchesFillCenterOnTallScreen() {
        // 3:4 upright analysis frame on a 1080×2400 (20:9) view: scale by height, crop the sides.
        val m = DisplayMapping.of(960, 1280, 1080f, 2400f, crop = true)
        assertEquals(2400f / 1280f, m.scale, 1e-5f)
        val shownWidth = 960 * m.scale // 1800
        assertEquals((1080f - shownWidth) / 2f, m.offsetX, 1e-3f)
        assertEquals(0f, m.offsetY, 1e-3f)
        // The image centre maps to the view centre; a box at the very left edge is off-screen.
        val centre = m.toView(Box(0.5f, 0.5f, 0.5f, 0.5f))
        assertEquals(540f, centre.left, 1e-3f)
        assertEquals(1200f, centre.top, 1e-3f)
        assertTrue(m.toView(Box(0f, 0f, 0.1f, 0.1f)).left < 0f)
        // Round trip view → image.
        val (nx, ny) = m.toImage(centre.left, centre.top)
        assertEquals(0.5f, nx, 1e-5f)
        assertEquals(0.5f, ny, 1e-5f)
    }

    @Test
    fun fitMappingLetterboxesPhoto() {
        val m = DisplayMapping.of(4032, 3024, 1080f, 1800f, crop = false)
        assertEquals(1080f / 4032f, m.scale, 1e-6f)
        assertEquals(0f, m.offsetX, 1e-3f)
        assertEquals((1800f - 3024 * m.scale) / 2f, m.offsetY, 1e-3f)
        val full = m.toView(Box(0f, 0f, 1f, 1f))
        assertEquals(1080f, full.right, 1e-2f)
        assertEquals(1800f - m.offsetY, full.bottom, 1e-2f)
    }
}
