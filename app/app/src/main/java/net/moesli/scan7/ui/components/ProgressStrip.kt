package net.moesli.scan7.ui.components

import net.moesli.scan7.ui.text.uiPlural
import net.moesli.scan7.ui.text.resolve
import net.moesli.scan7.ui.text.UiText
import net.moesli.scan7.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.tan
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/** Segment skew (CSS skewX(−20deg)). */
private const val SKEW_DEGREES = 20.0

/** "2 of 4 scored · to 200" (finished = scored or bust). */
fun progressText(players: List<PlayerUi>, target: Int): UiText {
    val done = players.count { it.status.isDone }
    return uiPlural(R.plurals.progress_scored, done, done, players.size, target)
}

/**
 * Progress strip above the bottom buttons (24 high): one skewed segment per player in seating
 * order (scored = player color, bust = magenta, open = outline) and "2 of 4 scored · to 200" on
 * the right. Segment width: clamp(120 / n, 8, 24) dp, 8 dp high, 4 dp apart, radius 2.
 */
@Composable
fun ProgressStrip(
    players: List<PlayerUi>,
    target: Int,
    modifier: Modifier = Modifier,
) {
    val colors = Scan7Theme.colors
    val text = progressText(players, target).resolve()
    val n = players.size.coerceAtLeast(1)
    val segment = (120 / n).coerceIn(8, 24).dp
    val gap = Scan7Spacing.xxs
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(Scan7Spacing.xl)
            .clearAndSetSemantics { contentDescription = text },
    ) {
        Canvas(Modifier.size(segment * players.size + gap * (players.size - 1).coerceAtLeast(0), 8.dp)) {
            val w = segment.toPx()
            val h = size.height
            val shift = (tan(Math.toRadians(SKEW_DEGREES)) * h / 2).toFloat()
            val stroke = 1.5.dp.toPx()
            val corners = PathEffect.cornerPathEffect(2.dp.toPx())
            players.forEachIndexed { i, p ->
                val x = i * (w + gap.toPx())
                // skewX(-20deg) around the center: top edge shifts right, bottom edge left.
                val inset = if (p.status == PlayerStatus.Open) stroke / 2 else 0f
                val path = Path().apply {
                    moveTo(x + shift + inset, inset)
                    lineTo(x + w + shift - inset, inset)
                    lineTo(x + w - shift - inset, h - inset)
                    lineTo(x - shift + inset, h - inset)
                    close()
                }
                when (p.status) {
                    PlayerStatus.Open -> drawPath(path, colors.openOutline, style = Stroke(stroke, pathEffect = corners))
                    PlayerStatus.Bust -> drawPath(path, colors.bust, style = Fill)
                    is PlayerStatus.Scored -> drawPath(path, p.color, style = Fill)
                }
            }
        }
        Text(text, color = colors.textMuted, style = Scan7Type.text12, maxLines = 1)
    }
}

@Preview(widthDp = 390)
@Composable
private fun ProgressStripPreview() {
    Scan7Theme {
        Column(
            verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s),
            modifier = Modifier
                .background(Scan7Theme.colors.ground)
                .padding(Scan7Spacing.screenMargin),
        ) {
            ProgressStrip(samplePlayers(4), 200)
            ProgressStrip(samplePlayers(10), 200)
        }
    }
}
