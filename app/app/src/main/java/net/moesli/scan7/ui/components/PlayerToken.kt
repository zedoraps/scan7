package net.moesli.scan7.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.moesli.scan7.game.Avatar
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme

/**
 * Score ring around the player's [PlayerAvatar]: progress to the target ([progress] in 0..1, clamped), track
 * `lineSubtle`, progress stroke in the player [color] with round caps, starting at 12 o'clock,
 * stroke ≈ 7 % of the diameter. [avatarHeight] is the avatar card's height (kit: 32 / 27 / 21 dp in the
 * table view, 30 / 20 dp in the list). [dimmed] draws ring + avatar at 45 % (finished players).
 */
@Composable
fun ScoreRing(
    progress: Float,
    avatar: Avatar,
    size: Dp,
    avatarHeight: Dp,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
) {
    val track = Scan7Theme.colors.lineSubtle
    val color = avatar.colorValue
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
        PlayerAvatar(avatar, avatarSizeForHeight(avatarHeight))
    }
}

/** Ring + avatar for [player] (progress = total / target); the avatar card is [tokenSize] tall. */
@Composable
fun PlayerScoreRing(
    player: PlayerUi,
    size: Dp,
    tokenSize: TokenSize,
    modifier: Modifier = Modifier,
    dimmed: Boolean = player.status.isDone,
) = ScoreRing(
    progress = if (player.target > 0) player.total.toFloat() / player.target else 0f,
    avatar = player.avatar,
    size = size,
    avatarHeight = tokenSize.height,
    modifier = modifier,
    dimmed = dimmed,
)

/** Opacity of ring + avatar on finished cards. */
internal const val DONE_ALPHA = 0.45f

@Preview
@Composable
private fun ScoreRingPreview() {
    Scan7Theme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.l),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(Scan7Theme.colors.ground)
                .padding(Scan7Spacing.l),
        ) {
            CardSize.entries.forEachIndexed { i, s ->
                ScoreRing(0.3f * (i + 1), SamplePlayers[i].avatar, s.ring, s.token.height)
            }
            ScoreRing(0.7f, SamplePlayers[3].avatar, 56.dp, CardSize.Large.token.height, dimmed = true)
        }
    }
}
