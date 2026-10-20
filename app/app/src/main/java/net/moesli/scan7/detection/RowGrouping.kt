package net.moesli.scan7.detection

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/*
 * Row grouping, plain Kotlin (JVM tests in RowGroupingTest): which detections of a photo belong to
 * the scanned player's row, and which are a neighbour's cards at the edge of the frame. Field data
 * (120 scans) showed that size cutoffs and border margins remove the player's own cards; grouping
 * the cards by the gaps between them does not.
 */

/** Two cards are in the same group when the gap between their boxes is at most this × the median card size. */
const val ROW_LINK_GAP = 0.4f

/** A non-main group further than this × the median card size from the main group is left out. */
const val ROW_FAR_GAP = 1.0f

/** A box edge within this fraction of the image width / height touches the border (the card is cut off). */
const val ROW_BORDER_MARGIN = 0.01f

/** Fewer detections than this are never grouped (nothing is left out). */
const val ROW_MIN_DETECTIONS = 3

/**
 * Result of [groupRow]: [groups] (detection indices, connected by gaps ≤ [ROW_LINK_GAP] × median
 * size), the [main] group's index in [groups] (null on a tie that can't be broken), and the
 * detection indices [leftOut] (whole non-main groups at the border or far from the main group).
 */
data class RowGroups(val groups: List<List<Int>>, val main: Int?, val leftOut: Set<Int>)

/**
 * Groups the [detections] (normalized boxes) of an [imageWidth]×[imageHeight] photo into rows:
 *
 * 1. boxes in pixels; card size = √(box area), [median] over the photo;
 * 2. two cards are linked when the edge-to-edge gap of their boxes (0 when they overlap) is
 *    ≤ [ROW_LINK_GAP] × median; groups = connected cards;
 * 3. the main group has the most cards; ties → larger total area, then nearer the image centre;
 *    still tied → no main group, nothing is left out;
 * 4. a non-main group is left out when one of its cards touches the image border
 *    ([ROW_BORDER_MARGIN]) or the group is more than [ROW_FAR_GAP] × median away from the main group.
 *
 * With fewer than [ROW_MIN_DETECTIONS] detections or a single group nothing is left out. Only
 * whole non-main groups are ever left out, never a card of the main group.
 */
fun groupRow(detections: List<Detection>, imageWidth: Int, imageHeight: Int): RowGroups {
    val n = detections.size
    if (n == 0 || imageWidth <= 0 || imageHeight <= 0) return RowGroups(emptyList(), null, emptySet())
    val w = imageWidth.toFloat()
    val h = imageHeight.toFloat()
    val px = detections.map { d -> Box(d.box.left * w, d.box.top * h, d.box.right * w, d.box.bottom * h) }
    val sizes = px.map { b -> sqrt((b.width * b.height).coerceAtLeast(0f)) }
    val median = median(sizes)
    val linkGap = ROW_LINK_GAP * median

    // Union-find over the links.
    val parent = IntArray(n) { it }
    fun find(i: Int): Int {
        var r = i
        while (parent[r] != r) r = parent[r]
        var c = i
        while (parent[c] != r) c = parent[c].also { parent[c] = r }
        return r
    }
    for (i in 0 until n) {
        for (j in i + 1 until n) {
            if (boxGap(px[i], px[j]) <= linkGap) parent[find(i)] = find(j)
        }
    }
    val groups = (0 until n).groupBy { find(it) }.values.map { it.sorted() }.sortedBy { it.first() }
    if (n < ROW_MIN_DETECTIONS || groups.size < 2) return RowGroups(groups, if (groups.size == 1) 0 else null, emptySet())

    val main = mainGroup(groups, px, w, h) ?: return RowGroups(groups, null, emptySet())
    val margin = ROW_BORDER_MARGIN
    val leftOut = HashSet<Int>()
    groups.forEachIndexed { g, members ->
        if (g == main) return@forEachIndexed
        val atBorder = members.any { i -> touchesBorder(detections[i].box, margin) }
        val distance = members.minOf { i -> groups[main].minOf { m -> boxGap(px[i], px[m]) } }
        if (atBorder || distance > ROW_FAR_GAP * median) leftOut.addAll(members)
    }
    return RowGroups(groups, main, leftOut)
}

/** Detection indices [groupRow] leaves out (a neighbour's cards); empty when unsure. */
fun rowLeftOut(detections: List<Detection>, imageWidth: Int, imageHeight: Int): Set<Int> =
    groupRow(detections, imageWidth, imageHeight).leftOut

/** The [detections] without the ones [groupRow] leaves out, in their order. */
fun rowKept(detections: List<Detection>, imageWidth: Int, imageHeight: Int): List<Detection> {
    val out = rowLeftOut(detections, imageWidth, imageHeight)
    return if (out.isEmpty()) detections else detections.filterIndexed { i, _ -> i !in out }
}

/** Edge-to-edge distance of two boxes (0 when they touch or overlap). */
internal fun boxGap(a: Box, b: Box): Float {
    val dx = maxOf(0f, maxOf(a.left, b.left) - minOf(a.right, b.right))
    val dy = maxOf(0f, maxOf(a.top, b.top) - minOf(a.bottom, b.bottom))
    return hypot(dx, dy)
}

/** Normalized [box] within [margin] of an image edge. */
internal fun touchesBorder(box: Box, margin: Float): Boolean =
    box.left <= margin || box.top <= margin || box.right >= 1f - margin || box.bottom >= 1f - margin

private fun median(values: List<Float>): Float {
    val s = values.sorted()
    val mid = s.size / 2
    return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2f
}

/** Relative tolerance for "tied" areas / distances (float noise). */
private const val TIE_EPS = 1e-4f

/** Index of the main group: most cards, then larger total area, then nearer the centre; null if still tied. */
private fun mainGroup(groups: List<List<Int>>, px: List<Box>, w: Float, h: Float): Int? {
    val most = groups.maxOf { it.size }
    var candidates = groups.indices.filter { groups[it].size == most }
    if (candidates.size == 1) return candidates.single()

    val area = candidates.associateWith { g -> groups[g].sumOf { i -> (px[i].width * px[i].height).toDouble() }.toFloat() }
    val maxArea = area.values.max()
    candidates = candidates.filter { g -> nearlyEqual(area.getValue(g), maxArea) }
    if (candidates.size == 1) return candidates.single()

    val dist = candidates.associateWith { g ->
        val members = groups[g]
        val cx = (members.minOf { px[it].left } + members.maxOf { px[it].right }) / 2f
        val cy = (members.minOf { px[it].top } + members.maxOf { px[it].bottom }) / 2f
        hypot(cx - w / 2f, cy - h / 2f)
    }
    val minDist = dist.values.min()
    candidates = candidates.filter { g -> nearlyEqual(dist.getValue(g), minDist, scale = hypot(w, h)) }
    return candidates.singleOrNull()
}

private fun nearlyEqual(a: Float, b: Float, scale: Float = maxOf(abs(a), abs(b))): Boolean =
    abs(a - b) <= TIE_EPS * maxOf(scale, 1f)
