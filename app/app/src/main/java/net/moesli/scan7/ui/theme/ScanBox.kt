package net.moesli.scan7.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Draws a detection box in the brand style: [Scan7Colors.scanBox] (cyan) stroke on top of a
 * wider [Scan7Colors.scanBoxOutline] (white) stroke, so it stays visible on the light-blue
 * Freeze card. [strokeWidth] is the cyan stroke; the white outline adds [outlineWidth] per side.
 */
fun DrawScope.drawScanBox(
    colors: Scan7Colors,
    topLeft: Offset,
    size: Size,
    strokeWidth: Float,
    outlineWidth: Float = strokeWidth / 2f,
) {
    drawRect(colors.scanBoxOutline, topLeft, size, style = Stroke(width = strokeWidth + 2f * outlineWidth))
    drawRect(colors.scanBox, topLeft, size, style = Stroke(width = strokeWidth))
}
