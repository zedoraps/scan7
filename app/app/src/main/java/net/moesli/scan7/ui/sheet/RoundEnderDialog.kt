package net.moesli.scan7.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.moesli.scan7.R
import net.moesli.scan7.game.GameState
import net.moesli.scan7.ui.components.DialogPanel
import net.moesli.scan7.ui.components.PlayerAvatar
import net.moesli.scan7.ui.components.PlayerUi
import net.moesli.scan7.ui.components.Scan7Dialog
import net.moesli.scan7.ui.components.SecondaryButton
import net.moesli.scan7.ui.components.avatarSizeForHeight
import net.moesli.scan7.ui.components.samplePlayers
import net.moesli.scan7.ui.components.scan7Clickable
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

private val RowAvatar = avatarSizeForHeight(30.dp)

/**
 * One player in [RoundEnderDialog]: their round [points] and, if ending the round would penalise
 * them, what they would count ([penalty]: points × 2, or double the round's highest for Crazy ×2).
 */
@Immutable
data class EnderChoice(val player: PlayerUi, val points: Int, val penalty: Int? = null) {
    val doubled: Boolean get() = penalty != null
}

/**
 * Skyjo: the players to pick from when closing the running round, each with the points they
 * entered and whether the game's rule would double them as the round ender.
 */
fun GameState.enderChoices(uis: Map<Long, PlayerUi>): List<EnderChoice> {
    val r = currentRound
    return playerIds.mapNotNull { id ->
        val ui = uis[id] ?: return@mapNotNull null
        val asEnder = r.copy(endedBy = id)
        EnderChoice(ui, r.points(id), roundPoints(asEnder, id).takeIf { isDoubled(asEnder, id) })
    }
}

/**
 * "Who ended round N?" (Skyjo, closing a round): one row per player (avatar, name, the round's
 * points, "×2 → 24" when the rule would double them). A tap picks the round ender and closes the
 * round ([onPick]); Cancel / Back / the scrim keep the round open.
 */
@Composable
fun RoundEnderDialog(
    round: Int,
    choices: List<EnderChoice>,
    onPick: (playerId: String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.ender_title, round)
    Scan7Dialog(onDismiss = onCancel, modifier = modifier.semantics { paneTitle = title }) {
        EnderContent(title, choices, onPick, onCancel, requestFocus = true)
    }
}

@Composable
private fun EnderContent(title: String, choices: List<EnderChoice>, onPick: (String) -> Unit, onCancel: () -> Unit, requestFocus: Boolean = false) {
    val colors = Scan7Theme.colors
    val focus = remember { FocusRequester() }
    if (requestFocus) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Text(
        title,
        color = colors.text,
        style = Scan7Type.buttonName,
        modifier = Modifier
            .focusRequester(focus)
            .focusable()
            .semantics { heading() },
    )
    Text(stringResource(R.string.ender_body), color = colors.textMuted, style = Scan7Type.text13)
    Column(
        verticalArrangement = Arrangement.spacedBy(Scan7Spacing.xs),
        modifier = Modifier
            .heightIn(max = 360.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        choices.forEach { c -> EnderRow(c) { onPick(c.player.id) } }
    }
    SecondaryButton(
        stringResource(R.string.action_cancel),
        onCancel,
        Modifier.fillMaxWidth(),
        height = 56.dp,
        textStyle = Scan7Type.button.copy(fontSize = 14.sp),
    )
}

@Composable
private fun EnderRow(choice: EnderChoice, onClick: () -> Unit) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.tagRow)
    val points = if (choice.doubled) {
        stringResource(R.string.ender_points_doubled, choice.points, choice.penalty ?: 0)
    } else {
        choice.points.toString()
    }
    val spoken = if (choice.doubled) {
        pluralStringResource(R.plurals.ender_row_doubled_spoken, choice.points, choice.player.name, choice.points, choice.penalty ?: 0)
    } else {
        pluralStringResource(R.plurals.ender_row_spoken, choice.points, choice.player.name, choice.points)
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(colors.ground, shape)
            .border(1.5.dp, colors.line, shape)
            .scan7Clickable(onClick)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
            }
            .padding(horizontal = Scan7Spacing.m),
    ) {
        PlayerAvatar(choice.player.avatar, RowAvatar)
        Text(choice.player.name, color = colors.text, style = Scan7Type.text14, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(points, color = if (choice.doubled) colors.text else colors.textMuted, style = Scan7Type.text14, maxLines = 1)
    }
}

@Preview(widthDp = 390)
@Composable
private fun RoundEnderPreview() {
    Scan7Theme {
        val players = samplePlayers(4)
        val choices = players.mapIndexed { i, p -> EnderChoice(p, listOf(12, 4, 23, -3)[i], penalty = listOf(24, null, 46, null)[i]) }
        Column(Modifier.background(Scan7Theme.colors.ground).padding(Scan7Spacing.xl)) {
            DialogPanel { EnderContent(stringResource(R.string.ender_title, 4), choices, {}, {}) }
        }
    }
}
