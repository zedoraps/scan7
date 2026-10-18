package net.moesli.scan7.ui.components

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/** Wiggle amplitude in arrange mode (±1.6°). */
private const val WIGGLE_DEGREES = 1.6f

/** Scale of a card while it is dragged. */
private const val DRAG_SCALE = 1.06f

/**
 * Arrange-mode wiggle: rotation ±1.6°, ~0.25 s per half cycle, alternating, staggered by [index]
 * (prototype: 0.22 + (i % 3) · 0.03 s, phase −i · 0.07 s). Read the returned state in a
 * graphicsLayer so only the layer redraws.
 */
@Composable
internal fun wiggleRotation(enabled: Boolean, index: Int): State<Float> {
    if (!enabled) return remember { mutableFloatStateOf(0f) }
    val halfCycle = 220 + (index % 3) * 30
    val transition = rememberInfiniteTransition(label = "wiggle")
    return transition.animateFloat(
        initialValue = -WIGGLE_DEGREES,
        targetValue = WIGGLE_DEGREES,
        animationSpec = infiniteRepeatable(
            animation = tween(halfCycle, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset((index * 70) % (2 * halfCycle), StartOffsetType.FastForward),
        ),
        label = "wiggle",
    )
}

/** Resolved look of a card/row for a player + mode (handoff §3 "Card states"). */
internal class CardLook(
    val background: Color,
    val ink: Color,
    val borderColor: Color,
    val borderWidth: Dp,
    val dashed: Boolean,
    val shadow: Shadowing,
) {
    enum class Shadowing { None, Open, Dragging }
}

@Composable
internal fun cardLook(status: PlayerStatus, mode: CardMode): CardLook {
    val c = Scan7Theme.colors
    val done = status.isDone
    return CardLook(
        background = if (done) c.surfaceDone else c.surface,
        ink = if (done) c.textDone else c.text,
        borderColor = when {
            mode == CardMode.Dragging -> c.accent
            mode == CardMode.Arrange -> c.openOutline
            done -> c.lineSubtleDone
            else -> c.line
        },
        borderWidth = if (mode == CardMode.Dragging) 2.dp else 1.5.dp,
        dashed = mode == CardMode.Arrange,
        shadow = when {
            mode == CardMode.Dragging -> CardLook.Shadowing.Dragging
            done -> CardLook.Shadowing.None
            else -> CardLook.Shadowing.Open
        },
    )
}

/** Shadow, clip, fill and border of a card/row surface. */
internal fun Modifier.cardSurface(look: CardLook, shape: Shape, cornerRadius: Dp): Modifier = this
    .then(
        when (look.shadow) {
            CardLook.Shadowing.None -> Modifier
            CardLook.Shadowing.Open -> Modifier.openCardShadow(shape)
            CardLook.Shadowing.Dragging -> Modifier.draggingCardShadow(shape)
        },
    )
    .clip(shape)
    .background(look.background)
    .cardBorder(look.borderWidth, look.borderColor, shape, cornerRadius, look.dashed)

/**
 * Card body / row click handling: tap opens the player, long-press starts arrange mode. With
 * [onDoubleClick] (compact table cards: scan) a double-tap calls it; a single tap then waits for
 * the double-tap timeout (~300 ms) before [onClick], so only pass it where it is used. TalkBack
 * gets it as the custom action [doubleClickLabel].
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.playerClickable(
    player: PlayerUi,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onDoubleClick: (() -> Unit)? = null,
    doubleClickLabel: String? = null,
): Modifier = this
    .combinedClickable(
        interactionSource = null,
        indication = PressDim,
        role = Role.Button,
        onLongClickLabel = if (onLongClick != null) "Arrange seats" else null,
        onLongClick = onLongClick,
        onDoubleClick = onDoubleClick,
        onClick = onClick,
    )
    .semantics {
        contentDescription = player.accessibilityLabel()
        if (onDoubleClick != null && doubleClickLabel != null) {
            customActions = listOf(CustomAccessibilityAction(doubleClickLabel) { onDoubleClick(); true })
        }
    }

/**
 * Player card for the table view. Layout: ring + token, then "Name Total" on one line (Compact:
 * name on one line, total below it, so names aren't squeezed), then the Scan/Bust actions (open
 * players, Large/Medium only) or the status tag (finished players, arrange mode, and always on
 * Compact cards). The leader badge overlaps the top-right corner.
 *
 * [onDoubleClick] (Compact cards have no in-card Scan): double-tap → scan, also offered to
 * TalkBack as the custom action "Scan <name>"; `null` keeps single taps instant.
 *
 * Stateless: [mode] and [index] (wiggle stagger) come from the screen; dragging itself is handled
 * by the caller (apply the drag offset to [modifier]).
 */
@Composable
fun PlayerCard(
    player: PlayerUi,
    size: CardSize,
    modifier: Modifier = Modifier,
    mode: CardMode = CardMode.Normal,
    index: Int = 0,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    onScan: () -> Unit = {},
    onBust: () -> Unit = {},
    onDoubleClick: (() -> Unit)? = null,
) {
    val done = player.status.isDone
    val look = cardLook(player.status, mode)
    val compact = size == CardSize.Compact
    val radius = if (size == CardSize.Large) Scan7Radii.cardLarge else Scan7Radii.card
    val shape = RoundedCornerShape(radius)
    val rotation = wiggleRotation(mode == CardMode.Arrange, index)
    val showActions = !done && mode == CardMode.Normal && size.actionHeight != null

    Box(
        modifier = modifier
            .size(size.width, size.height)
            .graphicsLayer {
                rotationZ = rotation.value
                val s = if (mode == CardMode.Dragging) DRAG_SCALE else 1f
                scaleX = s
                scaleY = s
            },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(if (compact) CompactTagGap else Scan7Spacing.xs),
            modifier = Modifier
                .matchParentSize()
                .cardSurface(look, shape, radius)
                .playerClickable(player, onClick, onLongClick, onDoubleClick, "Scan ${player.name}")
                .padding(horizontal = size.padding, vertical = if (compact) CompactPaddingV else size.padding),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 1.dp else Scan7Spacing.xxs, Alignment.CenterVertically),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clearAndSetSemantics {},
            ) {
                PlayerScoreRing(player, size.ring, size.token)
                if (compact) {
                    CompactNameAndTotal(player, look.ink)
                } else {
                    NameAndTotal(player, look.ink, size.nameStyle(), size.totalStyle())
                }
            }
            if (showActions) {
                ScanBustActions(
                    playerName = player.name,
                    onScan = onScan,
                    onBust = onBust,
                    height = size.actionHeight,
                    cornerRadius = Scan7Radii.actionCard,
                )
            } else {
                StatusTag(
                    status = player.status,
                    height = size.tagHeight,
                    cornerRadius = Scan7Radii.tagCard,
                    textStyle = if (size == CardSize.Large) Scan7Type.tag11 else Scan7Type.tag10,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clearAndSetSemantics {},
                )
            }
        }
        if (player.isLeader) {
            LeaderBadge(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp),
            )
        }
    }
}

@Composable
private fun NameAndTotal(player: PlayerUi, ink: Color, nameStyle: TextStyle, totalStyle: TextStyle) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.xs, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = player.name,
            color = ink,
            style = nameStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .alignByBaseline(),
        )
        Text(
            text = player.total.toString(),
            color = ink,
            style = totalStyle,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

/**
 * Compact cards (72×96, 7–10 players): name on its own line (full 60 dp width), total below in
 * Unbounded. Tight line heights so ring 36 + name 12 + total 13 + gaps fit the 63 dp above the tag.
 */
@Composable
private fun CompactNameAndTotal(player: PlayerUi, ink: Color) {
    Text(
        text = player.name,
        color = ink,
        style = Scan7Type.text11.copy(lineHeight = 12.sp),
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = player.total.toString(),
        color = ink,
        style = Scan7Type.total13.copy(lineHeight = 13.sp),
        maxLines = 1,
        softWrap = false,
    )
}

/** Compact card: vertical padding (5 instead of 6) and gap above the tag (3 instead of 6). */
private val CompactPaddingV = 5.dp
private val CompactTagGap = 3.dp

private fun CardSize.nameStyle(): TextStyle = if (this == CardSize.Large) Scan7Type.text12 else Scan7Type.text11

private fun CardSize.totalStyle(): TextStyle = when (this) {
    CardSize.Large -> Scan7Type.total17
    CardSize.Medium -> Scan7Type.total15
    CardSize.Compact -> Scan7Type.total13
}

@Preview(widthDp = 390)
@Composable
private fun PlayerCardPreview() {
    Scan7Theme {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Scan7Spacing.l),
            modifier = Modifier
                .background(Scan7Theme.colors.ground)
                .padding(Scan7Spacing.l),
        ) {
            val players = samplePlayers(4)
            CardSize.entries.forEach { size ->
                players.take(3).forEach { PlayerCard(it, size) }
            }
            PlayerCard(players[3], CardSize.Large, mode = CardMode.Arrange)
            PlayerCard(players[3], CardSize.Large, mode = CardMode.Dragging)
        }
    }
}
