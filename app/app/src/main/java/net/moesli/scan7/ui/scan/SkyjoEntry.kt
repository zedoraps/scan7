package net.moesli.scan7.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.moesli.scan7.R
import net.moesli.scan7.skyjo.SKYJO_COLUMNS
import net.moesli.scan7.skyjo.SKYJO_SLOTS
import net.moesli.scan7.skyjo.SkyjoCard
import net.moesli.scan7.skyjo.SkyjoGrid
import net.moesli.scan7.skyjo.SkyjoRules
import net.moesli.scan7.skyjo.emptySkyjoGrid
import net.moesli.scan7.skyjo.nextSlotAfter
import net.moesli.scan7.skyjo.sum
import net.moesli.scan7.ui.components.PlayerUi
import net.moesli.scan7.ui.components.PressDim
import net.moesli.scan7.ui.components.PrimaryButton
import net.moesli.scan7.ui.components.Scan7Dialog
import net.moesli.scan7.ui.components.SecondaryButton
import net.moesli.scan7.ui.components.TextLinkButton
import net.moesli.scan7.ui.components.cappedScale
import net.moesli.scan7.ui.components.samplePlayers
import net.moesli.scan7.ui.components.scan7Clickable
import net.moesli.scan7.ui.sheet.DeltaField
import net.moesli.scan7.ui.sheet.SignToggle
import net.moesli.scan7.ui.sheet.filterDeltaInput
import net.moesli.scan7.ui.sheet.flipSign
import net.moesli.scan7.ui.sheet.parseDelta
import net.moesli.scan7.ui.theme.ScoreNumberStyle
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type
import net.moesli.scan7.ui.theme.Unbounded

/** Value pad: −2 … 12 in rows of five. */
private const val PAD_COLUMNS = 5

/** Saves a grid across rotation / process death as card labels ("" = empty slot). */
private val GridSaver = listSaver<SkyjoGrid, String>(
    save = { grid -> grid.map { it?.label.orEmpty() } },
    restore = { labels -> labels.map { SkyjoCard.fromLabel(it) } },
)

/**
 * Skyjo entry (no detector yet): the player's 3 × 4 grid, row by row as on the table. Tap a slot to
 * select it, then a value on the pad (−2 … 12) fills it and selects the next empty slot; "Empty"
 * clears it (a removed column stays empty). The sum updates live; Save stores the cards with their
 * slots ([onSave]). "Total only" types the round's points without cards ([onSaveTotal]).
 */
@Composable
internal fun SkyjoEntryStep(
    player: PlayerUi,
    start: SkyjoGrid,
    onSave: (SkyjoGrid) -> Unit,
    onSaveTotal: (Int) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    afterScan: AfterScanUi? = null,
) {
    val colors = Scan7Theme.colors
    var grid by rememberSaveable(start, stateSaver = GridSaver) { mutableStateOf(start) }
    var selected by rememberSaveable(start) { mutableStateOf<Int?>(start.indexOfFirst { it == null }.takeIf { it >= 0 } ?: 0) }
    var typingTotal by rememberSaveable { mutableStateOf(false) }
    val sum = grid.sum
    val filled = grid.count { it != null }

    BoxWithConstraints(modifier.fillMaxSize().background(colors.ground), contentAlignment = Alignment.TopCenter) {
        // Short screens: smaller tiles, so the grid, the pad and Save fit without scrolling.
        val compact = maxHeight < 760.dp
        val padHeight = if (compact) Scan7Spacing.minTouch else 56.dp
        Column(modifier = Modifier.fillMaxHeight().widthIn(max = Scan7Spacing.maxContentWidth).fillMaxWidth()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = Scan7Spacing.screenMargin, vertical = Scan7Spacing.s)
                    .heightIn(min = Scan7Spacing.headerHeight),
            ) {
                PlayerHeading(player, stringResource(R.string.manual_cards_for), Modifier.weight(1f))
                SecondaryButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onCancel,
                    height = Scan7Spacing.minTouch,
                    cornerRadius = Scan7Radii.iconButton,
                    textStyle = Scan7Type.button.copy(fontSize = 13.sp),
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Scan7Spacing.screenMargin, vertical = Scan7Spacing.s),
            ) {
                Label(stringResource(R.string.skyjo_grid))
                GridSlots(grid, selected, compact) { selected = it }
                Text(stringResource(R.string.skyjo_grid_hint), color = colors.textMuted, style = Scan7Type.text12)
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Scan7Spacing.screenMargin, vertical = Scan7Spacing.s),
            ) {
                Label(stringResource(R.string.skyjo_values))
                ValuePad(
                    height = padHeight,
                    enabled = selected != null,
                    onValue = { card ->
                        val slot = selected ?: return@ValuePad
                        val next = grid.toMutableList().apply { this[slot] = card }
                        grid = next
                        selected = next.nextSlotAfter(slot)
                    },
                    onEmpty = {
                        val slot = selected ?: return@ValuePad
                        grid = grid.toMutableList().apply { this[slot] = null }
                    },
                )
            }
            val shape = RoundedCornerShape(topStart = Scan7Radii.cardLarge, topEnd = Scan7Radii.cardLarge)
            Column(
                verticalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(colors.surface)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = Scan7Spacing.screenMargin, end = Scan7Spacing.screenMargin, top = Scan7Spacing.l, bottom = Scan7Spacing.xl),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            pluralStringResource(R.plurals.skyjo_cards_count, filled, filled, SKYJO_SLOTS),
                            color = colors.textMuted,
                            style = Scan7Type.text13,
                        )
                        TextLinkButton(stringResource(R.string.skyjo_total_only), { typingTotal = true })
                    }
                    val pointsDescription = pluralStringResource(R.plurals.points, sum, sum)
                    Text(
                        sum.toString(),
                        color = colors.accent,
                        style = ScoreNumberStyle.copy(fontSize = 40.sp, lineHeight = 44.sp),
                        maxLines = 1,
                        modifier = Modifier.semantics {
                            contentDescription = pointsDescription
                            liveRegion = LiveRegionMode.Polite
                        },
                    )
                }
                ConfirmSplitButton(stringResource(R.string.action_save), { onSave(grid) }, afterScan, Modifier.fillMaxWidth(), enabled = filled > 0)
            }
        }
    }
    if (typingTotal) {
        TotalDialog(
            name = player.name,
            onCancel = { typingTotal = false },
            onSave = {
                typingTotal = false
                onSaveTotal(it)
            },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(),
        color = Scan7Theme.colors.textMuted,
        style = Scan7Type.capsLabel,
        modifier = Modifier.semantics { heading() },
    )
}

/** The 3 × 4 grid: each slot shows its value or a dash (empty); the selected slot has a cyan outline. */
@Composable
private fun GridSlots(grid: SkyjoGrid, selected: Int?, compact: Boolean, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s), modifier = Modifier.fillMaxWidth()) {
        grid.chunked(SKYJO_COLUMNS).forEachIndexed { row, cards ->
            Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s), modifier = Modifier.fillMaxWidth()) {
                cards.forEachIndexed { column, card ->
                    val slot = row * SKYJO_COLUMNS + column
                    Slot(
                        card = card,
                        row = row,
                        column = column,
                        selected = slot == selected,
                        onClick = { onSelect(slot) },
                        modifier = Modifier
                            .weight(1f)
                            // Wider than tall: the three rows, the pad and Save fit a phone without scrolling.
                            .aspectRatio(if (compact) 1.5f else 1.2f),
                    )
                }
            }
        }
    }
}

@Composable
private fun Slot(card: SkyjoCard?, row: Int, column: Int, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.iconButton)
    val value = card?.value?.toString() ?: stringResource(R.string.skyjo_slot_empty)
    val description = stringResource(R.string.skyjo_slot_spoken, row + 1, column + 1, value)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(shape)
            .background(if (card == null) colors.ground else colors.surface)
            .border(if (selected) 2.5.dp else 1.5.dp, if (selected) colors.accent else colors.line, shape)
            .selectable(selected = selected, interactionSource = null, indication = PressDim, role = Role.Tab, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Tab
                this.selected = selected
            },
    ) {
        Text(
            card?.value?.toString() ?: "–",
            color = if (card == null) colors.textMuted else colors.text,
            style = SlotLabel.cappedScale(1.3f),
            maxLines = 1,
        )
    }
}

/** −2 … 12 and "Empty"; a value fills the selected slot. */
@Composable
private fun ValuePad(height: Dp, enabled: Boolean, onValue: (SkyjoCard) -> Unit, onEmpty: () -> Unit) {
    val keys: List<SkyjoCard?> = SkyjoCard.entries + listOf(null)
    Column(verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
        keys.chunked(PAD_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s), modifier = Modifier.fillMaxWidth()) {
                row.forEach { card ->
                    PadKey(
                        text = card?.value?.toString() ?: stringResource(R.string.skyjo_slot_clear),
                        spoken = card?.value?.toString() ?: stringResource(R.string.skyjo_slot_clear_spoken),
                        enabled = enabled,
                        onClick = { if (card != null) onValue(card) else onEmpty() },
                        modifier = Modifier.weight(1f).height(height),
                    )
                }
                repeat(PAD_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PadKey(text: String, spoken: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.iconButton)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(shape)
            .background(colors.surface)
            .border(1.5.dp, colors.line, shape)
            .scan7Clickable(onClick, enabled = enabled)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
            },
    ) {
        BasicText(
            text,
            style = PadLabel.cappedScale(1.3f).copy(color = if (enabled) colors.text else colors.textMuted),
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = PadLabel.cappedScale(1.3f).fontSize),
            modifier = Modifier.padding(horizontal = Scan7Spacing.xxs),
        )
    }
}

/** "Total only": the round's points typed (± for negative, number keyboards often lack a minus). */
@Composable
private fun TotalDialog(name: String, onCancel: () -> Unit, onSave: (Int) -> Unit) {
    val colors = Scan7Theme.colors
    var input by rememberSaveable { mutableStateOf("") }
    val points = parseDelta(input)?.takeIf { it >= SkyjoRules.minPoints }
    val save = { if (points != null) onSave(points) }
    val focus = remember { FocusRequester() }
    // The field takes focus at once: the keyboard opens and Done saves.
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Scan7Dialog(onDismiss = onCancel) {
        Text(
            stringResource(R.string.skyjo_total_title, name),
            color = colors.text,
            style = Scan7Type.buttonName,
            modifier = Modifier.semantics { heading() },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            SignToggle(onClick = { input = flipSign(input) })
            DeltaField(
                input,
                onChange = { input = filterDeltaInput(it) },
                onDone = save,
                errorText = null,
                modifier = Modifier.weight(1f).focusRequester(focus),
                description = stringResource(R.string.skyjo_total_field),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
            SecondaryButton(stringResource(R.string.action_cancel), onCancel, Modifier.weight(1f), height = 56.dp, textStyle = Scan7Type.button.copy(fontSize = 14.sp))
            PrimaryButton(stringResource(R.string.action_save), { save() }, Modifier.weight(1.4f), enabled = points != null, height = 56.dp, textStyle = Scan7Type.button.copy(fontSize = 14.sp))
        }
    }
}

private val SlotLabel = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
private val PadLabel = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)

@Preview(widthDp = 390, heightDp = 860)
@Composable
private fun SkyjoEntryPreview() {
    Scan7Theme {
        val grid = emptySkyjoGrid().toMutableList().apply {
            this[0] = SkyjoCard.N12
            this[1] = SkyjoCard.M2
            this[5] = SkyjoCard.N4
        }
        SkyjoEntryStep(samplePlayers(1).first(), grid, {}, {}, {})
    }
}
