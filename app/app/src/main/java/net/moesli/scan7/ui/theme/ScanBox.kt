package net.moesli.scan7.ui.theme

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Draws a detection box in the brand style: [Scan7Colors.scanBox] (cyan) stroke on top of a
 * wider [Scan7Colors.scanBoxOutline] (white) stroke, so it stays visible on the light-blue
 * Freeze card. [strokeWidth] is the cyan stroke; the white outline adds [outlineWidth] per side.
 * [cornerRadius] (px) rounds the box (scan screens use 10 dp); 0 draws a plain rectangle.
 * [ink] replaces the cyan (bust magenta for duplicates); [alpha] fades both strokes (applied per
 * draw call, no offscreen layer).
 */
fun DrawScope.drawScanBox(
    colors: Scan7Colors,
    topLeft: Offset,
    size: Size,
    strokeWidth: Float,
    outlineWidth: Float = strokeWidth / 2f,
    cornerRadius: Float = 0f,
    ink: Color = colors.scanBox,
    alpha: Float = 1f,
) {
    if (cornerRadius > 0f) {
        val r = CornerRadius(cornerRadius)
        drawRoundRect(colors.scanBoxOutline, topLeft, size, r, style = Stroke(width = strokeWidth + 2f * outlineWidth), alpha = alpha)
        drawRoundRect(ink, topLeft, size, r, style = Stroke(width = strokeWidth), alpha = alpha)
    } else {
        drawRect(colors.scanBoxOutline, topLeft, size, style = Stroke(width = strokeWidth + 2f * outlineWidth), alpha = alpha)
        drawRect(ink, topLeft, size, style = Stroke(width = strokeWidth), alpha = alpha)
    }
}
