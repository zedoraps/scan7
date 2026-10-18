package net.moesli.scan7.ui.sheet

import net.moesli.scan7.ui.text.resolve
import net.moesli.scan7.ui.components.avatarSizeForHeight
import net.moesli.scan7.ui.components.PlayerAvatar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.moesli.scan7.ui.components.ConfirmContent
import net.moesli.scan7.ui.components.Confirmations
import net.moesli.scan7.ui.components.DialogPanel
import net.moesli.scan7.ui.components.PlayerUi
import net.moesli.scan7.ui.components.Scan7ConfirmDialog
import net.moesli.scan7.ui.components.samplePlayers
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

private val ChipAvatar = avatarSizeForHeight(21.dp)

/**
 * Close-round confirmation (8), a [Scan7ConfirmDialog] for [Confirmations.closeRound]: "Close round
 * N?", the [openPlayers]' tokens + names, "Anna and David haven't been scored. They'll be marked
 * BUST." Cancel / Close round. If nobody is scored ([playerCount] players, all open): "Nobody has
 * been scored yet – mark everyone BUST?" with the destructive (warning) button. Shows nothing
 * when nobody is open (the round then closes at once).
 */
@Composable
fun CloseRoundDialog(
    round: Int,
    openPlayers: List<PlayerUi>,
    playerCount: Int,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = Confirmations.closeRound(round, openPlayers.map { it.name }, playerCount) ?: return
    Scan7ConfirmDialog(spec, onConfirm, onCancel, modifier) { NameChips(openPlayers) }
}

/** The open players as token + name chips (silent for TalkBack: the body names them). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NameChips(players: List<PlayerUi>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Scan7Spacing.xs),
        modifier = Modifier.clearAndSetSemantics {},
    ) {
        players.forEach { p -> NameChip(p) }
    }
}

@Composable
private fun NameChip(player: PlayerUi) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.tagRow)
    Row(
        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(32.dp)
            .background(colors.ground, shape)
            .border(1.5.dp, colors.line, shape)
            .padding(start = Scan7Spacing.s, end = Scan7Spacing.m),
    ) {
        PlayerAvatar(player.avatar, ChipAvatar)
        Text(player.name, color = colors.text, style = Scan7Type.text13, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CloseRoundPreviewPanel(open: List<PlayerUi>, playerCount: Int) {
    val spec = Confirmations.closeRound(4, open.map { it.name }, playerCount) ?: return
    DialogPanel { ConfirmContent(spec.title.resolve(), spec.confirmLabel.resolve(), {}, {}, spec.body?.resolve(), spec.destructive) { NameChips(open) } }
}

@Preview(widthDp = 390)
@Composable
private fun CloseRoundPreview() {
    Scan7Theme {
        Column(
            verticalArrangement = Arrangement.spacedBy(Scan7Spacing.l),
            modifier = Modifier.background(Scan7Theme.colors.ground).padding(Scan7Spacing.xl),
        ) {
            CloseRoundPreviewPanel(samplePlayers(4).filter { it.name == "Ben" || it.name == "Mia" }, 4)
            CloseRoundPreviewPanel(samplePlayers(4), 4)
        }
    }
}
