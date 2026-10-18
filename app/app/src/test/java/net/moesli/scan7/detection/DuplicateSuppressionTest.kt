package net.moesli.scan7.detection

import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicateSuppressionTest {
    private fun det(label: String, score: Float, l: Float, t: Float, r: Float, b: Float) =
        Detection(label, score, Box(l, t, r, b))

    @Test
    fun sameCardTwiceKeepsHigherScore() {
        val dets = listOf(det("12", 0.99f, 0.10f, 0.10f, 0.30f, 0.40f), det("12", 0.55f, 0.11f, 0.10f, 0.31f, 0.41f))
        assertEquals(listOf(dets[0]), suppressDuplicates(dets))
    }

    @Test
    fun differentClassOnSameCardKeepsHigherScore() {
        val dets = listOf(det("plus8", 0.80f, 0.1f, 0.1f, 0.3f, 0.4f), det("plus6", 0.60f, 0.1f, 0.1f, 0.3f, 0.4f))
        assertEquals(listOf(dets[0]), suppressDuplicates(dets))
    }

    @Test
    fun slightlyOverlappingNeighboursAreKept() {
        // Two cards side by side with ~25 % overlap of their width: IoU ≈ 0.14.
        val dets = listOf(det("07", 0.9f, 0.10f, 0.1f, 0.30f, 0.4f), det("07", 0.8f, 0.25f, 0.1f, 0.45f, 0.4f))
        assertEquals(2, suppressDuplicates(dets).size)
    }

    @Test
    fun iouBasics() {
        val a = Box(0f, 0f, 1f, 1f)
        assertEquals(1f, iou(a, a), 1e-6f)
        assertEquals(0f, iou(a, Box(2f, 2f, 3f, 3f)), 1e-6f)
        assertEquals(1f / 3f, iou(a, Box(0.5f, 0f, 1.5f, 1f)), 1e-6f)
    }
}
