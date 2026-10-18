package net.moesli.scan7.ui.scan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.moesli.scan7.detection.Box as DetectionBox
import net.moesli.scan7.detection.Detection
import net.moesli.scan7.detection.DisplayMapping
import net.moesli.scan7.detection.TrackedDetection
import net.moesli.scan7.scoring.Card
import net.moesli.scan7.scoring.CardKind
import net.moesli.scan7.ui.components.PlayerToken
import net.moesli.scan7.ui.components.PlayerUi
import net.moesli.scan7.ui.components.TokenSize
import net.moesli.scan7.ui.components.dashedBorder
import net.moesli.scan7.ui.components.scan7Clickable
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type
import net.moesli.scan7.ui.theme.Unbounded
import net.moesli.scan7.ui.theme.drawScanBox

// ---------------------------------------------------------------------------------------------
// Card echo colors: abstract color dots / modifier tint that hint at the physical cards (handoff
// design note: echo the card colors abstractly, never copy the artwork). Scan screens only.
// ---------------------------------------------------------------------------------------------

private val NumberDots: Map<Int, Color> = mapOf(
    0 to Color(0xFFD9DEE6),
    1 to Color(0xFFCDB6A0),
    2 to Color(0xFFB5E3D8),
    3 to Color(0xFFF08A8A),
    4 to Color(0xFF7FD4C4),
    5 to Color(0xFFA8C46E),
    6 to Color(0xFFC79BF2),
    7 to Color(0xFFF2A0C0),
    8 to Color(0xFFA6E38A),
    9 to Color(0xFFF2B36B),
    10 to Color(0xFFE86F6F),
    11 to Color(0xFF79A8F2),
    12 to Color(0xFFB8BFC9),
)

/** Ink of modifier cards (+N, ×2): warm orange, as on the drafts. */
internal val ModifierInk = Color(0xFFFFB561)

/** Border of modifier chips. */
internal val ModifierLine = Color(0xFF6B4A1F)

/** Echo dot for [card]: number color, modifier orange, transparent for ignored cards. */
internal fun Card.echoDot(): Color = when (kind) {
    CardKind.NUMBER -> NumberDots.getValue(value!!)
    CardKind.MODIFIER_PLUS, CardKind.MODIFIER_TIMES2 -> ModifierInk
    else -> Color.Transparent
}

private val Card.isModifier: Boolean get() = kind == CardKind.MODIFIER_PLUS || kind == CardKind.MODIFIER_TIMES2

// ---------------------------------------------------------------------------------------------
// Icons (24×24 grid, same approach as ui/components/Icons.kt). Decorative.
// ---------------------------------------------------------------------------------------------

@Composable
private fun StrokeIcon(d: String, color: Color, size: Dp, strokeWidth: Float, modifier: Modifier = Modifier) {
    val path = remember(d) { PathParser().parsePathString(d).toPath() }
    Canvas(modifier.size(size)) {
        scale(this.size.width / 24f, pivot = Offset.Zero) {
            drawPath(path, color, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
internal fun CloseIcon(color: Color, size: Dp = 20.dp, strokeWidth: Float = 2.4f) =
    StrokeIcon("M6 6l12 12M18 6L6 18", color, size, strokeWidth)

@Composable
internal fun BackIcon(color: Color, size: Dp = 20.dp) = StrokeIcon("M19 12H5M11 6l-6 6 6 6", color, size, 2.2f)

@Composable
internal fun RetakeIcon(color: Color, size: Dp = 22.dp) = StrokeIcon("M4 12a8 8 0 1 0 3-6.2M4 4v4h4", color, size, 2.2f)

// ---------------------------------------------------------------------------------------------
// Player heading: token + caps label + name ("SCANNING / Mia", "NEXT / Ben").
// ---------------------------------------------------------------------------------------------

@Composable
internal fun PlayerHeading(player: PlayerUi, label: String, modifier: Modifier = Modifier) {
    val colors = Scan7Theme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clearAndSetSemantics {
            heading()
            contentDescription = "${label.lowercase().replaceFirstChar { it.uppercase() }}: ${player.name}"
        },
    ) {
        PlayerToken(player.initial, player.color, TokenSize(26.dp, 35.dp, 12.sp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label.uppercase(), color = colors.textMuted, style = Scan7Type.capsButton, maxLines = 1)
            Text(player.name, color = colors.text, style = Scan7Type.button, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Card chips.
// ---------------------------------------------------------------------------------------------

enum class ChipLook { Normal, Duplicate, Ignored }

/**
 * Card chip: dark tile with the card label (Unbounded) and an echo dot; modifiers in orange.
 * [ChipLook.Duplicate] = magenta outline (bust), [ChipLook.Ignored] = dashed grey "Freeze · ignored".
 * With [onClick] the chip is a button (manual screen: tap to remove).
 */
@Composable
internal fun CardChip(
    card: Card,
    look: ChipLook,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    onClick: (() -> Unit)? = null,
    clickLabel: String? = null,
) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.actionRow)
    val ink = when {
        look == ChipLook.Ignored -> colors.textMuted
        look == ChipLook.Duplicate -> colors.bust
        card.isModifier -> ModifierInk
        else -> colors.text
    }
    val description = when (look) {
        ChipLook.Normal -> card.spokenLabel()
        ChipLook.Duplicate -> "${card.spokenLabel()}, duplicate"
        ChipLook.Ignored -> "${card.chipLabel()}, ignored"
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(height)
            .defaultMinSize(minWidth = height)
            .clip(shape)
            .background(if (look == ChipLook.Duplicate) colors.bust.copy(alpha = 0.16f) else colors.ground)
            .then(
                when (look) {
                    ChipLook.Normal -> Modifier.border(1.5.dp, if (card.isModifier) ModifierLine else colors.line, shape)
                    ChipLook.Duplicate -> Modifier.border(2.5.dp, colors.bust, shape)
                    ChipLook.Ignored -> Modifier.dashedBorder(1.5.dp, colors.openOutline, Scan7Radii.actionRow)
                },
            )
            .then(if (onClick != null) Modifier.scan7Clickable(onClick, onClickLabel = clickLabel) else Modifier)
            .clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = 10.dp),
    ) {
        if (look != ChipLook.Ignored) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-5).dp, y = 5.dp)
                    .size(6.dp)
                    .background(card.echoDot(), CircleShape),
            )
            Text(card.chipLabel(), color = ink, style = ChipLabelStyle, maxLines = 1)
        } else {
            Text("${card.chipLabel()} · ignored", color = ink, style = Scan7Type.text12, maxLines = 1)
        }
    }
}

private val ChipLabelStyle = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)

// ---------------------------------------------------------------------------------------------
// Detection boxes over the camera preview / frozen frame.
// ---------------------------------------------------------------------------------------------

/** How a box is drawn. */
enum class BoxLook { Scoring, Duplicate, Ignored }

/**
 * One detected card for the overlay. [box] is normalized in upright image coordinates; [alpha] < 1
 * fades a live track that was missed in the latest frames.
 */
@Immutable
data class ScanBox(val card: Card, val box: DetectionBox, val look: BoxLook, val alpha: Float = 1f)

/** Detections with a known card label (unknown labels are dropped). */
fun List<Detection>.toCards(): List<Pair<Card, DetectionBox>> =
    mapNotNull { d -> Card.fromLabel(d.label)?.let { it to d.box } }

/** Overlay boxes: scoring cards cyan, duplicated numbers magenta (if [markDuplicates]), others ignored. */
fun scanBoxes(detections: List<Detection>, markDuplicates: Boolean, alphas: List<Float>? = null): List<ScanBox> {
    val cards = detections.mapIndexedNotNull { i, d -> Card.fromLabel(d.label)?.let { Triple(it, d.box, alphas?.getOrNull(i) ?: 1f) } }
    val dups = if (markDuplicates) duplicateCounts(cards.map { it.first }) else emptyMap()
    return cards.map { (card, box, alpha) ->
        val look = when {
            !card.isScoring -> BoxLook.Ignored
            isDuplicate(card, dups) -> BoxLook.Duplicate
            else -> BoxLook.Scoring
        }
        ScanBox(card, box, look, alpha)
    }
}

/** Live overlay boxes from the smoothed tracks, fading while a track is missed. */
fun trackBoxes(tracks: List<TrackedDetection>): List<ScanBox> =
    scanBoxes(tracks.map { it.detection }, markDuplicates = false, alphas = tracks.map { it.alpha })

/**
 * Draws [boxes] on an image of [imageWidth]×[imageHeight] shown centered in the same bounds,
 * cropped ([crop], camera preview) or fitted (frozen frame). Scoring: cyan 3 dp + 1.5 dp white
 * outline, radius 10, cyan label tag top-left. Duplicates: same in magenta. Ignored: dashed grey
 * with an "IGNORED" tag.
 */
@Composable
internal fun ScanBoxesOverlay(
    boxes: List<ScanBox>,
    imageWidth: Int,
    imageHeight: Int,
    crop: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = Scan7Theme.colors
    val bustColors = remember(colors) { colors.copy(scanBox = colors.bust) }
    val measurer = rememberTextMeasurer()
    val ignoredInk = colors.text.copy(alpha = 0.78f)
    Canvas(modifier) {
        if (imageWidth <= 0 || imageHeight <= 0) return@Canvas
        val mapping = DisplayMapping.of(imageWidth, imageHeight, size.width, size.height, crop)
        val radius = 10.dp.toPx()
        // Ignored first so scoring boxes stay on top where they overlap.
        for (b in boxes.sortedBy { it.look != BoxLook.Ignored }) {
            val fade = b.alpha < 1f
            if (fade) {
                drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { alpha = b.alpha.coerceIn(0f, 1f) })
            }
            val v = mapping.toView(b.box)
            val topLeft = Offset(v.left, v.top)
            val boxSize = Size(v.width, v.height)
            val tagColor: Color
            val tagText: String
            val tagStyle: TextStyle
            if (b.look == BoxLook.Ignored) {
                drawRoundRect(
                    ignoredInk, topLeft, boxSize, CornerRadius(radius),
                    style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                )
                tagColor = ignoredInk
                tagText = "IGNORED"
                tagStyle = BoxTagSmall
            } else {
                drawScanBox(
                    if (b.look == BoxLook.Duplicate) bustColors else colors,
                    topLeft, boxSize, strokeWidth = 3.dp.toPx(), outlineWidth = 1.5.dp.toPx(), cornerRadius = radius,
                )
                tagColor = if (b.look == BoxLook.Duplicate) colors.bust else colors.scanBox
                tagText = b.card.chipLabel()
                tagStyle = BoxTag
            }
            val text = measurer.measure(tagText, tagStyle.copy(color = colors.onAccent))
            val padX = 8.dp.toPx()
            val padY = 3.dp.toPx()
            val tagSize = Size(text.size.width + 2 * padX, text.size.height + 2 * padY)
            val tagTopLeft = Offset(
                (v.left - 1.dp.toPx()).coerceIn(0f, (size.width - tagSize.width).coerceAtLeast(0f)),
                (v.top - 1.dp.toPx()).coerceIn(0f, (size.height - tagSize.height).coerceAtLeast(0f)),
            )
            val corner = CornerRadius(9.dp.toPx())
            val tag = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = tagTopLeft.x, top = tagTopLeft.y,
                        right = tagTopLeft.x + tagSize.width, bottom = tagTopLeft.y + tagSize.height,
                        topLeftCornerRadius = corner, topRightCornerRadius = CornerRadius.Zero,
                        bottomRightCornerRadius = corner, bottomLeftCornerRadius = CornerRadius.Zero,
                    ),
                )
            }
            drawPath(tag, tagColor)
            drawText(text, topLeft = Offset(tagTopLeft.x + padX, tagTopLeft.y + padY))
            if (fade) drawContext.canvas.restore()
        }
    }
}

private val BoxTag = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
private val BoxTagSmall = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 9.sp)
