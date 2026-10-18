package net.moesli.scan7.ui.round

import androidx.compose.ui.geometry.Offset
import kotlin.math.roundToInt
import net.moesli.scan7.game.TablePosition
import net.moesli.scan7.game.clockwiseAngle
import net.moesli.scan7.ui.components.PlayerStatus
import net.moesli.scan7.ui.components.PlayerUi
import net.moesli.scan7.ui.components.isDone

/*
 * Pure layout and ordering logic of the Round screen (no Compose UI): pixel geometry of the table
 * area (default positions: `game.defaultPositions`), list reorder, next open player, round ranks. JVM unit tested.
 */

/**
 * Pixel geometry of the table area (prototype: x 8 … width − 8, y 112 … 704 of 844) for cards of
 * [cardWidth] × [cardHeight]. A normalized position maps the card's top-left linearly over the
 * range where the card stays fully inside the area, so every position in 0–1 is a valid spot.
 */
data class TableGeometry(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val cardWidth: Float,
    val cardHeight: Float,
) {
    private val rangeX: Float get() = (width - cardWidth).coerceAtLeast(0f)
    private val rangeY: Float get() = (height - cardHeight).coerceAtLeast(0f)

    /** Card top-left for a normalized [position] (clamped to 0–1). */
    fun topLeftOf(position: TablePosition): Offset {
        val p = position.clamped()
        return Offset(left + p.x * rangeX, top + p.y * rangeY)
    }

    /** Keeps a dragged card's top-left inside the area. */
    fun clampTopLeft(topLeft: Offset): Offset = Offset(
        topLeft.x.coerceIn(left, left + rangeX),
        topLeft.y.coerceIn(top, top + rangeY),
    )

    /** Normalized position (0–1) of a card whose top-left is at [topLeft] (clamped). */
    fun positionOf(topLeft: Offset): TablePosition {
        val c = clampTopLeft(topLeft)
        return TablePosition(
            x = if (rangeX > 0f) (c.x - left) / rangeX else 0.5f,
            y = if (rangeY > 0f) (c.y - top) / rangeY else 0.5f,
        ).clamped()
    }
}

/** Seating order re-derived from table positions: clockwise from 12 o'clock (stable for ties). */
fun <T> List<T>.sortedClockwiseBy(position: (T) -> TablePosition): List<T> = sortedBy { clockwiseAngle(position(it)) }

/**
 * List-view drop: the item at [from] and the item at [to] swap places (prototype `swap`).
 * Out-of-range indices are clamped; from == to returns the list unchanged.
 */
fun <T> List<T>.swapped(from: Int, to: Int): List<T> {
    if (isEmpty()) return this
    val f = from.coerceIn(indices)
    val t = to.coerceIn(indices)
    if (f == t) return this
    return toMutableList().apply {
        val tmp = this[f]
        this[f] = this[t]
        this[t] = tmp
    }
}

/** Target seat for a row dropped with its top at [offsetY] (relative to the first row), rows [pitch] apart. */
fun dropIndex(offsetY: Float, pitch: Float, count: Int): Int {
    if (count <= 0 || pitch <= 0f) return 0
    return (offsetY / pitch).roundToInt().coerceIn(0, count - 1)
}

/** Dragged row top (relative to the first row): at most 20 dp (prototype) beyond the first/last row. */
fun clampRowDrag(offsetY: Float, pitch: Float, count: Int, overshoot: Float): Float =
    offsetY.coerceIn(-overshoot, (count - 1).coerceAtLeast(0) * pitch + overshoot)

/**
 * Edge auto-scroll while a list row is dragged: pixels to scroll this frame for a row at
 * [rowTop]..[rowTop] + [rowHeight] in a viewport of [viewport] px. Inside [edge] px of the top or
 * bottom it scrolls up (negative) or down (positive), faster the deeper the row is in the edge zone,
 * at most [maxStep]; 0 elsewhere (and when the row is taller than the space between both zones).
 */
fun autoScrollStep(rowTop: Float, rowHeight: Float, viewport: Float, edge: Float, maxStep: Float): Float {
    if (edge <= 0f || viewport <= 0f) return 0f
    val intoTop = edge - rowTop
    val intoBottom = rowTop + rowHeight - (viewport - edge)
    val depth = when {
        intoTop > 0f && intoBottom > 0f -> return 0f
        intoTop > 0f -> -intoTop
        intoBottom > 0f -> intoBottom
        else -> return 0f
    }
    return (depth / edge).coerceIn(-1f, 1f) * maxStep
}

/** First open player in seating order ("Scan next" default), or null when everyone is done. */
fun firstOpenId(players: List<RoundPlayer>): String? = players.firstOrNull { !it.player.status.isDone }?.id

/**
 * Next open player after [afterId] in seating order, wrapping around (same rule as
 * `GameState.nextOpenPlayer`); [afterId] itself only if it is the sole open player.
 */
fun nextOpenIdAfter(players: List<RoundPlayer>, afterId: String?): String? {
    val start = players.indexOfFirst { it.id == afterId }
    if (start < 0) return firstOpenId(players)
    return (1..players.size)
        .map { players[(start + it) % players.size] }
        .firstOrNull { !it.player.status.isDone }
        ?.id
}
