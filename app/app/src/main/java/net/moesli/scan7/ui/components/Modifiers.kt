package net.moesli.scan7.ui.components

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.offset
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

/**
 * Grows the touch area by [horizontal] / [vertical] on each side while keeping the layout size:
 * the rest of the chain is measured that much larger and placed centred over this slot. Use it
 * right before the clickable, then `padding(horizontal, vertical)` before the visual modifiers
 * (clip, background, `indication`) so the look stays the same. Custom because
 * `minimumInteractiveComponentSize()` grows the layout, and the card / row geometry is fixed by
 * the handoff. Keep neighbours' expansions ≤ half their gap so they don't steal each other's taps;
 * a clipping parent still cuts the area at its edge.
 */
internal fun Modifier.expandedTouchArea(horizontal: Dp, vertical: Dp): Modifier = layout { measurable, constraints ->
    val dx = horizontal.roundToPx().coerceAtLeast(0)
    val dy = vertical.roundToPx().coerceAtLeast(0)
    val placeable = measurable.measure(constraints.offset(2 * dx, 2 * dy))
    layout(constraints.constrainWidth(placeable.width - 2 * dx), constraints.constrainHeight(placeable.height - 2 * dy)) {
        placeable.place(-dx, -dy)
    }
}

/** Extra touch margin per side so a [visual]-sized target reaches 48 dp (0 when it already does). */
internal fun touchSlack(visual: Dp, target: Dp = 48.dp): Dp = ((target - visual) / 2).coerceAtLeast(0.dp)

/** Solid or dashed border depending on [dashed]. */
internal fun Modifier.cardBorder(width: Dp, color: Color, shape: Shape, cornerRadius: Dp, dashed: Boolean): Modifier =
    if (dashed) dashedBorder(width, color, cornerRadius) else border(width, color, shape)

/** Open card shadow: `0 8px 18px rgba(0,0,0,.35)`. */
internal fun Modifier.openCardShadow(shape: Shape): Modifier =
    dropShadow(shape, Shadow(radius = 18.dp, color = Color.Black, offset = DpOffset(0.dp, 8.dp), alpha = 0.35f))

/** Dragged card shadow: `0 18px 30px rgba(0,0,0,.55)`. */
internal fun Modifier.draggingCardShadow(shape: Shape): Modifier =
    dropShadow(shape, Shadow(radius = 30.dp, color = Color.Black, offset = DpOffset(0.dp, 18.dp), alpha = 0.55f))

/** Largest font scale for components with fixed geometry (table cards, list rows). */
private const val MAX_FIXED_GEOMETRY_FONT_SCALE = 1.3f

/**
 * Caps the font scale for [content] whose size is fixed by the design (e.g. the 72×96 compact
 * card), so large font settings shrink back to [maxScale] instead of clipping the text. Only for
 * fixed geometry: everything else grows with the user's setting.
 */
@Composable
internal fun CappedFontScale(maxScale: Float = MAX_FIXED_GEOMETRY_FONT_SCALE, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    if (density.fontScale <= maxScale) {
        content()
    } else {
        CompositionLocalProvider(LocalDensity provides Density(density.density, maxScale), content = content)
    }
}
