package net.moesli.scan7.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/**
 * Player row for the list view. Columns: ring | name (+ leader badge) | total (right-aligned) |
 * Scan + Bust (open players in normal mode) or the status tag. Same states as [PlayerCard].
 * Fills the available width; the height comes from [size].
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
) {
    val done = player.status.isDone
    val look = cardLook(player.status, mode)
    val radius = if (size == RowSize.Regular) Scan7Radii.cardLarge else Scan7Radii.card
    val shape = RoundedCornerShape(radius)
    val rotation = wiggleRotation(mode == CardMode.Arrange, index)
    val showActions = !done && mode == CardMode.Normal

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
            horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = Scan7Spacing.m)
                .clearAndSetSemantics {},
        ) {
            PlayerScoreRing(player, size.ring, size.token)
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
                if (player.isLeader) LeaderBadge(size = 20.dp)
            }
            Text(
                text = player.total.toString(),
                color = look.ink,
                style = if (size == RowSize.Regular) Scan7Type.total24 else Scan7Type.total18,
                textAlign = TextAlign.End,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.width(size.totalWidth),
            )
        }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.width(size.actionsWidth)) {
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
