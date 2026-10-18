package net.moesli.scan7.ui.components

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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.moesli.scan7.game.Avatar
import net.moesli.scan7.game.AvatarCatalog
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import kotlin.math.PI

/**
 * Score ring around the player's [PlayerAvatar]: progress to the target ([progress] in 0..1, clamped), track
 * `lineSubtle`, progress stroke in the player color with round caps, starting at 12 o'clock,
 * stroke ≈ 7 % of the diameter. The arc carries the avatar's pattern ([BandPattern], ring-scale
 * version of the kit pattern; `plain` = flat colour). [avatarHeight] is the avatar card's height
 * (kit: 32 / 27 / 21 dp in the table view, 30 / 20 dp in the list). [dimmed] draws ring + avatar
 * at 45 % (finished players). [showRing] = false draws only the avatar, filling the ring's box (seating step: no score yet).
 */
@Composable
fun ScoreRing(
    progress: Float,
    avatar: Avatar,
    size: Dp,
    avatarHeight: Dp,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    showRing: Boolean = true,
) {
    val track = Scan7Theme.colors.lineSubtle
    val ink = Scan7Theme.colors.ground
    val color = avatar.colorValue
    val pattern = AvatarCatalog.pattern(avatar.pattern)
    val p = progress.coerceIn(0f, 1f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .alpha(if (dimmed) DONE_ALPHA else 1f)
            .then(
                if (!showRing) Modifier else Modifier.drawWithCache {
                    // Prototype: viewBox 100, r 45, stroke 7. Paths, strokes and the pattern are
                    // rebuilt only when size / progress / avatar change, not per frame.
                    val d = this.size.minDimension
                    val stroke = d * 0.07f
                    val radius = d * 0.45f
                    val c = this.size.center
                    val oval = Rect(Offset(c.x - radius, c.y - radius), Size(radius * 2, radius * 2))
                    val trackStroke = Stroke(stroke)
                    val arcStroke = Stroke(stroke, cap = StrokeCap.Round)
                    val band = BandPattern.create(pattern, stroke, (2 * PI * radius).toFloat(), ink, closed = true)
                    val centre = Path().apply {
                        // A whole lap as an arc from 12 o'clock too, so the stamps line up at any progress.
                        if (p > 0f) arcTo(oval, -90f, 360f * p.coerceAtMost(0.9999f), forceMoveTo = true)
                    }
                    val layer = oval.inflate(stroke)
                    onDrawBehind {
                        drawCircle(track, radius = radius, style = trackStroke)
                        if (p > 0f) {
                            if (band == null) {
                                drawArc(color, -90f, 360f * p, false, oval.topLeft, oval.size, style = arcStroke)
                            } else {
                                drawIntoCanvas { canvas ->
                                    canvas.saveLayer(layer, BandPattern.layerPaint)
                                    drawArc(color, -90f, 360f * p, false, oval.topLeft, oval.size, style = arcStroke)
                                    band.drawStamps(this, centre)
                                    canvas.restore()
                                }
                            }
                        }
                    }
                },
            ),
    ) {
        // Scales with the ring when a layout shrinks it (e.g. a large font scale on a compact card).
        // Without the ring the avatar takes the whole box.
        val avatarSize = if (showRing) avatarSizeForHeight(avatarHeight) else size
        PlayerAvatar(avatar, avatarSize, Modifier.fillMaxSize((avatarSize / size).coerceAtMost(1f)))
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
    showRing: Boolean = true,
) = ScoreRing(
    progress = if (player.target > 0) player.total.toFloat() / player.target else 0f,
    avatar = player.avatar,
    size = size,
    avatarHeight = tokenSize.height,
    modifier = modifier,
    dimmed = dimmed,
    showRing = showRing,
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
