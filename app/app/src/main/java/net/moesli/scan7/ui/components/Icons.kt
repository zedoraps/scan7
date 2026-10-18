package net.moesli.scan7.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Small line icons on a 24×24 grid (paths copied from the design prototype) as [ImageVector]s
 * drawn by M3 [Icon], so the app needs no icon library. Direction icons (back, chevron) mirror in
 * RTL. All are decorative: the owning button carries the content description.
 */

private const val GRID = 24f

/** One vector from SVG path data [d]: stroked with [strokeWidth] grid units, or filled if null. */
private fun vector(name: String, d: String, strokeWidth: Float?, autoMirror: Boolean = false): ImageVector =
    ImageVector.Builder(name, GRID.dp, GRID.dp, GRID, GRID, autoMirror = autoMirror).addPath(
        pathData = PathParser().parsePathString(d).toNodes(),
        fill = if (strokeWidth == null) SolidColor(Color.Black) else null,
        stroke = if (strokeWidth != null) SolidColor(Color.Black) else null,
        strokeLineWidth = strokeWidth ?: 0f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()

/** Circle of radius [r] at ([cx], [cy]) as SVG path data. */
private fun circle(cx: Float, cy: Float, r: Float): String =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

/** Rounded rectangle ([x], [y], [w], [h], corner [r]) as SVG path data. */
private fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float): String =
    "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} ${r}" +
        "h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}z"

@Composable
private fun VectorIcon(vector: ImageVector, color: Color, size: Dp, modifier: Modifier) =
    Icon(vector, contentDescription = null, tint = color, modifier = modifier.size(size))

private object Vectors {
    val back by lazy { vector("Back", "M15 5l-7 7 7 7", 2.4f, autoMirror = true) }
    val chevronRight by lazy { vector("ChevronRight", "M9 5l7 7-7 7", 2.2f, autoMirror = true) }
    val plus by lazy { vector("Plus", "M12 5v14M5 12h14", 2.4f) }
    val minus by lazy { vector("Minus", "M5 12h14", 2.4f) }
    val check by lazy { vector("Check", "M5 12.5l4.5 4.5L19 7.5", 3f) }
    val pencil by lazy { vector("Pencil", "M4 20h4L19 9l-4-4L4 16v4zM13.5 6.5l4 4", 2f) }
    val dragHandle by lazy {
        vector("DragHandle", listOf(9f, 15f).flatMap { x -> listOf(6f, 12f, 18f).map { y -> circle(x, y, 1.8f) } }.joinToString(""), null)
    }
    val crown by lazy { vector("Crown", "M3 18h18l-1.5-10-5 4L12 5l-2.5 7-5-4z", null) }
    val scan by lazy { vector("Scan", "M4 8V5h3M17 5h3v3M20 16v3h-3M7 19H4v-3M4 12h16", 2.4f) }
    val list by lazy { vector("List", "M4 6h16M4 12h16M4 18h16", 2f) }
    val arrange by lazy {
        vector(
            "Arrange",
            "M12 3v18M3 12h18M12 3l-3 3M12 3l3 3M12 21l-3-3M12 21l3-3M3 12l3-3M3 12l3 3M21 12l-3-3M21 12l-3 3",
            2f,
        )
    }
    val menu by lazy { vector("Menu", listOf(5f, 12f, 19f).joinToString("") { y -> circle(12f, y, 2f) }, null) }

    /** Four seats around a table (prototype: rects with rx 1). */
    val table by lazy {
        val seats = listOf(
            roundRect(9f, 2.5f, 6f, 4f, 1f),
            roundRect(9f, 17.5f, 6f, 4f, 1f),
            roundRect(2f, 10f, 5f, 4f, 1f),
            roundRect(17f, 10f, 5f, 4f, 1f),
        )
        vector("Table", seats.joinToString(""), 2f)
    }
    private const val TORCH = "M8 9h8l-2 4v8h-4v-8zM8 9V7h8v2M12 15v2"
    val torchOff by lazy { vector("TorchOff", TORCH, 2f) }
    val torchOn by lazy { vector("TorchOn", TORCH + "M12 2v2M6 3.5l1.2 1.6M18 3.5l-1.2 1.6", 2f) }
}

/** Back arrow (mirrored in RTL). */
@Composable
fun BackIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) = VectorIcon(Vectors.back, color, size, modifier)

/** Chevron pointing forward (mirrored in RTL). */
@Composable
fun ChevronRightIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) =
    VectorIcon(Vectors.chevronRight, color, size, modifier)

@Composable
fun PlusIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) = VectorIcon(Vectors.plus, color, size, modifier)

@Composable
fun MinusIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) = VectorIcon(Vectors.minus, color, size, modifier)

@Composable
fun CheckIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 16.dp) = VectorIcon(Vectors.check, color, size, modifier)

/** Six-dot drag handle. */
@Composable
fun DragHandleIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) =
    VectorIcon(Vectors.dragHandle, color, size, modifier)

@Composable
fun PencilIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) = VectorIcon(Vectors.pencil, color, size, modifier)

/** Crown for the leader badge. */
@Composable
fun CrownIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 14.dp) = VectorIcon(Vectors.crown, color, size, modifier)

/** Scan frame with a scan line (in-card / in-row Scan button). */
@Composable
fun ScanIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) = VectorIcon(Vectors.scan, color, size, modifier)

/** Three lines (list view). */
@Composable
fun ListIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) = VectorIcon(Vectors.list, color, size, modifier)

/** Four arrows (arrange seats). */
@Composable
fun ArrangeIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) = VectorIcon(Vectors.arrange, color, size, modifier)

/** Vertical three dots (menu). */
@Composable
fun MenuIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) = VectorIcon(Vectors.menu, color, size, modifier)

/** Four seats around a table (table view). */
@Composable
fun TableIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) = VectorIcon(Vectors.table, color, size, modifier)

/** Flashlight (torch toggle in the scan camera header); [on] adds light rays. */
@Composable
fun TorchIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp, on: Boolean = false) =
    VectorIcon(if (on) Vectors.torchOn else Vectors.torchOff, color, size, modifier)
