package net.moesli.scan7.detection

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameSelectionTest {
    private val box = Box(0.1f, 0.1f, 0.3f, 0.4f)
    private fun dets(vararg scores: Float) = scores.map { Detection("7", it, box) }

    @Test
    fun scoreFormula() {
        // 0.9 + 0.4 + 0.5·1 (one ≥ 0.5) + 1.0·(50/100)
        assertEquals(2.3f, selectionScore(FrameCandidate(dets(0.9f, 0.4f), 50f), 100f), 1e-5f)
    }

    @Test
    fun moreConfidentCardsWin() {
        val frames = listOf(FrameCandidate(dets(0.9f, 0.9f), 100f), FrameCandidate(dets(0.9f, 0.9f, 0.8f), 60f))
        assertEquals(1, selectBestFrame(frames))
    }

    @Test
    fun sharpnessBreaksTies() {
        val frames = listOf(FrameCandidate(dets(0.9f), 100f), FrameCandidate(dets(0.9f), 30f))
        assertEquals(0, selectBestFrame(frames))
    }

    @Test
    fun trackingFillInsDontCount() {
        val filled = dets(0.9f) + Detection("8", 0.9f, box, fromTracking = true)
        val frames = listOf(FrameCandidate(filled, 50f), FrameCandidate(dets(0.9f), 100f))
        assertEquals(1, selectBestFrame(frames))
    }

    @Test
    fun neighbourCardsAtTheEdgeDontCount() {
        // Frame 1 has the same row plus a neighbour's card cut by the top edge: it must not win on card count.
        val row = (0 until 3).map { i -> Detection("0${i + 1}", 0.9f, Box(0.1f + i * 0.25f, 0.5f, 0.3f + i * 0.25f, 0.7f)) }
        val neighbour = Detection("12", 0.9f, Box(0.8f, 0f, 0.98f, 0.12f))
        val frames = listOf(
            rowCandidate(row, 960, 1280, 100f),
            rowCandidate(row + neighbour, 960, 1280, 90f),
        )
        assertEquals(3, frames[1].detections.size)
        assertEquals(0, selectBestFrame(frames))
    }

    @Test
    fun emptyAndLaterOnTies() {
        assertEquals(-1, selectBestFrame(emptyList()))
        assertEquals(1, selectBestFrame(listOf(FrameCandidate(dets(0.5f), 1f), FrameCandidate(dets(0.5f), 1f))))
    }

    @Test
    fun laplacianVarianceSeesEdges() {
        val n = 32
        val flat = IntArray(n * n) { 0xFF808080.toInt() }
        val checker = IntArray(n * n) { i -> if (((i % n) / 2 + (i / n) / 2) % 2 == 0) 0xFFFFFFFF.toInt() else 0xFF000000.toInt() }
        // Blurred: horizontal gradient (smooth), almost no second derivative.
        val gradient = IntArray(n * n) { i -> val v = (i % n) * 8; (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        assertEquals(0f, laplacianVariance(flat, n, 0, 0, n, n), 0f)
        assertEquals(0f, laplacianVariance(gradient, n, 0, 0, n, n), 1e-3f)
        assert(laplacianVariance(checker, n, 0, 0, n, n) > 1000f)
    }
}
