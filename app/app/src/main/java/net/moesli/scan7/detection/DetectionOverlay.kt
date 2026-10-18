package net.moesli.scan7.detection

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.drawScanBox

/**
 * Draws [detections] on top of an image of [imageWidth]×[imageHeight] (upright) that is displayed
 * centered in the same bounds as this overlay with [contentScale]: [ContentScale.Crop] for the
 * camera preview (FILL_CENTER), [ContentScale.Fit] for a still photo. Other scales are not supported.
 */
@Composable
fun DetectionOverlay(
    detections: List<Detection>,
    imageWidth: Int,
    imageHeight: Int,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
) {
    val colors = Scan7Theme.colors
    val textMeasurer = rememberTextMeasurer()
    val crop = contentScale == ContentScale.Crop
    Canvas(modifier = modifier) {
        if (imageWidth <= 0 || imageHeight <= 0) return@Canvas
        val mapping = DisplayMapping.of(imageWidth, imageHeight, size.width, size.height, crop)
        val strokeWidth = 2.dp.toPx()
        for (detection in detections) {
            val box = mapping.toView(detection.box)
            drawScanBox(colors, Offset(box.left, box.top), Size(box.width, box.height), strokeWidth)
            drawText(
                textMeasurer = textMeasurer,
                text = "${detection.label} ${(detection.score * 100).roundToInt()}%",
                topLeft = Offset(box.left.coerceAtLeast(0f), (box.top - 16.sp.toPx()).coerceAtLeast(0f)),
                style = TextStyle(color = colors.text, fontSize = 12.sp, background = colors.scanLabelBackground),
            )
        }
    }
}
