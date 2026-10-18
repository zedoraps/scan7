package net.moesli.scan7.ui.sheet

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.moesli.scan7.ui.components.DialogPanel
import net.moesli.scan7.ui.components.PlayerToken
import net.moesli.scan7.ui.components.PlayerUi
import net.moesli.scan7.ui.components.PrimaryButton
import net.moesli.scan7.ui.components.Scan7Dialog
import net.moesli.scan7.ui.components.SecondaryButton
import net.moesli.scan7.ui.components.TokenSize
import net.moesli.scan7.ui.components.samplePlayers
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

private val ChipToken = TokenSize(16.dp, 21.dp, 9.sp)

/**
 * Close-round dialog (8): "Close round N?"; if [openPlayers] is not empty, their tokens + names
 * and "Anna and David haven't been scored. They'll be marked BUST." ([nobodyScored]: "Nobody has
 * been scored yet – mark everyone BUST?"). Cancel / Close round.
 */
@Composable
fun CloseRoundDialog(
    round: Int,
    openPlayers: List<PlayerUi>,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    nobodyScored: Boolean = false,
) {
    Scan7Dialog(onDismiss = onCancel, modifier = modifier) {
        CloseRoundContent(round, openPlayers, onCancel, onConfirm, nobodyScored)
    }
}

/** Content of [CloseRoundDialog] (previews). Call inside a [DialogPanel]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CloseRoundContent(
    round: Int,
    openPlayers: List<PlayerUi>,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    nobodyScored: Boolean = false,
) {
    val colors = Scan7Theme.colors
    Text(
        "Close round $round?",
        color = colors.text,
        style = Scan7Type.buttonName,
        modifier = Modifier.semantics { heading() },
    )
    if (openPlayers.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Scan7Spacing.xs),
            modifier = Modifier.clearAndSetSemantics {},
        ) {
            openPlayers.forEach { p -> NameChip(p) }
        }
        val warning = unscoredWarning(openPlayers.map { it.name }, nobodyScored)
        Text(
            buildAnnotatedString {
                val i = warning.lastIndexOf("BUST")
                append(warning.substring(0, i))
                withStyle(SpanStyle(color = colors.bust, fontWeight = FontWeight.Medium)) { append("BUST") }
                append(warning.substring(i + 4))
            },
            color = colors.text,
            style = Scan7Type.text14,
        )
    } else {
        Text("Everyone is scored.", color = colors.textMuted, style = Scan7Type.text14)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
        SecondaryButton("Cancel", onCancel, Modifier.weight(1f), height = 56.dp, textStyle = Scan7Type.button.copy(fontSize = 14.sp))
        PrimaryButton("Close round", onConfirm, Modifier.weight(1.4f), height = 56.dp, textStyle = Scan7Type.button.copy(fontSize = 14.sp))
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
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(20.dp, 24.dp)) {
            PlayerToken(player.initial, player.color, ChipToken)
        }
        Text(player.name, color = colors.text, style = Scan7Type.text13, maxLines = 1)
    }
}

@Preview(widthDp = 390)
@Composable
private fun CloseRoundPreview() {
    Scan7Theme {
        Column(Modifier.background(Scan7Theme.colors.ground).padding(Scan7Spacing.xl)) {
            DialogPanel { CloseRoundContent(4, samplePlayers(4).filter { it.name == "Ben" || it.name == "Mia" }, {}, {}) }
        }
    }
}

@Preview(widthDp = 390)
@Composable
private fun CloseRoundAllDonePreview() {
    Scan7Theme {
        Column(Modifier.background(Scan7Theme.colors.ground).padding(Scan7Spacing.xl)) {
            DialogPanel { CloseRoundContent(4, emptyList(), {}, {}) }
        }
    }
}
