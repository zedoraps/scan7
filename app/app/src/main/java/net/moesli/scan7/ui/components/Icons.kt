package net.moesli.scan7.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Small line icons drawn on a 24×24 grid (paths copied from the design prototype), so the app
 * needs no icon library. All are decorative: the owning button carries the content description.
 */

private const val GRID = 24f

/** Draws an SVG path [d] (24×24 grid) filled, or stroked with [strokeWidth] grid units. */
@Composable
private fun PathIcon(d: String, color: Color, size: Dp, modifier: Modifier, strokeWidth: Float?) {
    val path = remember(d) { PathParser().parsePathString(d).toPath() }
    Canvas(modifier.size(size)) {
        scale(this.size.width / GRID, pivot = Offset.Zero) {
            if (strokeWidth == null) {
                drawPath(path, color)
            } else {
                drawPath(path, color, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** Crown for the leader badge. */
@Composable
fun CrownIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 14.dp) =
    PathIcon("M3 18h18l-1.5-10-5 4L12 5l-2.5 7-5-4z", color, size, modifier, strokeWidth = null)

/** Scan frame with a scan line (in-card / in-row Scan button). */
@Composable
fun ScanIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) =
    PathIcon("M4 8V5h3M17 5h3v3M20 16v3h-3M7 19H4v-3M4 12h16", color, size, modifier, strokeWidth = 2.4f)

/** Three lines (list view). */
@Composable
fun ListIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) =
    PathIcon("M4 6h16M4 12h16M4 18h16", color, size, modifier, strokeWidth = 2f)

/** Four arrows (arrange seats). */
@Composable
fun ArrangeIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) = PathIcon(
    "M12 3v18M3 12h18M12 3l-3 3M12 3l3 3M12 21l-3-3M12 21l3-3M3 12l3-3M3 12l3 3M21 12l-3-3M21 12l-3 3",
    color, size, modifier, strokeWidth = 2f,
)

/** Vertical three dots (menu). */
@Composable
fun MenuIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    Canvas(modifier.size(size)) {
        val u = this.size.width / GRID
        for (y in listOf(5f, 12f, 19f)) drawCircle(color, radius = 2f * u, center = Offset(12f * u, y * u))
    }
}

/** Four seats around a table (table view). */
@Composable
fun TableIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    Canvas(modifier.size(size)) {
        val u = this.size.width / GRID
        val stroke = Stroke(2f * u)
        // x, y, w, h of the four seats (prototype: rects with rx 1).
        val seats = listOf(
            floatArrayOf(9f, 2.5f, 6f, 4f),
            floatArrayOf(9f, 17.5f, 6f, 4f),
            floatArrayOf(2f, 10f, 5f, 4f),
            floatArrayOf(17f, 10f, 5f, 4f),
        )
        for ((x, y, w, h) in seats) {
            drawRoundRect(
                color,
                topLeft = Offset(x * u, y * u),
                size = Size(w * u, h * u),
                cornerRadius = CornerRadius(u),
                style = stroke,
            )
        }
    }
}

/** Flashlight (torch toggle in the scan camera header); [on] adds light rays. */
@Composable
fun TorchIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp, on: Boolean = false) = PathIcon(
    "M8 9h8l-2 4v8h-4v-8zM8 9V7h8v2M12 15v2" + if (on) "M12 2v2M6 3.5l1.2 1.6M18 3.5l-1.2 1.6" else "",
    color, size, modifier, strokeWidth = 2f,
)
