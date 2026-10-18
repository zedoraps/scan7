package net.moesli.scan7.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.moesli.scan7.ui.theme.PlayerColors
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/** Token tilt (handoff: −8°). */
private const val TOKEN_ROTATION = -8f

/**
 * The player's token: a small card-shaped rectangle in the player's [color], rotated −8°, with the
 * [initial] in Unbounded 700 in `onAccent`. Decorative — the owning card/row describes the player.
 */
@Composable
fun PlayerToken(
    initial: String,
    color: Color,
    size: TokenSize,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = Scan7Radii.token,
    outline: Color? = null,
    outlineWidth: Dp = 2.dp,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size.width, size.height)
            .rotate(TOKEN_ROTATION)
            .background(color, shape)
            .then(if (outline != null) Modifier.border(outlineWidth, outline, shape) else Modifier),
    ) {
        Text(
            text = initial,
            color = Scan7Theme.colors.onAccent,
            style = Scan7Type.total13.copy(fontSize = size.fontSize, lineHeight = size.fontSize, fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/**
 * Score ring around a [PlayerToken]: progress to the target ([progress] in 0..1, clamped), track
 * `lineSubtle`, progress stroke in the player [color] with round caps, starting at 12 o'clock,
 * stroke ≈ 7 % of the diameter. [dimmed] draws ring + token at 45 % (finished players).
 */
@Composable
fun ScoreRing(
    progress: Float,
    color: Color,
    initial: String,
    size: Dp,
    tokenSize: TokenSize,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
) {
    val track = Scan7Theme.colors.lineSubtle
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .alpha(if (dimmed) DONE_ALPHA else 1f),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Prototype: viewBox 100, r 45, stroke 7.
            val d = this.size.minDimension
            val stroke = d * 0.07f
            val radius = d * 0.45f
            val topLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2, radius * 2)
            drawCircle(track, radius = radius, style = Stroke(stroke))
            val p = progress.coerceIn(0f, 1f)
            if (p > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * p,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        PlayerToken(initial = initial, color = color, size = tokenSize)
    }
}

/** Ring + token for [player] (progress = total / target). */
@Composable
fun PlayerScoreRing(
    player: PlayerUi,
    size: Dp,
    tokenSize: TokenSize,
    modifier: Modifier = Modifier,
    dimmed: Boolean = player.status.isDone,
) = ScoreRing(
    progress = if (player.target > 0) player.total.toFloat() / player.target else 0f,
    color = player.color,
    initial = player.initial,
    size = size,
    tokenSize = tokenSize,
    modifier = modifier,
    dimmed = dimmed,
)

/** Opacity of ring + token on finished cards. */
internal const val DONE_ALPHA = 0.45f

@Preview
@Composable
private fun PlayerTokenPreview() {
    Scan7Theme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.l),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(Scan7Theme.colors.ground)
                .padding(Scan7Spacing.l),
        ) {
            CardSize.entries.forEachIndexed { i, s -> PlayerToken("PAB"[i].toString(), PlayerColors[i], s.token) }
            PlayerToken("B", PlayerColors[2], TokenSize(32.dp, 42.dp, 14.sp), outline = Scan7Theme.colors.onAccent)
            ScoreRing(0f, PlayerColors[0], "P", 56.dp, CardSize.Large.token)
            ScoreRing(0.4f, PlayerColors[1], "A", 56.dp, CardSize.Large.token)
            ScoreRing(1f, PlayerColors[2], "B", 56.dp, CardSize.Large.token)
            ScoreRing(0.7f, PlayerColors[3], "M", 56.dp, CardSize.Large.token, dimmed = true)
        }
    }
}
