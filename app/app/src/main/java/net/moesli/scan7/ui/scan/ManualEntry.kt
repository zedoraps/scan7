package net.moesli.scan7.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.moesli.scan7.scoring.Card
import net.moesli.scan7.scoring.CardKind
import net.moesli.scan7.scoring.scoreRow
import net.moesli.scan7.ui.components.PlayerUi
import net.moesli.scan7.ui.components.PressDim
import net.moesli.scan7.ui.components.PrimaryButton
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

/**
 * Manual entry: selected cards as chips (tap to remove), toggle grid 0–12 and +2 … +10, ×2 (each
 * at most once), ignored cards from the scan greyed (info only), live breakdown + total, Save.
 * If prefilled duplicates remain (Fix after a bust scan), Save becomes "Confirm bust".
 */
@Composable
internal fun ManualEntryStep(
    player: PlayerUi,
    start: SplitCards,
    onSave: (ScanOutcome) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Scan7Theme.colors
    var selected by rememberSaveable(start) { mutableStateOf(start.scoring) }
    val ignored = start.ignored
    val score = scoreRow(selected)
    val duplicates = duplicateCounts(selected)

    Column(modifier = modifier.fillMaxSize().background(colors.ground)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = Scan7Spacing.screenMargin, vertical = Scan7Spacing.s)
                .height(Scan7Spacing.headerHeight),
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
        Column(
            verticalArrangement = Arrangement.spacedBy(Scan7Spacing.l),
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
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
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
                        height = 48.dp,
                        onClick = { selected = removeChipAt(selected, index) },
                        clickLabel = "Remove",
                    )
                }
                ignored.forEach { CardChip(it, ChipLook.Ignored, height = 48.dp) }
            }
            SectionLabel("Numbers")
            CardGrid(ManualNumbers, NUMBER_COLUMNS, selected) { selected = toggleCard(selected, it) }
            SectionLabel("Modifiers")
            CardGrid(ManualModifiers, MODIFIER_COLUMNS, selected) { selected = toggleCard(selected, it) }
        }
        val shape = RoundedCornerShape(topStart = Scan7Radii.cardLarge, topEnd = Scan7Radii.cardLarge)
        Column(
            verticalArrangement = Arrangement.spacedBy(Scan7Spacing.l),
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
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (score.bust) {
                    Text(
                        "${duplicateSummary(duplicates)} – remove one, or confirm the bust.",
                        color = colors.textMuted,
                        style = Scan7Type.text13,
                        modifier = Modifier.weight(1f),
                    )
                    StatusTag(PlayerStatus.Bust, height = 36.dp)
                } else {
                    Breakdown(selected, Modifier.weight(1f))
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
            if (score.bust) {
                PrimaryButton("Confirm bust", { onSave(manualOutcome(selected, ignored)) }, Modifier.fillMaxWidth(), color = colors.bust)
            } else {
                PrimaryButton("Save", { onSave(manualOutcome(selected, ignored)) }, Modifier.fillMaxWidth())
            }
        }
    }
}

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
private fun CardGrid(cards: List<Card>, columns: Int, selected: List<Card>, onToggle: (Card) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
        cards.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s), modifier = Modifier.fillMaxWidth()) {
                row.forEach { card ->
                    GridToggle(card, card in selected, { onToggle(card) }, Modifier.weight(1f))
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * One grid toggle. Off: `surface` tile with a line border and the card's echo dot. On: cyan fill,
 * dark label and a check mark, so the state never relies on color alone.
 */
@Composable
private fun GridToggle(card: Card, on: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.iconButton)
    val modifierCard = card.kind != CardKind.NUMBER
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(56.dp)
            .clip(shape)
            .background(if (on) colors.accent else colors.surface)
            .then(if (on) Modifier else Modifier.border(1.5.dp, if (modifierCard) ModifierLine else colors.line, shape))
            .toggleable(
                value = on,
                interactionSource = null,
                indication = PressDim,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            )
            .clearAndSetSemantics { contentDescription = card.spokenLabel() },
    ) {
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(x = 6.dp, y = 6.dp)
                .size(6.dp)
                .background(card.echoDot(), CircleShape),
        )
        if (on) {
            Box(Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 4.dp)) {
                CheckMark(colors.onAccent)
            }
        }
        Text(
            card.chipLabel(),
            color = when {
                on -> colors.onAccent
                modifierCard -> ModifierInk
                else -> colors.text
            },
            style = GridLabel,
            maxLines = 1,
        )
    }
}

@Composable
private fun CheckMark(color: Color) {
    Text("✓", color = color, style = Scan7Type.tag12)
}

private val GridLabel = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
