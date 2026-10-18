package net.moesli.scan7.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.moesli.scan7.scoring.Card
import net.moesli.scan7.scoring.CardKind
import net.moesli.scan7.scoring.scoreRow
import net.moesli.scan7.scoring.secondChanceText
import net.moesli.scan7.ui.components.PlayerUi
import net.moesli.scan7.ui.components.PressDim
import net.moesli.scan7.ui.components.SecondaryButton
import net.moesli.scan7.ui.components.StatusTag
import net.moesli.scan7.ui.components.PlayerStatus
import net.moesli.scan7.ui.theme.ScoreNumberStyle
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type
import net.moesli.scan7.ui.theme.Unbounded

private const val NUMBER_COLUMNS = 5
private const val MODIFIER_COLUMNS = 6

/** Same dimming as disabled buttons (ui/components). */
private const val DISABLED_TOGGLE_ALPHA = 0.4f

/**
 * Manual entry: selected cards as chips (tap to remove), toggle grid 0–12 and +2 … +10, ×2 (each
 * at most once), ignored cards from the scan greyed (info only), live breakdown + total, Save.
 * If prefilled duplicates remain (Fix after a bust scan), Save becomes "Confirm bust"; their grid
 * toggle shows "×2" and removes one copy per tap (see [toggleCard]).
 * At most [MAX_NUMBER_CARDS] number cards: at 7 the other number toggles are disabled; more than 7
 * (prefilled by Fix) shows a warning and disables Save until enough are removed.
 */
@Composable
internal fun ManualEntryStep(
    player: PlayerUi,
    start: SplitCards,
    onSave: (ScanOutcome) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    afterScan: AfterScanUi? = null,
) {
    val colors = Scan7Theme.colors
    var selected by rememberSaveable(start) { mutableStateOf(start.scoring) }
    val ignored = start.ignored
    // Ignored cards add nothing, but a Second Chance among them cancels a duplicate.
    val row = selected + ignored
    val score = scoreRow(row)
    val duplicates = bustDuplicates(row)
    val overLimit = overNumberLimit(row)
    val limitHint = numberLimitHint(row)

    // Tablets: phone width, centred. Short screens / large fonts: the denser layout (see manualDense).
    BoxWithConstraints(modifier.fillMaxSize().background(colors.ground), contentAlignment = Alignment.TopCenter) {
        val dense = manualDense(maxHeight, LocalDensity.current.fontScale)
        val gridGap = if (dense) Scan7Spacing.s else Scan7Spacing.l
        // Dense: 40 dp chips (still above the 36 dp compact touch minimum) and a one-line info row,
        // so a second chip line stays visible on 16:9 phones.
        val chipHeight = if (dense) 40.dp else 48.dp
        val statusHeight = if (dense) DenseStatusHeight else ManualStatusHeight
        Column(modifier = Modifier.fillMaxHeight().widthIn(max = Scan7Spacing.maxContentWidth).fillMaxWidth()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = Scan7Spacing.screenMargin, vertical = Scan7Spacing.s)
                    .heightIn(min = Scan7Spacing.headerHeight), // grows with large font scales
            ) {
                PlayerHeading(player, "Cards for", Modifier.weight(1f))
                SecondaryButton(
                    text = "Cancel",
                    onClick = onCancel,
                    height = Scan7Spacing.minTouch,
                    cornerRadius = Scan7Radii.iconButton,
                    textStyle = Scan7Type.button.copy(fontSize = 13.sp),
                )
            }
            // Chips at the top, the grids anchored at the bottom right above the panel: extra chip
            // lines grow into the free space between them (and scroll when it runs out), so the grid
            // buttons never move under the finger.
            Column(
                verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Scan7Spacing.screenMargin, vertical = Scan7Spacing.s),
            ) {
                SectionLabel("In the row")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s),
                    modifier = Modifier.fillMaxWidth().heightIn(min = chipHeight),
                ) {
                    if (selected.isEmpty() && ignored.isEmpty()) {
                        Text(
                            "Tap the cards below to add them",
                            color = colors.textMuted,
                            style = Scan7Type.text13,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                    selected.forEachIndexed { index, card ->
                        CardChip(
                            card = card,
                            look = if (isDuplicate(card, duplicates)) ChipLook.Duplicate else ChipLook.Normal,
                            height = chipHeight,
                            onClick = { selected = removeChipAt(selected, index) },
                            clickLabel = "Remove",
                        )
                    }
                    ignored.forEach { CardChip(it, ChipLook.Ignored, height = chipHeight) }
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(gridGap),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Scan7Spacing.screenMargin, end = Scan7Spacing.screenMargin, top = Scan7Spacing.s, bottom = gridGap),
            ) {
                SectionLabel("Numbers")
                // Dense: 0–6 over 7–12 (two rows instead of three).
                CardGrid(ManualNumbers, if (dense) DENSE_NUMBER_COLUMNS else NUMBER_COLUMNS, selected, ignored, score.bust, dense) {
                    selected = toggleCard(selected, it, ignored)
                }
                // Reserved line, so the hint appearing at 7 numbers doesn't move the number grid.
                Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.CenterStart) {
                    if (limitHint != null) {
                        BasicText(
                            limitHint,
                            style = Scan7Type.text13.copy(color = if (overLimit) colors.accent else colors.textMuted),
                            maxLines = 1,
                            softWrap = false,
                            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = Scan7Type.text13.fontSize),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
                SectionLabel("Modifiers")
                CardGrid(ManualModifiers, MODIFIER_COLUMNS, selected, ignored, score.bust, dense) { selected = toggleCard(selected, it, ignored) }
            }
            val shape = RoundedCornerShape(topStart = Scan7Radii.cardLarge, topEnd = Scan7Radii.cardLarge)
            Column(
                verticalArrangement = Arrangement.spacedBy(if (dense) Scan7Spacing.s else Scan7Spacing.l),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(colors.surface)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(
                        start = Scan7Spacing.screenMargin,
                        end = Scan7Spacing.screenMargin,
                        top = if (dense) Scan7Spacing.s else Scan7Spacing.l,
                        bottom = if (dense) Scan7Spacing.l else Scan7Spacing.xl,
                    ),
            ) {
                // Fixed height: adding / removing cards (bust ↔ not bust, Second Chance, the 7-card
                // warning) never moves the Save button.
                Column(Modifier.fillMaxWidth().height(statusHeight)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        if (score.bust) {
                            // Fixed-height status: large font scales shrink the text instead of clipping it.
                            BasicText(
                                "${duplicateSummary(duplicates)} – remove one, or confirm the bust.",
                                style = Scan7Type.text13.copy(color = colors.textMuted),
                                // Bounded by the 52 dp row: shrinks until all lines fit (no maxLines cut).
                                autoSize = TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = Scan7Type.text13.fontSize),
                                modifier = Modifier.weight(1f),
                            )
                            StatusTag(PlayerStatus.Bust, height = 36.dp)
                        } else {
                            Breakdown(row, Modifier.weight(1f), maxLines = 2)
                            Text(
                                score.total.toString(),
                                color = colors.accent,
                                style = ScoreNumberStyle.copy(fontSize = 40.sp, lineHeight = 44.sp),
                                maxLines = 1,
                                modifier = Modifier.semantics {
                                    contentDescription = "${score.total} points"
                                    liveRegion = LiveRegionMode.Polite
                                },
                            )
                        }
                    }
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.CenterStart) {
                        val secondChance = score.secondChanceText()
                        if (overLimit) {
                            // Fix prefilled more than 7 numbers: no real row has that many, so saving waits.
                            BasicText(
                                "More than $MAX_NUMBER_CARDS number cards – remove ${rowNumberCount(row) - MAX_NUMBER_CARDS} to save",
                                style = Scan7Type.text12.copy(color = colors.accent),
                                autoSize = TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = Scan7Type.text12.fontSize),
                            )
                        } else if (secondChance != null) {
                            SecondChanceInfo(secondChance)
                        }
                    }
                }
                val save = { onSave(manualOutcome(selected, ignored)) }
                if (score.bust) {
                    ConfirmSplitButton("Confirm bust", save, afterScan, Modifier.fillMaxWidth(), enabled = !overLimit, color = colors.bust)
                } else {
                    ConfirmSplitButton("Save", save, afterScan, Modifier.fillMaxWidth(), enabled = !overLimit)
                }
            }
        }
    }
}

/** Status area above Save: breakdown + total (or the bust text) and one info line. */
private val ManualStatusHeight = 88.dp
private val DenseStatusHeight = 72.dp

/** Dense layout: number grid in 7 columns (two rows). */
private const val DENSE_NUMBER_COLUMNS = 7

/**
 * Dense layout (two-row number grid, 48 dp toggles, tighter gaps) when the regular one wouldn't
 * leave room for the "In the row" chips: screens under ~800 dp high (16:9 phones), more with large
 * font scales (the labels and panel texts grow).
 */
internal fun manualDense(screenHeight: Dp, fontScale: Float): Boolean =
    screenHeight < 800.dp + 120.dp * (fontScale - 1f).coerceAtLeast(0f)

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = Scan7Theme.colors.textMuted,
        style = Scan7Type.capsLabel,
        modifier = Modifier.semantics { heading() },
    )
}

/** Toggle grid in rows of [columns]; a short last row keeps the column width. */
@Composable
private fun CardGrid(
    cards: List<Card>,
    columns: Int,
    selected: List<Card>,
    ignored: List<Card>,
    bust: Boolean,
    dense: Boolean,
    onToggle: (Card) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
        cards.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s), modifier = Modifier.fillMaxWidth()) {
                row.forEach { card ->
                    GridToggle(
                        card = card,
                        count = selectedCount(selected, card),
                        bust = bust,
                        enabled = isGridCardEnabled(selected, card, ignored),
                        onToggle = { onToggle(card) },
                        modifier = Modifier
                            .weight(1f)
                            .height(if (dense) Scan7Spacing.minTouch else 56.dp),
                    )
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * One grid toggle for [count] selected copies. Off: `surface` tile with a line border and the
 * card's echo dot. On: cyan fill without the border and a dark label (ground on accent, ~10:1),
 * so on and off differ in fill and outline, not only in hue; TalkBack gets the checkbox state. Several copies (a duplicate from a scan): "×2" badge, and while the row is [bust] a
 * magenta outline like the duplicate chips (a Second Chance that cancels it keeps the cyan look);
 * a tap removes one copy. Disabled (7 number cards selected): dimmed, not tappable, TalkBack
 * state "7 number cards selected" + disabled.
 */
@Composable
private fun GridToggle(
    card: Card,
    count: Int,
    bust: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.iconButton)
    val modifierCard = card.kind != CardKind.NUMBER
    val on = count > 0
    val several = count >= 2
    val duplicate = several && bust
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .alpha(if (enabled) 1f else DISABLED_TOGGLE_ALPHA)
            .clip(shape)
            .background(
                when {
                    duplicate -> colors.bust.copy(alpha = 0.16f)
                    on -> colors.accent
                    else -> colors.surface
                },
            )
            .then(
                when {
                    duplicate -> Modifier.border(2.5.dp, colors.bust, shape)
                    on -> Modifier
                    else -> Modifier.border(1.5.dp, if (modifierCard) ModifierLine else colors.line, shape)
                },
            )
            .toggleable(
                value = on,
                enabled = enabled,
                interactionSource = null,
                indication = PressDim,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            )
            .clearAndSetSemantics {
                contentDescription = card.spokenLabel()
                gridCountState(count)?.let { stateDescription = it }
                if (!enabled) {
                    stateDescription = NUMBER_LIMIT_STATE
                    disabled()
                }
            },
    ) {
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(x = 6.dp, y = 6.dp)
                .size(6.dp)
                .background(card.echoDot(), CircleShape),
        )
        if (several) {
            Box(Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 4.dp)) {
                Text("×$count", color = if (duplicate) colors.bust else colors.onAccent, style = Scan7Type.tag12)
            }
        }
        // Narrow tiles (dense grid, small phones) and large font scales: "+10" shrinks, never clips.
        BasicText(
            card.chipLabel(),
            style = GridLabel.copy(
                color = when {
                    duplicate -> colors.bust
                    on -> colors.onAccent
                    modifierCard -> ModifierInk
                    else -> colors.text
                },
            ),
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = GridLabel.fontSize),
            modifier = Modifier.padding(horizontal = Scan7Spacing.xxs),
        )
    }
}

/** Accent (not magenta: no bust) info line "Second Chance used · 7 counted once". */
@Composable
internal fun SecondChanceInfo(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Scan7Theme.colors.accent,
        style = Scan7Type.text13,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

private val GridLabel = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
