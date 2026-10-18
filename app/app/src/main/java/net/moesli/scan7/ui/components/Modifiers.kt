package net.moesli.scan7.ui.components

import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/** Dashed rounded border drawn inside the bounds (CSS `border: 1.5px dashed`). */
internal fun Modifier.dashedBorder(width: Dp, color: Color, cornerRadius: Dp): Modifier = drawWithContent {
    drawContent()
    val w = width.toPx()
    val dash = 4.dp.toPx()
    val gap = 3.dp.toPx()
    val r = (cornerRadius.toPx() - w / 2).coerceAtLeast(0f)
    drawRoundRect(
        color = color,
        topLeft = Offset(w / 2, w / 2),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(r),
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gap))),
    )
}

/** Solid or dashed border depending on [dashed]. */
internal fun Modifier.cardBorder(width: Dp, color: Color, shape: Shape, cornerRadius: Dp, dashed: Boolean): Modifier =
    if (dashed) dashedBorder(width, color, cornerRadius) else border(width, color, shape)

/** Open card shadow: `0 8px 18px rgba(0,0,0,.35)`. */
internal fun Modifier.openCardShadow(shape: Shape): Modifier =
    dropShadow(shape, Shadow(radius = 18.dp, color = Color.Black, offset = DpOffset(0.dp, 8.dp), alpha = 0.35f))

/** Dragged card shadow: `0 18px 30px rgba(0,0,0,.55)`. */
internal fun Modifier.draggingCardShadow(shape: Shape): Modifier =
    dropShadow(shape, Shadow(radius = 30.dp, color = Color.Black, offset = DpOffset(0.dp, 18.dp), alpha = 0.55f))
