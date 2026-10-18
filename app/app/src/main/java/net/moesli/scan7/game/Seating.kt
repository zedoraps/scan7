package net.moesli.scan7.game

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Horizontal radius of the default ellipse in normalized table coordinates (center 0.5, 0.5).
 * The prototype's ellipse (RX = 195 − W/2 − 12 on 390 wide, clamp margin 8) is ≈ 0.485 of the
 * card's free range for every size class; vertically it spans the full area (RY = 296 − H/2 → 0.5).
 */
const val DEFAULT_ELLIPSE_RX = 0.485f

/** Vertical radius of the default ellipse (see [DEFAULT_ELLIPSE_RX]). */
const val DEFAULT_ELLIPSE_RY = 0.5f

/**
 * Default card positions for [count] players (the single source of truth for new games, missing
 * stored positions and the demo screens): evenly spaced on the prototype's ellipse around the area
 * center, the first seat at the top, then clockwise (prototype `defaultPos`).
 */
fun defaultPositions(count: Int): List<TablePosition> = List(count) { i ->
    val a = 2.0 * PI * i / count
    TablePosition(
        x = (0.5 + DEFAULT_ELLIPSE_RX * sin(a)).toFloat(),
        y = (0.5 - DEFAULT_ELLIPSE_RY * cos(a)).toFloat(),
    )
}

/** Angle of [p] around the table center, clockwise from 12 o'clock, in [0, 2π). y grows downwards. */
fun clockwiseAngle(p: TablePosition): Double {
    val a = atan2((p.x - 0.5f).toDouble(), -(p.y - 0.5f).toDouble())
    return if (a < 0) a + 2 * PI else a
}

/** Sorts positions clockwise from the top (stable for equal angles). */
fun List<TablePosition>.sortedClockwise(): List<TablePosition> = sortedBy { clockwiseAngle(it) }
