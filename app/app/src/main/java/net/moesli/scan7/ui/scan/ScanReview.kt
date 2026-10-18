package net.moesli.scan7.ui.scan

import net.moesli.scan7.ui.text.resolve
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import net.moesli.scan7.R
import net.moesli.scan7.ui.components.avatarSizeForHeight
import net.moesli.scan7.ui.components.PlayerAvatar
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.moesli.scan7.detection.Detection
import net.moesli.scan7.detection.DisplayMapping
import net.moesli.scan7.scoring.Card
import net.moesli.scan7.training.TrainingCapture
import net.moesli.scan7.scoring.scoreRow
import net.moesli.scan7.ui.text.secondChanceText
import net.moesli.scan7.ui.components.IconButton
import net.moesli.scan7.ui.components.PlayerUi
import net.moesli.scan7.ui.components.PrimaryButton
import net.moesli.scan7.ui.components.SecondaryButton
import net.moesli.scan7.ui.components.scan7Clickable
import net.moesli.scan7.ui.theme.ScoreNumberStyle
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/** A frozen frame (upright) with the detector result on it; [error] if the detector failed. */
@Immutable
class ScanCapture(
    val bitmap: Bitmap,
    val detections: List<Detection>,
    val error: String? = null,
    val meta: CaptureMeta = CaptureMeta(),
) {
    /** Recognized cards with their detection index (unknown labels skipped), in detection order. */
    val detectedCards: List<DetectedCard> =
        detections.mapIndexedNotNull { i, d -> Card.fromLabel(d.label)?.let { DetectedCard(i, it) } }

    /** Recognized cards, scoring and ignored, in detection order. */
    val cards: List<Card> = detectedCards.map { it.card }
}

/** How a [ScanCapture] was made (stored with "Save scans for training"). */
@Immutable
data class CaptureMeta(
    /** "camera", or "photo" / "synthetic" for debug captures. */
    val origin: String = "camera",
    val rotationDegrees: Int? = null,
    val zoomRatio: Float? = null,
    val backend: String? = null,
    val threshold: Float? = null,
    /** Best-frame shutter ([net.moesli.scan7.camera.FrameChoice]); null for debug captures. */
    val choice: TrainingCapture? = null,
)

/**
 * Review step: the frozen frame with boxes and the result panel (scored / bust / nothing found).
 * Tapping a box or its chip leaves that card out ([excluded], e.g. a neighbour's card in the
 * frame) or includes it again ([onToggleExclude]); score, bust check and outcome ignore it.
 */

/** Review / manual panel header avatar (40 dp tall card). */
private val ReviewAvatar = avatarSizeForHeight(40.dp)

@Composable
internal fun ScanReviewStep(
    player: PlayerUi,
    capture: ScanCapture,
    roundNumber: Int?,
    onRetake: () -> Unit,
    onFix: () -> Unit,
    onEnterManually: () -> Unit,
    onConfirm: (ScanOutcome) -> Unit,
    modifier: Modifier = Modifier,
    excluded: Set<Int> = emptySet(),
    onToggleExclude: (Int) -> Unit = {},
    afterScan: AfterScanUi? = null,
) {
    val colors = Scan7Theme.colors
    val detected = capture.detectedCards
    val variant = reviewVariant(detected, excluded)
    val bust = variant == ReviewVariant.Bust
    val boxes = remember(capture, excluded) { scanBoxes(capture.detections, markDuplicates = true, excluded = excluded) }
    val chips = remember(capture, excluded) { reviewChips(detected, excluded) }
    val included = remember(capture, excluded) { includedCards(detected, excluded) }
    val overLimit = reviewOverNumberLimit(detected, excluded)
    val confirm = { if (!overLimit) onConfirm(reviewOutcome(detected, excluded)) }
    val minTouchPx = with(LocalDensity.current) { Scan7Spacing.minTouch.toPx() }
    val toggle by rememberUpdatedState(onToggleExclude)
    val currentBoxes by rememberUpdatedState(boxes)

    Column(modifier = modifier.fillMaxSize().background(colors.ground)) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Image(
                bitmap = remember(capture) { capture.bitmap.asImageBitmap() },
                contentDescription = stringResource(R.string.review_photo, player.name),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            // Dim the photo so boxes and labels read better (more on bust: the panel is the message).
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (bust) 0.45f else 0.2f)))
            ScanBoxesOverlay(
                boxes = boxes,
                imageWidth = capture.bitmap.width,
                imageHeight = capture.bitmap.height,
                crop = false,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(capture) {
                        // Tap a box: leave that card out / include it again (smallest box wins).
                        detectTapGestures { tap ->
                            val mapping = DisplayMapping.of(
                                capture.bitmap.width, capture.bitmap.height, size.width.toFloat(), size.height.toFloat(), crop = false,
                            )
                            val hits = currentBoxes.map { b ->
                                val v = mapping.toView(b.box)
                                HitBox(b.index, v.left, v.top, v.right, v.bottom)
                            }
                            pickBox(hits, tap.x, tap.y, minTouchPx)?.let(toggle)
                        }
                    },
            )
            IconButton(
                onClick = onRetake,
                contentDescription = stringResource(R.string.review_back_to_camera),
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(Scan7Spacing.screenMargin),
            ) { BackIcon(colors.text) }
        }
        val shape = RoundedCornerShape(topStart = Scan7Radii.cardLarge, topEnd = Scan7Radii.cardLarge)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surface)
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            when (variant) {
                ReviewVariant.Scored, ReviewVariant.Bust ->
                    ResultPanel(player, included, chips, roundNumber, bust, overLimit, onRetake, onFix, confirm, onToggleExclude, afterScan)
                ReviewVariant.Empty -> EmptyPanel(player, capture.error, onRetake, onEnterManually)
            }
        }
    }
}

/**
 * Result panel for a scored or busted scan. Its height doesn't depend on the variant: a fixed
 * [StatusSlot] (bust band or the normal header, plus one status line: the 7-card warning or the
 * Second Chance line) and a fixed [PointsLine], so leaving cards out / including them again never
 * moves the chips or buttons, also when the row flips between bust and not bust.
 */
@Composable
private fun ResultPanel(
    player: PlayerUi,
    cards: List<Card>,
    chips: List<ReviewChip>,
    roundNumber: Int?,
    bust: Boolean,
    overLimit: Boolean,
    onRetake: () -> Unit,
    onFix: () -> Unit,
    onConfirm: () -> Unit,
    onToggleExclude: (Int) -> Unit,
    afterScan: AfterScanUi?,
) {
    val colors = Scan7Theme.colors
    StatusSlot(player, cards, roundNumber, bust, overLimit)
    PanelContent {
        PointsLine(cards, bust)
        ReviewChips(chips, onToggleExclude)
        Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s), modifier = Modifier.fillMaxWidth()) {
            if (bust) {
                SecondaryButton(stringResource(R.string.review_fix), onFix, Modifier.width(120.dp))
                ConfirmSplitButton(
                    stringResource(R.string.review_confirm_bust), onConfirm, afterScan, Modifier.weight(1f), enabled = !overLimit, color = colors.bust, stacked = true,
                )
            } else {
                RetakeButton(onRetake)
                SecondaryButton(stringResource(R.string.review_fix), onFix, Modifier.width(80.dp))
                ConfirmSplitButton(stringResource(R.string.review_ok), onConfirm, afterScan, Modifier.weight(1f), enabled = !overLimit, stacked = true)
            }
        }
    }
}

/** Height of the review's status slot (bust band / header + status line). */
private val StatusSlotHeight = 112.dp

/** Height of the status line inside the slot (7-card warning, Second Chance, duplicates). */
private val StatusLineHeight = 40.dp

/** Height of the line under the slot (breakdown, or the struck-through points of a bust). */
private val PointsLineHeight = 44.dp

/**
 * The status slot, [StatusSlotHeight] high, edge to edge at the panel top. Bust: magenta band
 * "✕ BUST" + "Two 7s in the row" and the player (never color alone). Otherwise: token, name,
 * "Round 4 · 96 → 140", the total big in cyan (+ Flip 7 badge). The second line holds the 7-card
 * warning or "Second Chance used · 7 counted once" (else stays empty).
 */
@Composable
private fun StatusSlot(player: PlayerUi, cards: List<Card>, roundNumber: Int?, bust: Boolean, overLimit: Boolean) {
    val colors = Scan7Theme.colors
    val score = scoreRow(cards) // incl. action cards: a Second Chance cancels a duplicate
    val ink = if (bust) colors.onBust else colors.text
    Column(
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .height(StatusSlotHeight)
            .background(if (bust) colors.bust else Color.Transparent)
            .padding(start = Scan7Spacing.screenMargin, end = Scan7Spacing.screenMargin, top = Scan7Spacing.l, bottom = Scan7Spacing.s),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (bust) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).semantics(mergeDescendants = true) { heading() },
                ) {
                    CloseIcon(ink, size = 32.dp, strokeWidth = 3.2f)
                    Text(stringResource(R.string.label_bust), color = ink, style = Scan7Type.total24.copy(fontSize = 28.sp), maxLines = 1)
                }
                Text(player.name, color = ink, style = Scan7Type.tag12.copy(fontSize = 13.sp), maxLines = 1)
                PlayerAvatar(player.avatar, ReviewAvatar)
            } else {
                PlayerAvatar(player.avatar, ReviewAvatar)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                    Text(player.name, color = ink, style = Scan7Type.button.copy(fontSize = 17.sp), maxLines = 1)
                    val progress = "${player.total} → ${player.total + score.total}"
                    Text(
                        text = roundNumber?.let { stringResource(R.string.review_round_progress, it, progress) } ?: progress,
                        color = colors.textMuted,
                        style = Scan7Type.text12,
                        maxLines = 1,
                    )
                }
                if (score.flip7Bonus > 0) Flip7Badge(score.flip7Bonus)
                val pointsDescription = pluralStringResource(R.plurals.points, score.total, score.total)
                Text(
                    text = score.total.toString(),
                    color = colors.accent,
                    style = ScoreNumberStyle.copy(fontSize = 40.sp, lineHeight = 48.sp),
                    maxLines = 1,
                    modifier = Modifier.semantics { contentDescription = pointsDescription },
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(StatusLineHeight), contentAlignment = Alignment.CenterStart) {
            val secondChance = score.secondChanceText()?.resolve()
            when {
                overLimit -> NumberLimitWarning(if (bust) colors.onBust else colors.accent)
                bust -> Text(
                    stringResource(R.string.review_in_row, duplicateSummary(duplicateCounts(splitCards(cards).scoring)).resolve()),
                    color = ink,
                    style = Scan7Type.tag12.copy(fontSize = 14.sp),
                    maxLines = 1,
                )
                secondChance != null -> SecondChanceInfo(secondChance)
            }
        }
    }
}

/** Fixed-height line under the slot: the breakdown, or (bust) "Round points" with 52 struck through and 0. */
@Composable
private fun PointsLine(cards: List<Card>, bust: Boolean) {
    val colors = Scan7Theme.colors
    Box(Modifier.fillMaxWidth().height(PointsLineHeight), contentAlignment = Alignment.CenterStart) {
        if (bust) {
            val score = scoreRow(splitCards(cards).scoring)
            val zeroBust = stringResource(R.string.review_zero_bust)
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.review_round_points), color = colors.textMuted, style = Scan7Type.text13)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = zeroBust },
                ) {
                    Text(
                        score.totalIgnoringBust().toString(),
                        color = colors.textMuted,
                        style = Scan7Type.text14.copy(textDecoration = TextDecoration.LineThrough),
                    )
                    Text("0", color = colors.bust, style = ScoreNumberStyle.copy(fontSize = 36.sp, lineHeight = 44.sp))
                }
            }
        } else {
            Breakdown(cards, maxLines = 2)
        }
    }
}

/**
 * More than 7 number cards included (a misread or a neighbour's card): accent outline + text, not
 * magenta (it isn't a bust). OK / Confirm bust wait until it fits; Fix stays available.
 */
@Composable
private fun NumberLimitWarning(color: Color) {
    Text(
        REVIEW_NUMBER_LIMIT_WARNING.resolve(),
        color = color,
        style = Scan7Type.text12,
        maxLines = 2,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, color, RoundedCornerShape(Scan7Radii.iconButton))
            .padding(horizontal = Scan7Spacing.m, vertical = Scan7Spacing.xs)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun EmptyPanel(player: PlayerUi, error: String?, onRetake: () -> Unit, onEnterManually: () -> Unit) {
    val colors = Scan7Theme.colors
    PanelContent {
        Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(player.avatar, ReviewAvatar)
            Text(player.name, color = colors.text, style = Scan7Type.button.copy(fontSize = 17.sp), maxLines = 1)
        }
        Column(verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
            Text(
                stringResource(R.string.review_no_cards),
                color = colors.text,
                style = Scan7Type.total24,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = error?.let { stringResource(R.string.review_detector_failed, it) }
                    ?: stringResource(R.string.review_no_cards_hint),
                color = colors.textMuted,
                style = Scan7Type.text13,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton(stringResource(R.string.review_retake), onRetake, Modifier.weight(1f))
            PrimaryButton(stringResource(R.string.action_enter_manually), onEnterManually, Modifier.weight(1.6f))
        }
    }
}

@Composable
private fun PanelContent(content: @Composable ColumnScope.() -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Scan7Spacing.l),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Scan7Spacing.screenMargin, end = Scan7Spacing.screenMargin, top = Scan7Spacing.l, bottom = Scan7Spacing.xl),
        content = content,
    )
}

/** The score formula of [cards] ([breakdownFormula]): "(6 + 7 + 8) × 2" over "+ 4". */
@Composable
internal fun Breakdown(cards: List<Card>, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) =
    FormulaText(breakdownFormula(cards), modifier, maxLines)

/**
 * A score formula (one or two lines, see [breakdownFormula]): numbers in the text color,
 * modifiers in the modifier orange, the Flip 7 bonus in cyan, operators muted. Wraps only after
 * "+" ("× 2" stays together); TalkBack reads the plain spoken version with the total ([spokenFormula]).
 */
@Composable
internal fun FormulaText(
    parts: List<FormulaPart>,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = Scan7Type.text13.copy(lineHeight = 20.sp),
) {
    val colors = Scan7Theme.colors
    val spoken = spokenFormula(parts).resolve()
    Text(
        text = buildAnnotatedString {
            parts.filter { it.kind != FormulaKind.TOTAL }.forEach { p ->
                val color = when (p.kind) {
                    FormulaKind.NUMBER, FormulaKind.TOTAL -> colors.text
                    FormulaKind.MODIFIER -> ModifierInk
                    FormulaKind.BONUS -> colors.accent
                    FormulaKind.OPERATOR -> colors.textMuted
                }
                // Keep "× 2" and "+ 4" together: break only after " + ".
                val text = when (p.text) {
                    " × " -> " ×\u00A0"
                    "+ " -> "+\u00A0"
                    else -> p.text
                }
                withStyle(SpanStyle(color = color)) { append(text) }
            }
        },
        color = colors.text,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.semantics { contentDescription = spoken },
    )
}

/**
 * Review chips: scoring (duplicates outlined magenta), action cards / backs (name only, dashed),
 * excluded (struck through),
 * each a toggle "leave out / include", plus the muted hint "Tap a card to leave it out".
 */
@Composable
private fun ReviewChips(chips: List<ReviewChip>, onToggleExclude: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s),
            modifier = Modifier.fillMaxWidth(),
        ) {
            chips.forEach { chip ->
                val label = chip.card.spokenLabel().resolve()
                val excluded = chip.look == ChipLook.Excluded
                val action = stringResource(if (excluded) R.string.review_include else R.string.review_leave_out, label)
                CardChip(
                    card = chip.card,
                    look = chip.look,
                    onClick = { onToggleExclude(chip.index) },
                    clickLabel = action,
                    actions = listOf(CustomAccessibilityAction(action) { onToggleExclude(chip.index); true }),
                )
            }
        }
        Text(stringResource(R.string.review_tap_hint), color = Scan7Theme.colors.textMuted, style = Scan7Type.text12, maxLines = 1)
    }
}

/** "FLIP 7 +15" badge next to the total (7 different numbers). */
@Composable
private fun Flip7Badge(bonus: Int) {
    val colors = Scan7Theme.colors
    val description = stringResource(R.string.review_flip7_description, bonus)
    Row(
        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(colors.accent, RoundedCornerShape(Scan7Radii.tagRow))
            .padding(horizontal = Scan7Spacing.s, vertical = Scan7Spacing.xxs)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Text(stringResource(R.string.review_flip7), color = colors.onAccent, style = Scan7Type.tag11, maxLines = 1)
        Text("+$bonus", color = colors.onAccent, style = Scan7Type.total15, maxLines = 1)
    }
}

/** Retake: 64 dp square outline button with the circular-arrow icon. */
@Composable
private fun RetakeButton(onClick: () -> Unit) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.button)
    val description = stringResource(R.string.review_retake)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(Scan7Spacing.bottomButtonsHeight)
            .clip(shape)
            .border(1.5.dp, colors.line, shape)
            .scan7Clickable(onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            },
    ) {
        RetakeIcon(colors.text)
    }
}
