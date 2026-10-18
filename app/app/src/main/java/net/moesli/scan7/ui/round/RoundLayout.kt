package net.moesli.scan7.ui.round

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import net.moesli.scan7.game.TablePosition
import net.moesli.scan7.game.clockwiseAngle
import net.moesli.scan7.ui.components.PlayerStatus
import net.moesli.scan7.ui.components.PlayerUi
import net.moesli.scan7.ui.components.isDone

/*
 * Pure layout and ordering logic of the Round screen (no Compose UI): default ellipse, pixel
 * geometry of the table area, list reorder, next open player, round ranks. JVM unit tested.
 */

/**
 * Horizontal radius of the default ellipse in normalized coordinates. The prototype's ellipse
 * (RX = 195 − W/2 − 12 on 390 wide, clamp margin 8) is ≈ 0.485 of the card's free range for every
 * size class; vertically it spans the full area (RY = 296 − H/2 → 0.5).
 */
const val DEFAULT_ELLIPSE_RX = 0.485f
const val DEFAULT_ELLIPSE_RY = 0.5f

/**
 * Default table positions for [count] players: evenly spaced on an ellipse around the area center,
 * the first seat at the top, then clockwise (prototype `defaultPos`).
 */
fun defaultTablePositions(count: Int): List<TablePosition> = List(count) { i ->
    val a = 2.0 * PI * i / count
    TablePosition(
        x = (0.5 + DEFAULT_ELLIPSE_RX * sin(a)).toFloat(),
        y = (0.5 - DEFAULT_ELLIPSE_RY * cos(a)).toFloat(),
    )
}

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

/**
 * Rank by this round's points (1 = best; equal points share a rank, next rank skipped: 1, 1, 3).
 * A bust ranks below a scored 0, open players last (same scheme as `GameState.roundRanks`).
 */
fun roundRanks(players: List<PlayerUi>): Map<String, Int> {
    val keys = players.associate { p ->
        p.id to when (val s = p.status) {
            is PlayerStatus.Scored -> s.points.toLong() * 4 + 2
            PlayerStatus.Bust -> 1L
            PlayerStatus.Open -> 0L
        }
    }
    return players.associate { p -> p.id to 1 + keys.values.count { it > keys.getValue(p.id) } }
}
