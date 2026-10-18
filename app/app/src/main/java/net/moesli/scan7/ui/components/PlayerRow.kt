package net.moesli.scan7.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/**
 * Player row for the list view. Columns: ring | name (+ leader badge) | total (right-aligned) |
 * Scan + Bust (open players in normal mode) or the status tag. Same states as [PlayerCard].
 * Fills the available width; the height comes from [size]. [showScore] = false (new game's
 * seating step): only avatar + name — no ring, total, leader badge or tag.
 */
@Composable
fun PlayerRow(
    player: PlayerUi,
    size: RowSize,
    modifier: Modifier = Modifier,
    mode: CardMode = CardMode.Normal,
    index: Int = 0,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    onScan: () -> Unit = {},
    onBust: () -> Unit = {},
    showScore: Boolean = true,
) {
    val done = player.status.isDone
    val look = cardLook(player.status, mode)
    val radius = if (size == RowSize.Regular) Scan7Radii.cardLarge else Scan7Radii.card
    val shape = RoundedCornerShape(radius)
    val rotation = wiggleRotation(mode == CardMode.Arrange, index)
    val showActions = !done && mode == CardMode.Normal

    // Fixed card geometry (handoff size classes): cap the font scale instead of clipping.
    CappedFontScale {
        // Constraints only: the caller's modifier (drag, TalkBack actions) stays on the clickable row.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            PlayerRowContent(
                player, size, look, shape, radius, rotation, mode, showActions,
                narrow = maxWidth < NarrowRowWidth,
                showScore = showScore,
                modifier = modifier,
                onClick = onClick,
                onLongClick = onLongClick,
                onScan = onScan,
                onBust = onBust,
            )
        }
    }
}

/** Rows narrower than this (a 360 dp phone) use the tighter column layout of [PlayerRowContent]. */
private val NarrowRowWidth = 340.dp

/**
 * The row itself. [narrow] (360 dp phones): smaller ring, tighter gaps and actions, the total
 * only as wide as it is, and the leader badge on the ring's corner (as on the table cards) instead
 * of after the name, so the name keeps room instead of shrinking to "P…".
 */
@Composable
private fun PlayerRowContent(
    player: PlayerUi,
    size: RowSize,
    look: CardLook,
    shape: RoundedCornerShape,
    radius: Dp,
    rotation: State<Float>,
    mode: CardMode,
    showActions: Boolean,
    narrow: Boolean,
    showScore: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onScan: () -> Unit,
    onBust: () -> Unit,
) {
    val gap = if (narrow) Scan7Spacing.s else Scan7Spacing.m
    Row(
        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(size.height)
            .graphicsLayer {
                rotationZ = rotation.value
                val s = if (mode == CardMode.Dragging) 1.06f else 1f
                scaleX = s
                scaleY = s
            }
            .cardSurface(look, shape, radius)
            .playerClickable(player, onClick, onLongClick)
            .padding(end = Scan7Spacing.s),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = Scan7Spacing.m)
                .clearAndSetSemantics {},
        ) {
            Box {
                PlayerScoreRing(player, if (narrow) size.ring - 8.dp else size.ring, size.token, showRing = showScore)
                if (narrow && player.isLeader && showScore) {
                    LeaderBadge(Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp), size = 20.dp)
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = player.name,
                    color = look.ink,
                    style = if (size == RowSize.Regular) Scan7Type.text15 else Scan7Type.text14,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (player.isLeader && !narrow && showScore) LeaderBadge(size = 20.dp)
            }
            if (showScore) Text(
                text = player.total.toString(),
                color = look.ink,
                style = if (size == RowSize.Regular) Scan7Type.total24 else Scan7Type.total18,
                textAlign = TextAlign.End,
                maxLines = 1,
                softWrap = false,
                // Right-aligned against the actions column either way.
                modifier = Modifier.widthIn(min = if (narrow) 0.dp else size.totalWidth),
            )
        }
        if (showScore) Box(contentAlignment = Alignment.Center, modifier = Modifier.width(if (narrow) size.actionsWidth - 12.dp else size.actionsWidth)) {
            if (showActions) {
                ScanBustActions(
                    playerName = player.name,
                    onScan = onScan,
                    onBust = onBust,
                    height = size.actionHeight,
                    cornerRadius = Scan7Radii.actionRow,
                )
            } else {
                StatusTag(
                    status = player.status,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clearAndSetSemantics {},
                )
            }
        }
    }
}

@Preview(widthDp = 390)
@Composable
private fun PlayerRowPreview() {
    Scan7Theme {
        Column(
            modifier = Modifier
                .background(Scan7Theme.colors.ground)
                .padding(Scan7Spacing.screenMargin),
        ) {
            val players = samplePlayers(4)
            RowSize.entries.forEach { size ->
                Column(verticalArrangement = Arrangement.spacedBy(size.gap), modifier = Modifier.padding(bottom = Scan7Spacing.l)) {
                    players.forEach { PlayerRow(it, size) }
                    PlayerRow(players[3], size, mode = CardMode.Arrange)
                }
            }
        }
    }
}
