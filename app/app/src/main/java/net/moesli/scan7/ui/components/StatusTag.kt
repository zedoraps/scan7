package net.moesli.scan7.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.moesli.scan7.R
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/** Tag text: "OPEN", "✓ +46" (Skyjo also "✓ −3") or "✕ BUST" (bust is never shown by color alone). */
@Composable
fun PlayerStatus.tagText(): String = when (this) {
    PlayerStatus.Open -> stringResource(R.string.tag_open)
    is PlayerStatus.Scored -> if (points < 0) stringResource(R.string.tag_scored_negative, -points) else stringResource(R.string.tag_scored, points)
    PlayerStatus.Bust -> stringResource(R.string.label_bust_tag)
}

@Composable
private fun PlayerStatus.tagDescription(): String = when (this) {
    PlayerStatus.Open -> stringResource(R.string.status_open)
    is PlayerStatus.Scored -> stringResource(R.string.status_scored, points)
    PlayerStatus.Bust -> stringResource(R.string.status_bust)
}

/**
 * Status tag. Open: dashed 1.5 dp outline, muted "OPEN" (only shown in arrange mode / compact
 * cards). Scored: `accentTint` fill, `accent` "✓ +46". Bust: `bust` fill, `onAccent` "✕ BUST".
 * The tag fills the width it is given; defaults are the list-row size (28 high, radius 8, 12 sp).
 */
@Composable
fun StatusTag(
    status: PlayerStatus,
    modifier: Modifier = Modifier,
    height: Dp = 28.dp,
    cornerRadius: Dp = Scan7Radii.tagRow,
    textStyle: TextStyle = Scan7Type.tag12,
) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(cornerRadius)
    val description = status.tagDescription()
    val (bg, ink) = when (status) {
        PlayerStatus.Open -> colors.ground.copy(alpha = 0f) to colors.textMuted
        is PlayerStatus.Scored -> colors.accentTint to colors.accentText
        PlayerStatus.Bust -> colors.bust to colors.onAccent
    }
    val full = status.tagText()
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = height)
            .background(bg, shape)
            .then(
                if (status == PlayerStatus.Open) Modifier.dashedBorder(1.5.dp, colors.openOutline, cornerRadius) else Modifier,
            )
            .semantics { contentDescription = description },
    ) {
        // The word must always be readable in full (brand rule: never "BUS'"): shrink the label to
        // fit; if even the smallest size doesn't fit, drop the side padding, then the ✕ / ✓ icon.
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        // A fixed minimum in dp, not sp: at font scale 2.0 an 8 sp minimum would still be too wide.
        val minSize = with(density) { MIN_TAG_TEXT.toSp() }
        val available = constraints.maxWidth
        val padding = with(density) { Scan7Spacing.xs.roundToPx() } * 2
        fun fits(text: String, pad: Int): Boolean =
            available == Constraints.Infinity ||
                measurer.measure(text, textStyle.copy(fontSize = minSize), maxLines = 1, softWrap = false).size.width + pad <= available
        val (text, pad) = when {
            fits(full, padding) -> full to Scan7Spacing.xs
            fits(full, 0) -> full to 0.dp
            else -> full.withoutIcon() to 0.dp
        }
        BasicText(
            text = text,
            style = textStyle.copy(color = ink, textAlign = TextAlign.Center),
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = minSize, maxFontSize = if (textStyle.fontSize.value > minSize.value) textStyle.fontSize else minSize),
            modifier = Modifier.padding(horizontal = pad),
        )
    }
}

/** "BUST" from "✕ BUST", "+27" from "✓ +27": the label without its leading icon glyph. */
internal fun String.withoutIcon(): String =
    if (length > 2 && this[1] == ' ' && !this[0].isLetterOrDigit() && this[0] != '+' && this[0] != '-') substring(2) else this

/** Smallest tag text, in dp so it doesn't grow with the font scale. */
private val MIN_TAG_TEXT = 7.dp

/**
 * Leader badge: white circle with a dark crown. 24 dp overlapping a card's top-right corner; the
 * list view uses a 20 dp badge after the name. The crown icon is [CROWN_FILL] of the badge (the
 * glyph itself ≈ 63 % wide, 55 % high), so it reads at 20 dp; the badge's outer size is unchanged.
 */
@Composable
fun LeaderBadge(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    crownSize: Dp = size * CROWN_FILL,
) {
    val colors = Scan7Theme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .background(colors.text, CircleShape),
    ) {
        CrownIcon(colors.ground, size = crownSize)
    }
}

/** Crown icon size relative to the badge diameter (the 24-grid glyph spans 18 × 16 of it). */
private const val CROWN_FILL = 0.84f

@Preview
@Composable
private fun StatusTagPreview() {
    Scan7Theme {
        Column(
            verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s),
            modifier = Modifier
                .background(Scan7Theme.colors.ground)
                .padding(Scan7Spacing.l),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
                listOf(PlayerStatus.Open, PlayerStatus.Scored(46), PlayerStatus.Bust).forEach {
                    StatusTag(it, Modifier.width(96.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                LeaderBadge()
                LeaderBadge(size = 20.dp)
            }
        }
    }
}
