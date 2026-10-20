package net.moesli.scan7.detection

import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row grouping on layouts modelled on measured field scans (960×1280 upright frames). Own cards
 * are 180×250 px (size √area ≈ 212 = the median M); gaps are given in units of M as measured.
 */
class RowGroupingTest {
    private val w = 960
    private val h = 1280
    private val m = sqrt(180f * 250f)

    /** A card at pixel box ([left], [top]) of [width]×[height]. */
    private fun card(label: String, left: Float, top: Float, width: Float = 180f, height: Float = 250f) =
        Detection(label, 0.9f, Box(left / w, top / h, (left + width) / w, (top + height) / h))

    /** [count] own cards in a row from x = [left] at y = [top], [gap] (× M) apart. */
    private fun row(count: Int, left: Float, top: Float, gap: Float, labels: List<String> = listOf("01", "03", "05", "07", "08", "10")) =
        (0 until count).map { i -> card(labels[i % labels.size], left + i * (180f + gap * m), top) }

    @Test
    fun neighbourNineAndFreezeCutAtTopRightAreLeftOut() {
        // 9 (0.79 M) + Freeze (0.84 M) cut by the top-right corner, 1.77 M above the row.
        val own = row(4, 60f, 0f, gap = 0.2f).map { it.shift(dy = 211f + 1.77f * m) }
        val nine = card("09", 640f, 0f, 140f, 0.79f * m * 0.79f * m / 140f)
        val freeze = card("freeze", 810f, 0f, 150f, 0.84f * m * 0.84f * m / 150f)
        val dets = own + nine + freeze
        val r = groupRow(dets, w, h)
        assertEquals(setOf(4, 5), r.leftOut)
        assertEquals(listOf(0, 1, 2, 3), r.groups[r.main!!])
    }

    @Test
    fun lonelyNeighbourTwelveAtTopRightIsLeftOut() {
        // A neighbour's 12 (0.73 M) in the corner would give a false bust with the own 12.
        val size = 0.73f * m
        for (gap in listOf(1.45f, 2.0f, 2.44f)) {
            val own = row(4, 60f, size + gap * m, gap = 0.25f, labels = listOf("12", "03", "05", "07"))
            val twelve = card("12", 960f - size, 0f, size, size)
            assertEquals("gap $gap", setOf(4), rowLeftOut(own + twelve, w, h))
        }
    }

    @Test
    fun neighbourTwelveAndSecondChanceAtTopLeftAreLeftOutAtTheEdge() {
        // Only 0.93 M from the row (not "far"), but cut by the image border.
        val own = row(4, 120f, 250f + 0.93f * m, gap = 0.2f)
        val twelve = card("12", 0f, 0f, 150f, 250f)
        val second = card("second_chance", 150f + 0.1f * m, 0f, 160f, 250f)
        assertEquals(setOf(4, 5), rowLeftOut(own + twelve + second, w, h))
    }

    @Test
    fun spreadOutOwnRowIsOneGroup() {
        val own = row(5, 20f, 500f, gap = 0.34f).take(4) + row(5, 20f, 820f, gap = 0.34f).take(1)
        // Second line 70 px (0.33 M) below the first: still linked.
        val r = groupRow(own, w, h)
        assertEquals(1, r.groups.size)
        assertTrue(r.leftOut.isEmpty())
    }

    @Test
    fun deskRowWithWideGapAndNoEdgeContactIsKept() {
        // Two groups 0.93 M apart, neither touching the border: kept (not far, not cut off).
        val left = row(2, 40f, 500f, gap = 0.1f)
        val right = listOf(card("10", 40f + 2 * 180f + 0.1f * m + 0.93f * m, 500f))
        val r = groupRow(left + right, w, h)
        assertEquals(2, r.groups.size)
        assertTrue(r.leftOut.isEmpty())
    }

    @Test
    fun ownCardsAtTheBorderInTheMainGroupAreKept() {
        // Row from the left to the right edge; a far, cut-off neighbour card is still left out.
        val own = row(4, 0f, 600f, gap = 0.05f) + card("10", 780f, 600f)
        assertTrue(own.first().box.left <= 0f && own.last().box.right >= 0.99f)
        val neighbour = card("06", 400f, 0f, 150f, 160f)
        assertEquals(setOf(5), rowLeftOut(own + neighbour, w, h))
        assertTrue(rowLeftOut(own, w, h).isEmpty())
    }

    @Test
    fun ownCardsOfVeryDifferentSizesStayInTheRow() {
        // 0.6 … 1.9 × the median size, small gaps: one group.
        val sizes = listOf(0.6f, 0.9f, 1.0f, 1.3f, 1.9f)
        var x = 10f
        val dets = sizes.map { s ->
            // Same area as an s·M square, narrower (portrait cards side by side).
            val width = s * m * 0.6f
            val height = s * m / 0.6f
            card("0${sizes.indexOf(s) + 1}", x, 800f - height, width, height).also { x += width + 0.15f * m }
        }
        assertTrue(rowLeftOut(dets, w, h).isEmpty())
        assertEquals(1, groupRow(dets, w, h).groups.size)
    }

    @Test
    fun overlappingRowsAreOneGroup() {
        val top = row(3, 100f, 300f, gap = 0.1f)
        val bottom = row(3, 140f, 300f + 230f, gap = 0.1f) // overlaps the row above by 20 px: gap 0
        val r = groupRow(top + bottom, w, h)
        assertEquals(1, r.groups.size)
        assertTrue(r.leftOut.isEmpty())
    }

    @Test
    fun fewDetectionsOrTiesLeaveEverythingIn() {
        // Two cards far apart, one at the border: < 3 detections.
        assertTrue(rowLeftOut(listOf(card("01", 300f, 600f), card("02", 0f, 0f)), w, h).isEmpty())
        // Two equal groups mirrored around the centre, both at the border: no main group.
        val a = row(2, 0f, 500f, gap = 0.1f)
        val b = row(2, 960f - 2 * 180f - 0.1f * m, 500f, gap = 0.1f)
        val r = groupRow(a + b, w, h)
        assertEquals(null, r.main)
        assertTrue(r.leftOut.isEmpty())
    }

    @Test
    fun tieOnCountIsBrokenByArea() {
        // Two pairs: the bigger cards are the scanned row (nearer the camera); the small pair at the edge goes.
        val own = row(2, 300f, 600f, gap = 0.1f)
        val small = listOf(card("09", 0f, 100f, 120f, 160f), card("11", 130f, 100f, 120f, 160f))
        assertEquals(setOf(2, 3), rowLeftOut(own + small, w, h))
    }

    @Test
    fun gapIsEdgeToEdge() {
        assertEquals(0f, boxGap(Box(0f, 0f, 10f, 10f), Box(5f, 5f, 20f, 20f)), 0f)
        assertEquals(5f, boxGap(Box(0f, 0f, 10f, 10f), Box(13f, 14f, 20f, 20f)), 1e-5f)
    }

    private fun Detection.shift(dy: Float) = copy(box = Box(box.left, box.top + dy / h, box.right, box.bottom + dy / h))
}
