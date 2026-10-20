package net.moesli.scan7.ui.scan

import androidx.compose.ui.platform.LocalResources
import net.moesli.scan7.ui.text.resolve
import androidx.compose.ui.res.stringResource
import net.moesli.scan7.R
import net.moesli.scan7.ui.components.avatarSizeForHeight
import net.moesli.scan7.ui.components.PlayerAvatar
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
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import net.moesli.scan7.ui.theme.Scan7Colors
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.moesli.scan7.detection.Box as DetectionBox
import net.moesli.scan7.detection.Detection
import net.moesli.scan7.detection.DisplayMapping
import net.moesli.scan7.detection.TrackedDetection
import net.moesli.scan7.scoring.Card
import net.moesli.scan7.scoring.CardKind
import net.moesli.scan7.ui.components.FitText
import net.moesli.scan7.ui.components.PlayerUi
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

/** Keyboard (the camera's "Type" button: enter the cards by hand). */
@Composable
internal fun KeyboardIcon(color: Color, size: Dp = 20.dp) =
    StrokeIcon("M4 6h16a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1zM7 10h0M10.5 10h0M14 10h0M17 10h0M8 14h8", color, size, 2f)

@Composable
internal fun RetakeIcon(color: Color, size: Dp = 22.dp) = StrokeIcon("M4 12a8 8 0 1 0 3-6.2M4 4v4h4", color, size, 2.2f)

// ---------------------------------------------------------------------------------------------
// Player heading: token + caps label + name ("SCANNING / Mia", "NEXT / Ben").
// ---------------------------------------------------------------------------------------------

@Composable
internal fun PlayerHeading(player: PlayerUi, label: String, modifier: Modifier = Modifier) {
    val colors = Scan7Theme.colors
    val description = stringResource(R.string.player_heading, label, player.name)
    Row(
        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clearAndSetSemantics {
            heading()
            contentDescription = description
        },
    ) {
        PlayerAvatar(player.avatar, avatarSizeForHeight(35.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // The label wraps ("CARDS / FOR") rather than losing a word at large font scales.
            Text(label.uppercase(), color = colors.textMuted, style = Scan7Type.capsButton)
            FitText(player.name, Scan7Type.button, color = colors.text, minSize = 12.dp)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Card chips.
// ---------------------------------------------------------------------------------------------

enum class ChipLook { Normal, Duplicate, Ignored, Excluded }

/**
 * Card chip: dark tile with the card label (Unbounded) and an echo dot; modifiers in orange.
 * [ChipLook.Duplicate] = magenta outline (bust), [ChipLook.Ignored] = action card / back (also a
 * Second Chance that cancelled a duplicate): only its name in a dashed muted outline,
 * [ChipLook.Excluded] = left out on the review: greyed, label struck through.
 * With [onClick] the chip is a button (manual screen: tap to remove; review: leave out / include).
 * [actions] adds TalkBack custom actions.
 */
@Composable
internal fun CardChip(
    card: Card,
    look: ChipLook,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    onClick: (() -> Unit)? = null,
    clickLabel: String? = null,
    actions: List<CustomAccessibilityAction> = emptyList(),
) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.actionRow)
    val ink = when {
        look == ChipLook.Ignored || look == ChipLook.Excluded -> colors.textMuted
        look == ChipLook.Duplicate -> colors.bust
        card.isModifier -> ModifierInk
        else -> colors.text
    }
    val label = card.chipLabel().resolve()
    val spoken = card.spokenLabel().resolve()
    val description = when (look) {
        ChipLook.Normal -> spoken
        ChipLook.Duplicate -> stringResource(R.string.chip_duplicate, spoken)
        ChipLook.Ignored -> stringResource(R.string.chip_ignored, label)
        ChipLook.Excluded -> stringResource(R.string.chip_excluded, spoken)
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
                    ChipLook.Excluded -> Modifier.border(1.5.dp, colors.lineSubtle, shape)
                },
            )
            .then(if (onClick != null) Modifier.scan7Clickable(onClick, onClickLabel = clickLabel) else Modifier)
            .clearAndSetSemantics {
                contentDescription = description
                if (actions.isNotEmpty()) customActions = actions
            }
            .padding(horizontal = 10.dp),
    ) {
        if (look == ChipLook.Excluded) {
            Text(
                label,
                color = ink.copy(alpha = 0.7f),
                style = ChipLabelStyle.copy(textDecoration = TextDecoration.LineThrough),
                maxLines = 1,
            )
        } else if (look != ChipLook.Ignored) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-5).dp, y = 5.dp)
                    .size(6.dp)
                    .background(card.echoDot(), CircleShape),
            )
            Text(label, color = ink, style = ChipLabelStyle, maxLines = 1)
        } else {
            Text(label, color = ink, style = Scan7Type.text12, maxLines = 1)
        }
    }
}

private val ChipLabelStyle = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)

// ---------------------------------------------------------------------------------------------
// Detection boxes over the camera preview / frozen frame.
// ---------------------------------------------------------------------------------------------

/** How a box is drawn. */
enum class BoxLook { Scoring, Duplicate, Ignored, Excluded }

/**
 * One detected card for the overlay. [box] is normalized in upright image coordinates; [alpha] < 1
 * fades a live track that was missed in the latest frames. [index] is the detection's index (the
 * review toggles exclusion by it).
 */
@Immutable
data class ScanBox(val card: Card, val box: DetectionBox, val look: BoxLook, val alpha: Float = 1f, val index: Int = -1)

/** Detections with a known card label (unknown labels are dropped). */
fun List<Detection>.toCards(): List<Pair<Card, DetectionBox>> =
    mapNotNull { d -> Card.fromLabel(d.label)?.let { it to d.box } }

/**
 * Overlay boxes: scoring cards cyan, duplicated numbers magenta (if [markDuplicates], counted over
 * the cards not [excluded]), action cards / backs ignored, [excluded] detections (indices) left out.
 */
fun scanBoxes(
    detections: List<Detection>,
    markDuplicates: Boolean,
    alphas: List<Float>? = null,
    excluded: Set<Int> = emptySet(),
): List<ScanBox> {
    val cards = detections.mapIndexedNotNull { i, d -> Card.fromLabel(d.label)?.let { ScanBox(it, d.box, BoxLook.Scoring, alphas?.getOrNull(i) ?: 1f, i) } }
    val dups = if (markDuplicates) bustDuplicates(cards.filter { it.index !in excluded }.map { it.card }) else emptyMap()
    return cards.map { b ->
        val look = when {
            b.index in excluded -> BoxLook.Excluded
            !b.card.isScoring -> BoxLook.Ignored
            isDuplicate(b.card, dups) -> BoxLook.Duplicate
            else -> BoxLook.Scoring
        }
        b.copy(look = look)
    }
}

/** Live overlay boxes from the smoothed tracks, fading while a track is missed. */
fun trackBoxes(tracks: List<TrackedDetection>): List<ScanBox> =
    scanBoxes(tracks.map { it.detection }, markDuplicates = false, alphas = tracks.map { it.alpha })

/**
 * Draws [boxes] on an image of [imageWidth]×[imageHeight] shown in the same bounds, cropped
 * ([crop]) or fitted (camera preview, frozen frame); centred, or moved up / down by [verticalBias]
 * (−1 = top, as [DisplayMapping.of]). Scoring: cyan 3 dp + 1.5 dp white
 * outline, radius 10, cyan label tag top-left. Duplicates: same in magenta. Action cards / backs:
 * dashed grey with the card's name in a grey tag. Excluded (left out by the user): the card dimmed, short-dashed muted
 * outline, crossed out, "EXCLUDED" tag in muted grey.
 */
@Composable
internal fun ScanBoxesOverlay(
    boxes: List<ScanBox>,
    imageWidth: Int,
    imageHeight: Int,
    crop: Boolean,
    modifier: Modifier = Modifier,
    keepClear: DpSize = DpSize.Zero,
    verticalBias: Float = 0f,
) {
    val colors = Scan7Theme.colors
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    // Per-frame work stays allocation-light (the live overlay redraws on every analysis frame):
    // strokes, dash effects, tag styles and the tag path are built once and reused.
    val styles = remember(colors, density) { BoxDrawStyles(colors, density) }
    // Tag texts resolved once per locale (not per frame).
    val resources = LocalResources.current
    val excludedTag = stringResource(R.string.box_excluded)
    val labels = remember(resources, excludedTag) { Card.entries.associateWith { it.chipLabel().resolve(resources) } }
    Canvas(modifier) {
        if (imageWidth <= 0 || imageHeight <= 0) return@Canvas
        val mapping = DisplayMapping.of(imageWidth, imageHeight, size.width, size.height, crop, biasY = verticalBias)
        // All boxes first, then all tags, so a neighbouring box never covers a tag (long ones like
        // "IGNORIERT" reach into the next card). Within each pass excluded, then ignored, then
        // scoring, so scoring stays on top where they overlap (one loop per layer, no sorted copy).
        for (layer in 0..2) {
            for (i in boxes.indices) {
                val b = boxes[i]
                if (b.look.layer == layer) drawScanBoxItem(b, mapping, styles, colors, measurer, labels, excludedTag, tag = false)
            }
        }
        // Tags: scoring first so they keep their spot; later tags that would overlap a placed tag
        // (or the back button area [keepClear], top-left) move to another edge of their box or are
        // skipped (simple greedy, the chips below list every card anyway).
        styles.placedCount = 0
        if (keepClear.width > 0.dp && keepClear.height > 0.dp) {
            styles.place(0f, 0f, keepClear.width.toPx(), keepClear.height.toPx())
        }
        for (layer in 2 downTo 0) {
            for (i in boxes.indices) {
                val b = boxes[i]
                if (b.look.layer == layer) drawScanBoxItem(b, mapping, styles, colors, measurer, labels, excludedTag, tag = true)
            }
        }
    }
}

/** Draw order of a [BoxLook]: lower layers first. */
private val BoxLook.layer: Int get() = when (this) {
    BoxLook.Excluded -> 0
    BoxLook.Ignored -> 1
    BoxLook.Scoring, BoxLook.Duplicate -> 2
}

/** Pixel sizes, strokes, tag styles and a reusable tag path for [ScanBoxesOverlay]. */
private class BoxDrawStyles(colors: Scan7Colors, density: Density) {
    val radius: Float
    val scanWidth: Float
    val outlineWidth: Float
    val excludedStroke: Stroke
    val ignoredStroke: Stroke
    val crossWidth: Float
    val padX: Float
    val padY: Float
    val tagInset: Float
    val tagCorner: CornerRadius
    // Font sizes in dp: the tags sit on the photo's card boxes and must not grow with the font scale.
    val tagStyle = with(density) { BoxTag.copy(color = colors.onAccent, fontSize = BoxTagSize.toSp()) }
    val tagStyleSmall = with(density) { BoxTag.copy(color = colors.onAccent, fontSize = BoxTagSmallSize.toSp()) }
    val tagPath = Path()

    /** Rects (l, t, r, b) of the tags placed in the current frame (reused, no per-frame allocation). */
    private var placed = FloatArray(4 * 32)
    var placedCount = 0

    fun place(l: Float, t: Float, r: Float, b: Float) {
        if (placedCount * 4 + 4 > placed.size) placed = placed.copyOf(placed.size * 2)
        placed[placedCount * 4] = l
        placed[placedCount * 4 + 1] = t
        placed[placedCount * 4 + 2] = r
        placed[placedCount * 4 + 3] = b
        placedCount++
    }

    fun overlaps(l: Float, t: Float, r: Float, b: Float): Boolean {
        for (i in 0 until placedCount) {
            val j = i * 4
            if (l < placed[j + 2] && r > placed[j] && t < placed[j + 3] && b > placed[j + 1]) return true
        }
        return false
    }

    init {
        with(density) {
            radius = 10.dp.toPx()
            scanWidth = 3.dp.toPx()
            outlineWidth = 1.5.dp.toPx()
            excludedStroke = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())))
            ignoredStroke = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
            crossWidth = 2.dp.toPx()
            padX = 8.dp.toPx()
            padY = 3.dp.toPx()
            tagInset = 1.dp.toPx()
            tagCorner = CornerRadius(9.dp.toPx())
        }
    }
}

/**
 * One box ([tag] = false) or its tag ([tag] = true). A fading live track ([ScanBox.alpha] < 1) gets the alpha on each draw
 * call instead of an offscreen layer (a full-screen saveLayer per faded box and frame was costly).
 */
private fun DrawScope.drawScanBoxItem(
    b: ScanBox,
    mapping: DisplayMapping,
    s: BoxDrawStyles,
    colors: Scan7Colors,
    measurer: TextMeasurer,
    labels: Map<Card, String>,
    excludedTag: String,
    tag: Boolean,
) {
    val alpha = b.alpha.coerceIn(0f, 1f)
    if (alpha <= 0f) return
    val v = mapping.toView(b.box)
    val topLeft = Offset(v.left, v.top)
    val boxSize = Size(v.width, v.height)
    val corner = CornerRadius(s.radius)
    val tagColor: Color
    val tagText: String
    val tagStyle: TextStyle
    when (b.look) {
        BoxLook.Excluded -> {
            if (!tag) {
                drawRoundRect(colors.ground, topLeft, boxSize, corner, alpha = 0.55f * alpha)
                drawRoundRect(colors.textMuted, topLeft, boxSize, corner, style = s.excludedStroke, alpha = alpha)
                drawLine(
                    colors.textMuted,
                    Offset(v.left + s.radius, v.bottom - s.radius),
                    Offset(v.right - s.radius, v.top + s.radius),
                    strokeWidth = s.crossWidth,
                    cap = StrokeCap.Round,
                    alpha = alpha,
                )
            }
            tagColor = colors.textMuted
            tagText = excludedTag
            tagStyle = s.tagStyleSmall
        }
        BoxLook.Ignored -> {
            if (!tag) drawRoundRect(colors.text, topLeft, boxSize, corner, style = s.ignoredStroke, alpha = IgnoredInkAlpha * alpha)
            tagColor = colors.text.copy(alpha = IgnoredInkAlpha)
            tagText = labels.getValue(b.card)
            tagStyle = s.tagStyleSmall
        }
        BoxLook.Scoring, BoxLook.Duplicate -> {
            val ink = if (b.look == BoxLook.Duplicate) colors.bust else colors.scanBox
            if (!tag) {
                drawScanBox(
                    colors, topLeft, boxSize, strokeWidth = s.scanWidth, outlineWidth = s.outlineWidth,
                    cornerRadius = s.radius, ink = ink, alpha = alpha,
                )
            }
            tagColor = ink
            tagText = labels.getValue(b.card)
            tagStyle = s.tagStyle
        }
    }
    if (!tag) return
    // TextMeasurer caches the layouts of recent (text, style) pairs, so this is cheap per frame.
    val text = measurer.measure(tagText, tagStyle)
    val tagWidth = text.size.width + 2 * s.padX
    val tagHeight = text.size.height + 2 * s.padY
    val tagLeft = (v.left - s.tagInset).coerceIn(0f, (size.width - tagWidth).coerceAtLeast(0f))
    // Excluded: the tag sits on the bottom edge, clear of the neighbours' top-left tags (a long tag
    // like "IGNORIERT" would otherwise run under the next card's tag). If the preferred edge is
    // taken by another tag, try the other edge, then the middle of the box; else no tag.
    val top = v.top - s.tagInset
    val bottom = v.bottom - tagHeight + s.tagInset
    val middle = (v.top + v.bottom) / 2 - tagHeight / 2
    val maxTop = (size.height - tagHeight).coerceAtLeast(0f)
    var tagTop = Float.NaN
    for (k in 0..2) {
        val y = when (k) {
            0 -> if (b.look == BoxLook.Excluded) bottom else top
            1 -> if (b.look == BoxLook.Excluded) top else bottom
            else -> middle
        }.coerceIn(0f, maxTop)
        if (!s.overlaps(tagLeft, y, tagLeft + tagWidth, y + tagHeight)) {
            tagTop = y
            break
        }
    }
    if (tagTop.isNaN()) return
    s.place(tagLeft, tagTop, tagLeft + tagWidth, tagTop + tagHeight)
    s.tagPath.reset()
    s.tagPath.addRoundRect(
        RoundRect(
            left = tagLeft, top = tagTop, right = tagLeft + tagWidth, bottom = tagTop + tagHeight,
            topLeftCornerRadius = s.tagCorner, topRightCornerRadius = CornerRadius.Zero,
            bottomRightCornerRadius = s.tagCorner, bottomLeftCornerRadius = CornerRadius.Zero,
        ),
    )
    drawPath(s.tagPath, tagColor, alpha = alpha)
    drawText(text, topLeft = Offset(tagLeft + s.padX, tagTop + s.padY), alpha = alpha)
}

/** Ink alpha of ignored boxes (action cards / backs). */
private const val IgnoredInkAlpha = 0.78f

private val BoxTag = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.SemiBold)
private val BoxTagSize = 12.dp
private val BoxTagSmallSize = 9.dp
