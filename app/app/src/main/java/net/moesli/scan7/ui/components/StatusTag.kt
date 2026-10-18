package net.moesli.scan7.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.moesli.scan7.R
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/** Tag text: "OPEN", "✓ +46" or "✕ BUST" (bust is never shown by color alone). */
fun PlayerStatus.tagText(): String = when (this) {
    PlayerStatus.Open -> "OPEN"
    is PlayerStatus.Scored -> "✓ +$points"
    PlayerStatus.Bust -> "✕ BUST"
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
        is PlayerStatus.Scored -> colors.accentTint to colors.accent
        PlayerStatus.Bust -> colors.bust to colors.onAccent
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = height)
            .background(bg, shape)
            .then(
                if (status == PlayerStatus.Open) Modifier.dashedBorder(1.5.dp, colors.openOutline, cornerRadius) else Modifier,
            )
            .padding(horizontal = Scan7Spacing.xs)
            .semantics { contentDescription = description },
    ) {
        Text(text = status.tagText(), color = ink, style = textStyle, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
    }
}

/**
 * Leader badge: white circle with a dark crown. 24 dp overlapping a card's top-right corner; the
 * list view uses a 20 dp badge after the name (crown 14 / 12 dp as in the prototype).
 */
@Composable
fun LeaderBadge(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    crownSize: Dp = if (size >= 24.dp) 14.dp else 12.dp,
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
