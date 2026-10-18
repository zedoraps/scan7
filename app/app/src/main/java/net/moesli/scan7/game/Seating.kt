package net.moesli.scan7.game

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Radius of the default ring, in normalized table coordinates (center is 0.5, 0.5). */
const val DEFAULT_RING_RADIUS = 0.4f

/**
 * Default card positions for [count] players: evenly spaced on an ellipse (a circle in normalized
 * coordinates, an ellipse on screen), starting at the top and going clockwise.
 */
fun defaultPositions(count: Int): List<TablePosition> = List(count) { i ->
    val a = 2.0 * PI * i / count
    TablePosition(
        x = (0.5 + DEFAULT_RING_RADIUS * sin(a)).toFloat(),
        y = (0.5 - DEFAULT_RING_RADIUS * cos(a)).toFloat(),
    )
}

/** Angle of [p] around the table center, clockwise from 12 o'clock, in [0, 2π). y grows downwards. */
fun clockwiseAngle(p: TablePosition): Double {
    val a = atan2((p.x - 0.5f).toDouble(), -(p.y - 0.5f).toDouble())
    return if (a < 0) a + 2 * PI else a
}

/** Sorts positions clockwise from the top (stable for equal angles). */
fun List<TablePosition>.sortedClockwise(): List<TablePosition> = sortedBy { clockwiseAngle(it) }
