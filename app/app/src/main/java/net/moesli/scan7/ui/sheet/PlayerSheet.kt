package net.moesli.scan7.ui.sheet

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.moesli.scan7.game.ConfirmedCard
import net.moesli.scan7.game.ScoreAdjustment
import net.moesli.scan7.game.countedCards
import net.moesli.scan7.scoring.Card
import net.moesli.scan7.scoring.CardKind
import net.moesli.scan7.scoring.scoreRow
import net.moesli.scan7.ui.components.BottomSheetPanel
import net.moesli.scan7.ui.components.CardSize
import net.moesli.scan7.ui.components.PlayerScoreRing
import net.moesli.scan7.ui.components.PlayerStatus
import net.moesli.scan7.ui.components.PlayerUi
import net.moesli.scan7.ui.components.Scan7BottomSheet
import net.moesli.scan7.ui.components.ScanIcon
import net.moesli.scan7.ui.components.SecondaryButton
import net.moesli.scan7.ui.components.SectionLabel
import net.moesli.scan7.ui.components.TextLinkButton
import net.moesli.scan7.ui.components.dashedBorder
import net.moesli.scan7.ui.components.isDone
import net.moesli.scan7.ui.components.samplePlayers
import net.moesli.scan7.ui.components.scan7Clickable
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/**
 * Player sheet (4a) state. [player] carries name, color, game total and this round's status
 * (open / scored +N / bust); [cards] are the confirmed cards of the result (empty if none were
 * recorded, e.g. one-tap bust or points typed directly).
 */
data class PlayerSheetUi(
    val player: PlayerUi,
    val round: Int,
    val cards: List<ConfirmedCard> = emptyList(),
    /** This player's total adjustments in this game, oldest first ([PlayerUi.total] includes them). */
    val adjustments: List<ScoreAdjustment> = emptyList(),
)

/**
 * "Adjust total" wiring of the player sheet; `null` on [PlayerSheet] hides the section.
 * [onRestore] is the Undo after [onRemove].
 */
class AdjustActions(
    val onAdjust: (delta: Int, note: String?) -> Unit,
    val onRemove: (ScoreAdjustment) -> Unit,
    val onRestore: (ScoreAdjustment) -> Unit,
)

/**
 * Player sheet (4a) as a bottom sheet in the card styling. Open player: Scan (cyan, tall), Enter
 * manually, Bust (magenta outline). Scored / bust: the result (points or BUST, the cards if any)
 * with Edit, Rescan and Reset to open ([onResetToOpen] `null` hides it, e.g. on the end-of-round
 * view: a closed round has no open players). With [adjust]: "Adjust total" (signed delta + note,
 * [AdjustTotalDialog]) and the player's adjustments, each removable with Undo.
 */
@Composable
fun PlayerSheet(
    state: PlayerSheetUi,
    onScan: () -> Unit,
    onEnterManually: () -> Unit,
    onBust: () -> Unit,
    onEdit: () -> Unit,
    onRescan: () -> Unit,
    onResetToOpen: (() -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    adjust: AdjustActions? = null,
) {
    var adjusting by rememberSaveable { mutableStateOf(false) }
    // Last removed adjustment, for the inline Undo (cleared when the sheet closes).
    var removed by remember { mutableStateOf<ScoreAdjustment?>(null) }
    Scan7BottomSheet(onDismiss = onDismiss, modifier = modifier) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            PlayerSheetContent(state, onScan, onEnterManually, onBust, onEdit, onRescan, onResetToOpen)
            if (adjust != null) {
                Spacer(Modifier.height(Scan7Spacing.l))
                AdjustSection(
                    state = state,
                    removed = removed,
                    onAdjust = { adjusting = true },
                    onRemove = {
                        removed = it
                        adjust.onRemove(it)
                    },
                    onUndo = { a ->
                        removed = null
                        adjust.onRestore(a)
                    },
                )
            }
        }
    }
    if (adjust != null && adjusting) {
        AdjustTotalDialog(
            name = state.player.name,
            total = state.player.total,
            onCancel = { adjusting = false },
            onApply = { delta, note ->
                adjusting = false
                removed = null
                adjust.onAdjust(delta, note)
            },
        )
    }
}

/**
 * "Adjust total" button plus the player's adjustments ("+10  penalty · round 2  ✕"); after a
 * removal an "Adjustment removed · Undo" line. Content of [PlayerSheet] (previews).
 */
@Composable
fun AdjustSection(
    state: PlayerSheetUi,
    removed: ScoreAdjustment?,
    onAdjust: () -> Unit,
    onRemove: (ScoreAdjustment) -> Unit,
    onUndo: (ScoreAdjustment) -> Unit,
) {
    val colors = Scan7Theme.colors
    val name = state.player.name
    Column(verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
        if (state.adjustments.isNotEmpty()) {
            SectionLabel("Total adjustments")
            state.adjustments.forEach { AdjustmentRow(it, onRemove = { onRemove(it) }) }
        }
        if (removed != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            ) {
                Text(
                    "Removed ${formatDelta(removed.delta)}",
                    color = colors.textMuted,
                    style = Scan7Type.text13,
                    modifier = Modifier.weight(1f),
                )
                TextLinkButton("Undo", onClick = { onUndo(removed) })
            }
        }
        SecondaryButton(
            "Adjust total",
            onAdjust,
            Modifier.fillMaxWidth(),
            height = Scan7Spacing.minTouch,
            cornerRadius = Scan7Radii.iconButton,
            textStyle = Scan7Type.button.copy(fontSize = 13.sp),
            contentDescription = "Adjust $name's total (penalty, house rule)",
        )
    }
}

@Composable
private fun AdjustmentRow(adjustment: ScoreAdjustment, onRemove: () -> Unit) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.tagRow)
    Row(
        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.ground, shape)
            .padding(start = Scan7Spacing.m),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .clearAndSetSemantics { contentDescription = "Adjustment " + adjustmentDescription(adjustment) },
        ) {
            Text(formatDelta(adjustment.delta), color = colors.text, style = Scan7Type.total15, maxLines = 1)
            Text(
                adjustmentDetail(adjustment),
                color = colors.textMuted,
                style = Scan7Type.text12,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(Scan7Spacing.minTouch)
                .clip(shape)
                .scan7Clickable(onRemove)
                .clearAndSetSemantics {
                    contentDescription = "Remove adjustment " + adjustmentDescription(adjustment)
                    role = Role.Button
                },
        ) {
            Text("✕", color = colors.textMuted, style = Scan7Type.text14)
        }
    }
}

/** Content of [PlayerSheet] (previews, gallery). */
@Composable
fun PlayerSheetContent(
    state: PlayerSheetUi,
    onScan: () -> Unit,
    onEnterManually: () -> Unit,
    onBust: () -> Unit,
    onEdit: () -> Unit,
    onRescan: () -> Unit,
    onResetToOpen: (() -> Unit)?,
) {
    val player = state.player
    Column(verticalArrangement = Arrangement.spacedBy(Scan7Spacing.l)) {
        SheetHeader(state)
        if (!player.status.isDone) {
            OpenActions(player.name, onScan, onEnterManually, onBust)
        } else {
            ResultPanel(state)
            Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
                SecondaryButton("Edit", onEdit, Modifier.weight(1f), height = 56.dp, contentDescription = "Edit ${player.name}'s cards")
                SecondaryButton("Rescan", onRescan, Modifier.weight(1f), height = 56.dp, contentDescription = "Rescan ${player.name}")
            }
            if (onResetToOpen != null) {
                SecondaryButton(
                    "Reset to open",
                    onResetToOpen,
                    Modifier.fillMaxWidth(),
                    height = Scan7Spacing.minTouch,
                    cornerRadius = Scan7Radii.iconButton,
                    textStyle = Scan7Type.button.copy(fontSize = 13.sp),
                    contentDescription = "Reset ${player.name} to not scored",
                )
            }
        }
    }
}

@Composable
private fun SheetHeader(state: PlayerSheetUi) {
    val colors = Scan7Theme.colors
    val player = state.player
    Row(
        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        PlayerScoreRing(player, CardSize.Large.ring, CardSize.Large.token, dimmed = false)
        Column(verticalArrangement = Arrangement.spacedBy(Scan7Spacing.xxs), modifier = Modifier.weight(1f)) {
            Text(player.name, color = colors.text, style = Scan7Type.buttonName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${player.total} ${if (player.total == 1) "point" else "points"} · round ${state.round}",
                color = colors.textMuted,
                style = Scan7Type.text12,
            )
        }
    }
}

@Composable
private fun OpenActions(name: String, onScan: () -> Unit, onEnterManually: () -> Unit, onBust: () -> Unit) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.button)
    Column(verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(shape)
                .background(colors.accent)
                .scan7Clickable(onScan)
                .clearAndSetSemantics {
                    contentDescription = "Scan $name"
                    role = Role.Button
                },
        ) {
            ScanIcon(colors.onAccent, size = 28.dp)
            Text("Scan", color = colors.onAccent, style = Scan7Type.button.copy(fontSize = 18.sp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
            SecondaryButton(
                "Enter manually",
                onEnterManually,
                Modifier.weight(1f),
                height = 56.dp,
                textStyle = Scan7Type.button.copy(fontSize = 14.sp),
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(shape)
                    .border(1.5.dp, colors.bust, shape)
                    .scan7Clickable(onBust)
                    .clearAndSetSemantics {
                        contentDescription = "Mark $name as bust"
                        role = Role.Button
                    },
            ) {
                Text("✕ BUST", color = colors.bust, style = Scan7Type.tag12.copy(fontSize = 14.sp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultPanel(state: PlayerSheetUi) {
    val colors = Scan7Theme.colors
    val status = state.player.status
    val counted = state.cards.countedCards()
    val score = if (state.cards.isEmpty()) null else scoreRow(counted)
    val duplicates = duplicatedCards(state.cards)
    val shape = RoundedCornerShape(Scan7Radii.card)
    Column(
        verticalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.ground, shape)
            .padding(Scan7Spacing.l),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m), verticalAlignment = Alignment.Bottom) {
            when (status) {
                is PlayerStatus.Scored -> {
                    Text("+${status.points}", color = colors.accent, style = Scan7Type.roundNumberList)
                    Text("points this round", color = colors.textMuted, style = Scan7Type.text12, modifier = Modifier.padding(bottom = 2.dp))
                }
                else -> {
                    Text("✕ BUST", color = colors.bust, style = Scan7Type.roundNumberList)
                    Text("0 points this round", color = colors.textMuted, style = Scan7Type.text12, modifier = Modifier.padding(bottom = 2.dp))
                }
            }
        }
        if (score != null) {
            Text(
                breakdownLine(counted.filter { it.kind == CardKind.NUMBER }.map { it.value!! }, score),
                color = if (score.bust) colors.bust else colors.textMuted,
                style = Scan7Type.text12,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.xs), verticalArrangement = Arrangement.spacedBy(Scan7Spacing.xs)) {
                state.cards.forEach { CardChip(it.card, it.ignored, duplicate = it.card in duplicates) }
            }
        } else {
            Text(
                if (status == PlayerStatus.Bust) "Marked bust, no cards recorded." else "Points entered without cards.",
                color = colors.textMuted,
                style = Scan7Type.text12,
            )
        }
    }
}

/** Card chip: number / modifier normal; duplicates outlined magenta; action cards and backs dashed grey "ignored". */
@Composable
fun CardChip(card: Card, ignored: Boolean, modifier: Modifier = Modifier, duplicate: Boolean = false) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.tagRow)
    val label = chipLabel(card)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(32.dp)
            .widthIn(min = 36.dp)
            .clip(shape)
            .then(
                when {
                    ignored -> Modifier.dashedBorder(1.5.dp, colors.openOutline, Scan7Radii.tagRow)
                    duplicate -> Modifier.background(colors.surface).border(1.5.dp, colors.bust, shape)
                    else -> Modifier.background(colors.surface).border(1.5.dp, colors.line, shape)
                },
            )
            .padding(horizontal = Scan7Spacing.s)
            .semantics { contentDescription = if (ignored) "$label, ignored" else if (duplicate) "$label, duplicate" else label },
    ) {
        Text(
            if (ignored) "$label · ignored" else label,
            color = when {
                ignored -> colors.textMuted
                duplicate -> colors.bust
                else -> colors.text
            },
            style = Scan7Type.tag12.copy(fontSize = 13.sp),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

internal val SampleCards = listOf(Card.N7, Card.N12, Card.N3, Card.TIMES_2, Card.PLUS_4, Card.FREEZE).map { ConfirmedCard(it) }
internal val SampleBustCards = listOf(Card.N7, Card.N5, Card.N7, Card.PLUS_2).map { ConfirmedCard(it) }

/** Sheet states for previews and the demo: open, scored with cards, bust with cards, bust tapped. */
internal fun sampleSheetStates(): List<PlayerSheetUi> {
    val p = samplePlayers(4)
    return listOf(
        PlayerSheetUi(p[2], 4),
        PlayerSheetUi(p[0].copy(status = PlayerStatus.Scored(48)), 4, SampleCards),
        PlayerSheetUi(p[1], 4, SampleBustCards),
        PlayerSheetUi(p[1], 4),
    )
}

@Preview(widthDp = 390)
@Composable
private fun PlayerSheetOpenPreview() {
    Scan7Theme { BottomSheetPanel { PlayerSheetContent(sampleSheetStates()[0], {}, {}, {}, {}, {}, {}) } }
}

@Preview(widthDp = 390)
@Composable
private fun PlayerSheetScoredPreview() {
    Scan7Theme { BottomSheetPanel { PlayerSheetContent(sampleSheetStates()[1], {}, {}, {}, {}, {}, {}) } }
}

@Preview(widthDp = 390)
@Composable
private fun PlayerSheetBustPreview() {
    Scan7Theme { BottomSheetPanel(Modifier.width(390.dp)) { PlayerSheetContent(sampleSheetStates()[2], {}, {}, {}, {}, {}, {}) } }
}

@Preview(widthDp = 390)
@Composable
private fun PlayerSheetAdjustPreview() {
    val state = sampleSheetStates()[1].copy(
        adjustments = listOf(
            ScoreAdjustment(1, 1, 10, "penalty", 2),
            ScoreAdjustment(2, 1, -12, null, 3),
        ),
    )
    Scan7Theme {
        BottomSheetPanel {
            PlayerSheetContent(state, {}, {}, {}, {}, {}, {})
            Spacer(Modifier.height(Scan7Spacing.l))
            AdjustSection(state, removed = ScoreAdjustment(3, 1, 5, "house rule", 1), onAdjust = {}, onRemove = {}, onUndo = {})
        }
    }
}
