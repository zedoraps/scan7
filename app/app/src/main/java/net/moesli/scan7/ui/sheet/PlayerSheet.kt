package net.moesli.scan7.ui.sheet

import net.moesli.scan7.ui.text.UiText

import net.moesli.scan7.ui.components.FitText
import net.moesli.scan7.ui.text.chipText
import net.moesli.scan7.ui.text.resolve
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import net.moesli.scan7.R
import net.moesli.scan7.skyjo.skyjoGridOf
import net.moesli.scan7.skyjo.SkyjoGrid
import net.moesli.scan7.skyjo.SkyjoCard
import net.moesli.scan7.skyjo.SKYJO_COLUMNS
import net.moesli.scan7.ui.components.LocalBustAllowed
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.text.TextAutoSize
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
import net.moesli.scan7.ui.scan.FormulaText
import net.moesli.scan7.ui.scan.breakdownFormula
import net.moesli.scan7.ui.components.BottomSheetPanel
import net.moesli.scan7.ui.components.CardSize
import net.moesli.scan7.ui.components.PlayerScoreRing
import net.moesli.scan7.ui.components.PlayerStatus
import net.moesli.scan7.ui.components.PlayerUi
import net.moesli.scan7.ui.components.Scan7BottomSheet
import net.moesli.scan7.ui.components.KeypadIcon
import net.moesli.scan7.ui.components.ScanIcon
import net.moesli.scan7.ui.components.Confirmations
import net.moesli.scan7.ui.components.Scan7ConfirmDialog
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
    /**
     * Multiplayer guest: someone else entered this result ("Scored by Anna's phone"); the sheet
     * then shows it read-only (no Edit / Rescan / Reset: guests only change their own entries).
     */
    val scoredBy: UiText? = null,
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
 * with Edit, Rescan and Reset last entry (asks first, [Confirmations.resetToOpen]; [onResetToOpen]
 * `null` hides it, e.g. on the end-of-round view: a closed round has no open players). With [adjust]: "Adjust total" (signed delta + note,
 * [AdjustTotalDialog]) and the player's adjustments, each removable with Undo. [typeEntry]
 * (entry mode Type): the big button is "Enter manually" with the keypad (no camera, no second
 * manual button) and done players have no Rescan (Edit covers it).
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
    typeEntry: Boolean = false,
) {
    var adjusting by rememberSaveable { mutableStateOf(false) }
    // Last removed adjustment, for the inline Undo (cleared when the sheet closes).
    var removed by remember { mutableStateOf<ScoreAdjustment?>(null) }
    // "Reset to open" asks first (Scan7ConfirmDialog over the sheet; the sheet's layout stays).
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    // Actions slide the sheet down first (hideThen); "Adjust total" swaps it for the dialog.
    if (!adjusting) Scan7BottomSheet(onDismiss = onDismiss, modifier = modifier) { sheet ->
        Column(Modifier.verticalScroll(rememberScrollState())) {
            val resetSpec = Confirmations.resetToOpen(state.player.name, state.player.status)
            PlayerSheetContent(
                state,
                { sheet.hideThen(onScan) },
                { sheet.hideThen(onEnterManually) },
                { sheet.hideThen(onBust) },
                { sheet.hideThen(onEdit) },
                { sheet.hideThen(onRescan) },
                onResetToOpen?.let { reset -> { if (resetSpec == null) sheet.hideThen(reset) else confirmReset = true } },
                typeEntry = typeEntry,
            )
            if (confirmReset && onResetToOpen != null && resetSpec != null) {
                Scan7ConfirmDialog(
                    resetSpec,
                    onConfirm = {
                        confirmReset = false
                        sheet.hideThen(onResetToOpen)
                    },
                    onCancel = { confirmReset = false },
                )
            }
            if (adjust != null) {
                Spacer(Modifier.height(Scan7Spacing.l))
                AdjustSection(
                    state = state,
                    removed = removed,
                    onAdjust = { sheet.hideThen({ adjusting = true }, dismiss = false) },
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
            SectionLabel(stringResource(R.string.sheet_adjustments))
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
                    stringResource(R.string.sheet_removed, formatDelta(removed.delta)),
                    color = colors.textMuted,
                    style = Scan7Type.text13,
                    modifier = Modifier.weight(1f),
                )
                TextLinkButton(stringResource(R.string.action_undo), onClick = { onUndo(removed) })
            }
        }
        SecondaryButton(
            stringResource(R.string.sheet_adjust),
            onAdjust,
            Modifier.fillMaxWidth(),
            height = Scan7Spacing.minTouch,
            cornerRadius = Scan7Radii.iconButton,
            textStyle = Scan7Type.button.copy(fontSize = 13.sp),
            contentDescription = stringResource(R.string.sheet_adjust_description, name),
        )
    }
}

@Composable
private fun AdjustmentRow(adjustment: ScoreAdjustment, onRemove: () -> Unit) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.tagRow)
    val spoken = adjustmentDescription(adjustment).resolve()
    val description = stringResource(R.string.sheet_adjustment_description, spoken)
    val removeDescription = stringResource(R.string.sheet_remove_adjustment, spoken)
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
                .clearAndSetSemantics { contentDescription = description },
        ) {
            Text(formatDelta(adjustment.delta), color = colors.text, style = Scan7Type.total15, maxLines = 1)
            Text(
                adjustmentDetail(adjustment).resolve(),
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
                    contentDescription = removeDescription
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
    typeEntry: Boolean = false,
) {
    val player = state.player
    Column(verticalArrangement = Arrangement.spacedBy(Scan7Spacing.l)) {
        SheetHeader(state)
        if (!player.status.isDone) {
            OpenActions(player.name, onScan, onEnterManually, onBust, typeEntry)
        } else {
            ResultPanel(state)
            state.scoredBy?.let { by ->
                Text(by.resolve(), color = Scan7Theme.colors.textMuted, style = Scan7Type.text15)
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
                SecondaryButton(
                    stringResource(R.string.action_edit),
                    onEdit,
                    Modifier.weight(1f),
                    height = 56.dp,
                    contentDescription = stringResource(R.string.sheet_edit_cards, player.name),
                )
                if (!typeEntry) {
                    SecondaryButton(
                        stringResource(R.string.sheet_rescan),
                        onRescan,
                        Modifier.weight(1f),
                        height = 56.dp,
                        contentDescription = stringResource(R.string.sheet_rescan_player, player.name),
                    )
                }
            }
            if (onResetToOpen != null) {
                SecondaryButton(
                    stringResource(R.string.sheet_reset),
                    onResetToOpen,
                    Modifier.fillMaxWidth(),
                    height = Scan7Spacing.minTouch,
                    cornerRadius = Scan7Radii.iconButton,
                    textStyle = Scan7Type.button.copy(fontSize = 13.sp),
                    contentDescription = stringResource(R.string.sheet_reset_description, player.name),
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
            FitText(player.name, Scan7Type.buttonName, color = colors.text, minSize = 12.dp)
            Text(
                stringResource(R.string.sheet_points_round, pluralStringResource(R.plurals.points, player.total, player.total), state.round),
                color = colors.textMuted,
                style = Scan7Type.text12,
            )
        }
    }
}

@Composable
private fun OpenActions(name: String, onScan: () -> Unit, onEnterManually: () -> Unit, onBust: () -> Unit, typeEntry: Boolean) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.button)
    val scanDescription = stringResource(if (typeEntry) R.string.action_enter_player else R.string.action_scan_player, name)
    val bustDescription = stringResource(R.string.action_bust_player, name)
    Column(verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(shape)
                .background(colors.accent)
                .scan7Clickable(if (typeEntry) onEnterManually else onScan)
                .clearAndSetSemantics {
                    contentDescription = scanDescription
                    role = Role.Button
                },
        ) {
            if (typeEntry) KeypadIcon(colors.onAccent, size = 28.dp) else ScanIcon(colors.onAccent, size = 28.dp)
            Text(
                stringResource(if (typeEntry) R.string.action_enter_manually else R.string.action_scan),
                color = colors.onAccent,
                style = Scan7Type.button.copy(fontSize = 18.sp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
            if (!typeEntry) {
                SecondaryButton(
                    // Large font scales: two lines (the button grows) instead of a tiny or cut label.
                    stringResource(if (LocalDensity.current.fontScale > 1.4f) R.string.action_enter_manually_two_lines else R.string.action_enter_manually),
                    onEnterManually,
                    Modifier.weight(1f),
                    height = 56.dp,
                    textStyle = Scan7Type.button.copy(fontSize = 14.sp),
                )
            }
            if (!LocalBustAllowed.current) return@Row
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .clip(shape)
                    .border(1.5.dp, colors.bust, shape)
                    .scan7Clickable(onBust)
                    .clearAndSetSemantics {
                        contentDescription = bustDescription
                        role = Role.Button
                    }
                    .padding(horizontal = Scan7Spacing.s),
            ) {
                // One line at any font scale: shrinks instead of wrapping into the clipped second line.
                val style = Scan7Type.tag12.copy(fontSize = 14.sp, color = colors.bust)
                BasicText(
                    stringResource(R.string.label_bust_tag),
                    style = style,
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = style.fontSize),
                )
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
    val skyjo = state.cards.any { it.card is SkyjoCard }
    val score = if (state.cards.isEmpty() || skyjo) null else scoreRow(counted)
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
                    Text(if (status.points < 0) formatDelta(status.points) else "+${status.points}", color = colors.accent, style = Scan7Type.roundNumberList)
                    Text(stringResource(R.string.sheet_points_this_round), color = colors.textMuted, style = Scan7Type.text12, modifier = Modifier.padding(bottom = 2.dp))
                }
                else -> {
                    Text(stringResource(R.string.label_bust_tag), color = colors.bust, style = Scan7Type.roundNumberList)
                    Text(stringResource(R.string.sheet_zero_points_this_round), color = colors.textMuted, style = Scan7Type.text12, modifier = Modifier.padding(bottom = 2.dp))
                }
            }
        }
        if (skyjo) {
            SkyjoGridSummary(skyjoGridOf(state.cards))
        } else if (score != null) {
            if (score.bust) {
                Text(
                    breakdownLine(counted.filter { it.kind == CardKind.NUMBER }.map { it.value!! }, score).resolve(),
                    color = colors.bust,
                    style = Scan7Type.text12,
                )
            } else {
                // Same score formula as the review / manual entry: "(6 + 7 + 8) × 2 + 4 = 46".
                FormulaText(breakdownFormula(counted), style = Scan7Type.text12, joinLines = true)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.xs), verticalArrangement = Arrangement.spacedBy(Scan7Spacing.xs)) {
                state.cards.forEach { c -> (c.card as? Card)?.let { CardChip(it, c.ignored, duplicate = it in duplicates) } }
            }
        } else {
            Text(
                stringResource(if (status == PlayerStatus.Bust) R.string.sheet_marked_bust else R.string.sheet_points_only),
                color = colors.textMuted,
                style = Scan7Type.text12,
            )
        }
    }
}

/** Skyjo: the entered grid, 3 rows of 4 values ("–" = empty), silent for TalkBack (the points say it). */
@Composable
private fun SkyjoGridSummary(grid: SkyjoGrid) {
    val colors = Scan7Theme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Scan7Spacing.xxs), modifier = Modifier.clearAndSetSemantics {}) {
        grid.chunked(SKYJO_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.xs)) {
                row.forEach { card ->
                    Text(
                        card?.value?.toString() ?: "–",
                        color = if (card == null) colors.textMuted else colors.text,
                        style = Scan7Type.total15,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.width(36.dp),
                    )
                }
            }
        }
    }
}

/**
 * Card chip: number / modifier normal; duplicates outlined magenta; action cards and backs (and a
 * Second Chance that cancelled a duplicate) only their name, dashed and muted, like the review /
 * manual chips (no "ignored" word; TalkBack: "Freeze, doesn't score").
 */
@Composable
fun CardChip(card: Card, ignored: Boolean, modifier: Modifier = Modifier, duplicate: Boolean = false) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.tagRow)
    val label = card.chipText().resolve()
    val description = when {
        ignored -> stringResource(R.string.chip_ignored, label)
        duplicate -> stringResource(R.string.sheet_card_duplicate_description, label)
        else -> label
    }
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
            .semantics { contentDescription = description },
    ) {
        Text(
            label,
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
