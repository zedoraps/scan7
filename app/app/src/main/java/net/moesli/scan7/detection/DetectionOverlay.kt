package net.moesli.scan7.detection

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Draws [detections] on top of an image of [imageWidth]×[imageHeight] (upright) that is displayed
 * centered with [contentScale] in the same bounds as this overlay — e.g. [ContentScale.Crop] for the
 * camera preview, [ContentScale.Fit] for a still photo.
 */
@Composable
fun DetectionOverlay(
    detections: List<Detection>,
    imageWidth: Int,
    imageHeight: Int,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
    color: Color = Color.Green,
) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier = modifier) {
        if (imageWidth <= 0 || imageHeight <= 0) return@Canvas
        val imageSize = Size(imageWidth.toFloat(), imageHeight.toFloat())
        val scale = contentScale.computeScaleFactor(imageSize, size)
        val shownWidth = imageSize.width * scale.scaleX
        val shownHeight = imageSize.height * scale.scaleY
        val left = (size.width - shownWidth) / 2f
        val top = (size.height - shownHeight) / 2f
        val strokeWidth = 2.dp.toPx()
        for (detection in detections) {
            val box = detection.box
            val topLeft = Offset(left + box.left * shownWidth, top + box.top * shownHeight)
            drawRect(
                color = color,
                topLeft = topLeft,
                size = Size(box.width() * shownWidth, box.height() * shownHeight),
                style = Stroke(width = strokeWidth),
            )
            drawText(
                textMeasurer = textMeasurer,
                text = "${detection.label} ${(detection.score * 100).roundToInt()}%",
                topLeft = Offset(topLeft.x, (topLeft.y - 16.sp.toPx()).coerceAtLeast(0f)),
                style = TextStyle(color = color, fontSize = 12.sp, background = Color.Black.copy(alpha = 0.5f)),
            )
        }
    }
}
