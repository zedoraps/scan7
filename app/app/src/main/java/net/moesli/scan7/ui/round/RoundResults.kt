package net.moesli.scan7.ui.round

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import net.moesli.scan7.ui.components.IconButton
import net.moesli.scan7.ui.components.PlayerStatus
import net.moesli.scan7.ui.components.PlayerToken
import net.moesli.scan7.ui.components.RowSize
import net.moesli.scan7.ui.components.StatusTag
import net.moesli.scan7.ui.components.scan7Clickable
import net.moesli.scan7.ui.components.samplePlayers
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/** Height of one player line in the round results overlay. */
private val ResultRowHeight = 48.dp

/** Dim behind the overlay panel (the new round stays visible underneath). */
private const val SCRIM_ALPHA = 0.72f

/**
 * Round results overlay ("Round N results"), shown on top of the new round after "Next round":
 * a card-styled panel with each player's rank badge, token + name, the round's points ("✓ +46")
 * or BUST (magenta + word) and the game total. Tap a player → [onPlayer] (their result sheet for
 * that closed round); ✕ or a tap outside the panel → [onDismiss]. The panel sits between [top]
 * and [bottom] (screen y); the scrim covers the whole screen, so the caller draws the bottom
 * buttons above it to keep them usable.
 */
@Composable
internal fun RoundResultsOverlay(
    results: RoundResultsUi,
    target: Int,
    onPlayer: (String) -> Unit,
    onDismiss: () -> Unit,
    top: Dp,
    bottom: Dp,
    modifier: Modifier = Modifier,
) {
    val c = Scan7Theme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(5f)
            .background(c.ground.copy(alpha = SCRIM_ALPHA))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .clearAndSetSemantics {},
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .offset(y = top)
            .fillMaxWidth()
            .height((bottom - top).coerceAtLeast(ResultRowHeight * 2))
            .zIndex(6f)
            .padding(horizontal = Scan7Spacing.screenMargin),
    ) {
        ResultsPanel(results, target, onPlayer, onDismiss)
    }
}

@Composable
private fun ResultsPanel(
    results: RoundResultsUi,
    target: Int,
    onPlayer: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.cardLarge)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .border(1.5.dp, c.line, shape)
            // Swallow taps on the panel so they don't dismiss it.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
            .semantics { paneTitle = "Round ${results.round} results" }
            .padding(start = Scan7Spacing.l, end = Scan7Spacing.s, top = Scan7Spacing.s, bottom = Scan7Spacing.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Round ${results.round} results",
                color = c.text,
                style = Scan7Type.headline16,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            IconButton(onClick = onDismiss, contentDescription = "Close results") { CloseIcon() }
        }
        Text(
            text = "Tap a player to edit",
            color = c.textMuted,
            style = Scan7Type.text12,
            maxLines = 1,
            modifier = Modifier.padding(bottom = Scan7Spacing.s),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(Scan7Spacing.xxs),
            modifier = Modifier
                .padding(end = Scan7Spacing.s)
                .heightIn(max = ResultRowHeight * 10 + Scan7Spacing.xxs * 9)
                .verticalScroll(rememberScrollState()),
        ) {
            results.rows.forEach { row -> ResultRow(row, target) { onPlayer(row.id) } }
        }
    }
}

@Composable
private fun ResultRow(row: RoundResultRow, target: Int, onClick: () -> Unit) {
    val c = Scan7Theme.colors
    val p = row.player
    val shape = RoundedCornerShape(Scan7Radii.actionRow)
    val roundText = when (val s = p.status) {
        is PlayerStatus.Scored -> "${s.points} points"
        PlayerStatus.Bust -> "bust"
        PlayerStatus.Open -> "not scored"
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(ResultRowHeight)
            .clip(shape)
            .background(c.surfaceDone)
            .scan7Clickable(onClick, onClickLabel = "Edit")
            .clearAndSetSemantics {
                contentDescription = "Rank ${row.rank}, ${p.name}, $roundText this round, total ${p.total} of $target"
            }
            .padding(horizontal = Scan7Spacing.s),
    ) {
        RankBadge(row.rank)
        PlayerToken(p.initial, p.color, RowSize.Compact.token)
        Text(
            text = p.name,
            color = c.text,
            style = Scan7Type.text14,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        StatusTag(p.status, Modifier.width(76.dp), height = 26.dp, textStyle = Scan7Type.tag11)
        Text(
            text = p.total.toString(),
            color = c.text,
            style = Scan7Type.total18,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(48.dp),
        )
    }
}

/** ✕ glyph for the overlay's close button. */
@Composable
private fun CloseIcon(size: Dp = 16.dp) {
    val ink = Scan7Theme.colors.text
    Canvas(Modifier.size(size)) {
        val w = 2.dp.toPx()
        drawLine(ink, Offset(0f, 0f), Offset(this.size.width, this.size.height), w, StrokeCap.Round)
        drawLine(ink, Offset(this.size.width, 0f), Offset(0f, this.size.height), w, StrokeCap.Round)
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RoundResultsOverlayPreview() {
    Scan7Theme {
        val players = samplePlayers(6).map { if (it.status == PlayerStatus.Open) it.copy(status = PlayerStatus.Scored(12)) else it }
        val ranks = roundRanks(players)
        val rows = players.map { RoundResultRow(it, ranks.getValue(it.id)) }.sortedBy { it.rank }
        Box(Modifier.fillMaxSize().background(Scan7Theme.colors.ground)) {
            RoundResultsOverlay(RoundResultsUi(3, rows), 200, {}, {}, top = 104.dp, bottom = 748.dp)
        }
    }
}
