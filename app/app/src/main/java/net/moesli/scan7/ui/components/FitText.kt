package net.moesli.scan7.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified

/**
 * Text that shrinks to fit at large font scales (down to its size at font scale 1.0, at least
 * [minSize]; both in dp so the floor doesn't grow with the font scale) before it ellipsizes: short
 * names like "Philip" stay whole at font scale 2.0, only genuinely long ones get "…". At font scale
 * 1.0 it looks like a plain [androidx.compose.material3.Text].
 */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    minSize: Dp = MIN_FIT_TEXT,
    maxLines: Int = 1,
    textAlign: TextAlign = TextAlign.Unspecified,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    // Shrinks only what the font scale added: never below the size at font scale 1.0 (so phones at
    // the default setting look unchanged and a long name ellipsizes as before), nor below [minSize].
    // A bit below it at larger scales (1.3: ~82 %, 2.0: 70 %), so "Philip" still fits beside a total that grew.
    val density = LocalDensity.current
    val belowNormal = (1f - 0.6f * (density.fontScale - 1f)).coerceIn(0.7f, 1f)
    val normal = if (style.fontSize.isSpecified && style.fontSize.isSp) (style.fontSize.value * belowNormal).dp else minSize
    val floor = with(density) { maxOf(minSize, normal).toSp() }
    // Font scales below 1.0: the style itself is the size (never larger than asked for).
    val min = if (style.fontSize.isSpecified && style.fontSize.isSp && style.fontSize.value < floor.value) style.fontSize else floor
    val max = if (style.fontSize.isSpecified && style.fontSize.value > min.value) style.fontSize else min
    // The line height is usually in sp (e.g. 48 sp at 40 sp): scale it with the shrunk font.
    val fitted = if (style.lineHeight.isSpecified && style.lineHeight.isSp && style.fontSize.isSpecified) {
        style.copy(lineHeight = (style.lineHeight.value / style.fontSize.value).em)
    } else {
        style
    }
    BasicText(
        text = text,
        style = fitted.copy(color = color.takeOrElse { style.color }, textAlign = if (textAlign != TextAlign.Unspecified) textAlign else style.textAlign),
        modifier = modifier,
        maxLines = maxLines,
        softWrap = maxLines > 1,
        overflow = overflow,
        autoSize = TextAutoSize.StepBased(minFontSize = min, maxFontSize = max),
    )
}

/** Default floor for [FitText]: still readable at any font scale. */
val MIN_FIT_TEXT = 9.dp

/**
 * [style] with its font size (and sp line height) capped at [maxScale] × the size: for fixed
 * geometry like table cells or photo tags that must not grow with the font setting without limit.
 */
@Composable
fun TextStyle.cappedScale(maxScale: Float): TextStyle {
    val density = LocalDensity.current
    if (density.fontScale <= maxScale) return this
    val f = maxScale / density.fontScale
    return copy(
        fontSize = if (fontSize.isSpecified && fontSize.isSp) fontSize * f else fontSize,
        lineHeight = if (lineHeight.isSpecified && lineHeight.isSp) lineHeight * f else lineHeight,
    )
}
