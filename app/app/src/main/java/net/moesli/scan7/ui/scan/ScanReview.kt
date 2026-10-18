package net.moesli.scan7.ui.scan

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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.moesli.scan7.detection.Detection
import net.moesli.scan7.scoring.Card
import net.moesli.scan7.training.TrainingCapture
import net.moesli.scan7.scoring.scoreRow
import net.moesli.scan7.ui.components.IconButton
import net.moesli.scan7.ui.components.PlayerToken
import net.moesli.scan7.ui.components.PlayerUi
import net.moesli.scan7.ui.components.PrimaryButton
import net.moesli.scan7.ui.components.SecondaryButton
import net.moesli.scan7.ui.components.TokenSize
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
    /** Recognized cards, scoring and ignored, in detection order. */
    val cards: List<Card> = detections.toCards().map { it.first }
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

/** Review step: the frozen frame with boxes and the result panel (scored / bust / nothing found). */
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
) {
    val colors = Scan7Theme.colors
    val variant = reviewVariant(capture.cards)
    val bust = variant == ReviewVariant.Bust
    val boxes = remember(capture) { scanBoxes(capture.detections, markDuplicates = true) }

    Column(modifier = modifier.fillMaxSize().background(colors.ground)) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Image(
                bitmap = remember(capture) { capture.bitmap.asImageBitmap() },
                contentDescription = "Frozen photo of ${player.name}'s cards",
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
                modifier = Modifier.fillMaxSize(),
            )
            IconButton(
                onClick = onRetake,
                contentDescription = "Back to camera",
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
                ReviewVariant.Scored -> ScoredPanel(player, capture.cards, roundNumber, onRetake, onFix, onConfirm)
                ReviewVariant.Bust -> BustPanel(player, capture.cards, onFix, onConfirm)
                ReviewVariant.Empty -> EmptyPanel(player, capture.error, onRetake, onEnterManually)
            }
        }
    }
}

@Composable
private fun ScoredPanel(
    player: PlayerUi,
    cards: List<Card>,
    roundNumber: Int?,
    onRetake: () -> Unit,
    onFix: () -> Unit,
    onConfirm: (ScanOutcome) -> Unit,
) {
    val colors = Scan7Theme.colors
    val split = splitCards(cards)
    val score = scoreRow(split.scoring)
    PanelContent {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).padding(top = Scan7Spacing.xs),
            ) {
                PlayerToken(player.initial, player.color, TokenSize(30.dp, 40.dp, 14.sp), cornerRadius = 6.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(player.name, color = colors.text, style = Scan7Type.button.copy(fontSize = 17.sp), maxLines = 1)
                    val progress = "${player.total} → ${player.total + score.total}"
                    Text(
                        text = roundNumber?.let { "Round $it · $progress" } ?: progress,
                        color = colors.textMuted,
                        style = Scan7Type.text12,
                        maxLines = 1,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Scan7Spacing.xs)) {
                if (score.flip7Bonus > 0) Flip7Badge(score.flip7Bonus)
                Text(
                    text = score.total.toString(),
                    color = colors.accent,
                    style = ScoreNumberStyle,
                    maxLines = 1,
                    modifier = Modifier.semantics { contentDescription = "${score.total} points" },
                )
            }
        }
        Breakdown(split.scoring)
        Chips(split.scoring, split.ignored, duplicates = emptyMap())
        Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s), modifier = Modifier.fillMaxWidth()) {
            RetakeButton(onRetake)
            SecondaryButton("Fix", onFix, Modifier.width(96.dp))
            PrimaryButton("✓ OK", { onConfirm(reviewOutcome(cards)) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BustPanel(player: PlayerUi, cards: List<Card>, onFix: () -> Unit, onConfirm: (ScanOutcome) -> Unit) {
    val colors = Scan7Theme.colors
    val split = splitCards(cards)
    val score = scoreRow(split.scoring)
    val duplicates = duplicateCounts(split.scoring)
    val summary = duplicateSummary(duplicates)
    // Magenta band: ✕ BUST + "Two 7s" (never color alone).
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bust)
            .padding(horizontal = Scan7Spacing.screenMargin, vertical = Scan7Spacing.l)
            .semantics(mergeDescendants = true) { heading() },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            CloseIcon(colors.onBust, size = 32.dp, strokeWidth = 3.2f)
            Column(verticalArrangement = Arrangement.spacedBy(Scan7Spacing.xxs)) {
                Text("BUST", color = colors.onBust, style = Scan7Type.total24.copy(fontSize = 28.sp), maxLines = 1)
                Text("$summary in the row", color = colors.onBust, style = Scan7Type.tag12.copy(fontSize = 14.sp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Text(player.name, color = colors.onBust, style = Scan7Type.tag12.copy(fontSize = 13.sp), maxLines = 1)
            PlayerToken(player.initial, player.color, TokenSize(30.dp, 40.dp, 14.sp), cornerRadius = 6.dp, outline = colors.onBust)
        }
    }
    PanelContent {
        Chips(split.scoring, split.ignored, duplicates)
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Round points", color = colors.textMuted, style = Scan7Type.text13)
            Row(
                horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clearAndSetSemantics { contentDescription = "0 points, bust" },
            ) {
                Text(
                    score.totalIgnoringBust().toString(),
                    color = colors.textMuted,
                    style = Scan7Type.text14.copy(textDecoration = TextDecoration.LineThrough),
                )
                Text("0", color = colors.bust, style = ScoreNumberStyle.copy(fontSize = 44.sp, lineHeight = 48.sp))
            }
        }
        val dupValue = duplicates.keys.firstOrNull()
        Text(
            text = "Misread a card? Tap Fix to correct it." +
                (dupValue?.let { " ${player.name} used a Second Chance? Fix and remove one $it." } ?: ""),
            color = colors.textMuted,
            style = Scan7Type.text13,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton("Fix", onFix, Modifier.width(120.dp))
            PrimaryButton("Confirm bust", { onConfirm(reviewOutcome(cards)) }, Modifier.weight(1f), color = colors.bust)
        }
    }
}

@Composable
private fun EmptyPanel(player: PlayerUi, error: String?, onRetake: () -> Unit, onEnterManually: () -> Unit) {
    val colors = Scan7Theme.colors
    PanelContent {
        Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            PlayerToken(player.initial, player.color, TokenSize(30.dp, 40.dp, 14.sp), cornerRadius = 6.dp)
            Text(player.name, color = colors.text, style = Scan7Type.button.copy(fontSize = 17.sp), maxLines = 1)
        }
        Column(verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
            Text(
                "No cards found",
                color = colors.text,
                style = Scan7Type.total24,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = error?.let { "The card detector failed: $it" }
                    ?: "Hold the phone over the whole row, with enough light, and try again.",
                color = colors.textMuted,
                style = Scan7Type.text13,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton("Retake", onRetake, Modifier.weight(1f))
            PrimaryButton("Enter manually", onEnterManually, Modifier.weight(1.6f))
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

/** Breakdown line, separators muted: "7 + 12 + 3 = 22 · ×2 = 44 · +4 · Flip 7 +15". */
@Composable
internal fun Breakdown(cards: List<Card>, modifier: Modifier = Modifier) {
    val colors = Scan7Theme.colors
    val parts = breakdownParts(cards)
    Text(
        text = buildAnnotatedString {
            if (parts.isEmpty()) {
                withStyle(SpanStyle(color = colors.textMuted)) { append(NO_SCORING_CARDS) }
            }
            parts.forEachIndexed { i, part ->
                if (i > 0) withStyle(SpanStyle(color = colors.textMuted)) { append(" · ") }
                append(part)
            }
        },
        color = colors.text,
        style = Scan7Type.text13.copy(lineHeight = 20.sp),
        modifier = modifier,
    )
}

/** Scoring chips (duplicates outlined magenta) followed by the ignored ones (greyed, info only). */
@Composable
internal fun Chips(scoring: List<Card>, ignored: List<Card>, duplicates: Map<Int, Int>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s),
        modifier = Modifier.fillMaxWidth(),
    ) {
        scoring.forEach { CardChip(it, if (isDuplicate(it, duplicates)) ChipLook.Duplicate else ChipLook.Normal) }
        ignored.forEach { CardChip(it, ChipLook.Ignored) }
    }
}

/** "FLIP 7 +15" badge next to the total (7 different numbers). */
@Composable
private fun Flip7Badge(bonus: Int) {
    val colors = Scan7Theme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(colors.accent, RoundedCornerShape(Scan7Radii.tagRow))
            .padding(horizontal = Scan7Spacing.s, vertical = Scan7Spacing.xxs)
            .clearAndSetSemantics { contentDescription = "Flip 7 bonus, plus $bonus" },
    ) {
        Text("FLIP 7", color = colors.onAccent, style = Scan7Type.tag11, maxLines = 1)
        Text("+$bonus", color = colors.onAccent, style = Scan7Type.total15, maxLines = 1)
    }
}

/** Retake: 64 dp square outline button with the circular-arrow icon. */
@Composable
private fun RetakeButton(onClick: () -> Unit) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.button)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(Scan7Spacing.bottomButtonsHeight)
            .clip(shape)
            .border(1.5.dp, colors.line, shape)
            .scan7Clickable(onClick)
            .clearAndSetSemantics {
                contentDescription = "Retake"
                role = Role.Button
            },
    ) {
        RetakeIcon(colors.text)
    }
}
