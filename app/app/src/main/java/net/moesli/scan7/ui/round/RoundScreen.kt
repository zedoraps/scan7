package net.moesli.scan7.ui.round

import androidx.compose.ui.res.stringResource
import net.moesli.scan7.R
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.moesli.scan7.game.TablePosition
import net.moesli.scan7.ui.components.bottomButtonsOffset
import net.moesli.scan7.ui.components.BottomButtons
import net.moesli.scan7.ui.components.CardMode
import net.moesli.scan7.ui.components.CardSize
import net.moesli.scan7.ui.components.PlayerCard
import net.moesli.scan7.ui.components.PlayerRow
import net.moesli.scan7.ui.components.PrimaryButton
import net.moesli.scan7.ui.components.ProgressStrip
import net.moesli.scan7.ui.components.RoundView
import net.moesli.scan7.ui.components.RowSize
import net.moesli.scan7.ui.components.isDone
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/** Card / row move animation (handoff: ~220 ms ease-out). */
private const val MOVE_MS = 220

/** Opacity of the center label, progress strip and bottom buttons in arrange mode. */
private const val ARRANGE_DIM = 0.25f

/** How long a dropped card keeps its drop spot while the caller hasn't confirmed the move. */
private const val PENDING_MOVE_MS = 1_000L

/** Smallest distance between two default table seats (x or y) before the cards are scaled down. */
private val TableSeatGap = 4.dp

/** Horizontal clamp margin of table cards (prototype clampX: 8 … 390 − W − 8). */
private val TableSideMargin = 8.dp

/** List view: "ROUND n" row at the area top (y 112), rows from y 168. */
private val ListRowsOffset = 56.dp

/** A dragged list row may overshoot the first/last slot by this much (prototype ±20). */
private val RowOvershoot = 20.dp

/**
 * Vertical layout of the Round screen. The prototype frame is 390 × 844: header at 48, table area
 * 112–704, progress strip at 720, bottom buttons 24 from the bottom. Top parts are anchored to the
 * top (below the status bar), bottom parts to the bottom (above the navigation bar), and the table
 * area fills what is between, so on the reference frame it is exactly y 112–704.
 */
private class RoundMetrics(val width: Dp, height: Dp, statusTop: Dp, navBottom: Dp) {
    val headerTop: Dp = max(Scan7Spacing.headerTop, statusTop)
    val areaTop: Dp = headerTop + Scan7Spacing.headerHeight + Scan7Spacing.l
    val buttonsTop: Dp = height - bottomButtonsOffset(navBottom) - Scan7Spacing.bottomButtonsHeight
    val stripTop: Dp = buttonsTop - 36.dp
    val areaBottom: Dp = stripTop - Scan7Spacing.l
    val toastTop: Dp = buttonsTop - Scan7Spacing.s - Scan7Spacing.minTouch
}

/** Room above the first list row for the rank badges (they overlap the row's top-left corner). */
private val BadgeRoom = 8.dp

/**
 * The Round screen (main screen, handoff §4): header, table or list of players in seating order,
 * progress strip and bottom buttons; arrange mode.
 * Stateless apart from transient UI (drag in progress); the bust undo toast lives in [toast] so the
 * caller can show it for other bust paths too (player sheet). In-card Bust shows it here.
 *
 * The new game's seating step reuses this screen in arrange mode with two slots: [header] replaces
 * the round header (placed at the header row), [bottomBar] replaces the progress strip and bottom
 * buttons (it gets the band from the strip to the bottom buttons' bottom, not dimmed).
 */
@Composable
fun RoundScreen(
    state: RoundUiState,
    actions: RoundActions,
    modifier: Modifier = Modifier,
    toast: BustToastState = rememberBustToastState(),
    header: (@Composable (Modifier) -> Unit)? = null,
    bottomBar: (@Composable (Modifier) -> Unit)? = null,
) {
    val colors = Scan7Theme.colors
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val haptics = LocalHapticFeedback.current
    val onBust: (RoundPlayer) -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        actions.onBust(it.id)
        toast.show(it.id, it.player.name)
    }
    BackHandler(enabled = state.arranging, onBack = actions::onDone)

    // Screen stays on during the game (the phone lies on the table between scans).
    // Tablets / unfolded foldables: the layout keeps its phone width, centred.
    Box(modifier.fillMaxSize().keepScreenOn().background(colors.ground), contentAlignment = Alignment.TopCenter) {
        BoxWithConstraints(Modifier.fillMaxHeight().widthIn(max = Scan7Spacing.maxContentWidth).fillMaxWidth()) {
            val m = RoundMetrics(maxWidth, maxHeight, statusTop, navBottom)
            val dim = if (state.arranging) ARRANGE_DIM else 1f

            when (state.view) {
                RoundView.Table -> TableView(state, actions, m, onBust)
                RoundView.List -> ListView(state, actions, m, onBust)
            }

            if (header != null) {
                header(Modifier.offset(y = m.headerTop))
            } else {
                RoundHeader(
                    view = state.view,
                    arranging = state.arranging,
                    actions = actions,
                    modifier = Modifier.offset(y = m.headerTop),
                )
            }

            if (bottomBar != null) {
                bottomBar(
                    Modifier
                        .offset(y = m.stripTop)
                        .padding(horizontal = Scan7Spacing.screenMargin)
                        .fillMaxWidth()
                        .height(m.buttonsTop + Scan7Spacing.bottomButtonsHeight - m.stripTop),
                )
            } else ProgressStrip(
                players = state.players.map { it.player },
                target = state.target,
                modifier = Modifier
                    .offset(y = m.stripTop)
                    .padding(horizontal = Scan7Spacing.screenMargin)
                    .alpha(dim),
            )

            if (bottomBar == null) Box(
                modifier = Modifier
                    .offset(y = m.buttonsTop)
                    .padding(horizontal = Scan7Spacing.screenMargin)
                    .fillMaxWidth()
                    .height(Scan7Spacing.bottomButtonsHeight)
                    .alpha(dim)
                    .then(if (state.arranging) Modifier.clearAndSetSemantics {} else Modifier),
            ) {
                val enabled = !state.arranging
                if (state.allDone) {
                    // Closes the round and starts the next one (or Game over) in one tap.
                    PrimaryButton(
                        text = stringResource(R.string.round_next),
                        onClick = { if (enabled) actions.onNextRound() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    BottomButtons(
                        next = state.nextOpen?.player,
                        onCloseRound = { if (enabled) actions.onCloseRound() },
                        onScanNext = { if (enabled) actions.onScanNext() },
                    )
                }
            }

            BustToastHost(
                toast = toast,
                onUndo = actions::onUndoBust,
                modifier = Modifier
                    .offset(y = m.toastTop)
                    .padding(horizontal = Scan7Spacing.screenMargin)
                    .zIndex(20f),
            )
        }
    }
}

// ---- Table view ---------------------------------------------------------------------------

@Composable
private fun TableView(state: RoundUiState, actions: RoundActions, m: RoundMetrics, onBust: (RoundPlayer) -> Unit) {
    val density = LocalDensity.current
    val areaWidth = m.width - TableSideMargin * 2
    // Largest card size whose default seats fit this screen (not by player count).
    val size = tableCardSize(state.playerCount, areaWidth.value, (m.areaBottom - m.areaTop).value, TableSeatGap.value)
    val areaHeight = (m.areaBottom - m.areaTop).coerceAtLeast(size.height)
    // Short screens (16:9): smaller cards instead of overlapping seats; 1 on regular phones.
    val scale = tableCardScale(
        count = state.playerCount,
        width = areaWidth.value,
        height = areaHeight.value,
        cardWidth = size.width.value,
        cardHeight = size.height.value,
        gap = TableSeatGap.value,
    )
    val geometry = with(density) {
        TableGeometry(
            left = TableSideMargin.toPx(),
            top = m.areaTop.toPx(),
            width = areaWidth.toPx(),
            height = areaHeight.toPx(),
            cardWidth = size.width.toPx() * scale,
            cardHeight = size.height.toPx() * scale,
        )
    }
    val cardDensity = remember(density, scale) { Density(density.density * scale, density.fontScale) }

    // Center: "ROUND" + number (72). No table graphic. None in the seating step (no round yet).
    if (!state.seating) Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .offset(y = m.areaTop)
            .fillMaxWidth()
            .height(m.areaBottom - m.areaTop)
            .alpha(if (state.arranging) ARRANGE_DIM else 1f),
    ) {
        val roundDescription = stringResource(R.string.round_number, state.round)
        // Seat rects relative to this box: the "ROUND" label must not end up under a card (short
        // screens: the top seat reaches down to the number). Above the number if free, else below,
        // else left out (the number alone still reads as the round; TalkBack gets the full text).
        val areaTopPx = with(density) { m.areaTop.toPx() }
        val seats = state.players.map { geometry.topLeftOf(it.position) - Offset(0f, areaTopPx) }
        val gap = with(density) { Scan7Spacing.s.roundToPx() }
        Layout(
            content = {
                Text(stringResource(R.string.round_label), color = Scan7Theme.colors.textMuted, style = Scan7Type.capsLabel)
                Text(state.round.toString(), color = Scan7Theme.colors.text, style = Scan7Type.roundNumberTable)
            },
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = roundDescription
                heading()
            },
        ) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val label = measurables[0].measure(loose)
            val number = measurables[1].measure(loose)
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val top = (height - (label.height + gap + number.height)) / 2
            val numberY = top + label.height + gap
            val labelX = (width - label.width) / 2
            fun free(y: Int) = seats.none { s ->
                labelX < s.x + geometry.cardWidth && labelX + label.width > s.x &&
                    y < s.y + geometry.cardHeight && y + label.height > s.y
            }
            val labelY = listOf(top, numberY + number.height + gap).firstOrNull(::free)
            layout(width, height) {
                number.place((width - number.width) / 2, numberY)
                if (labelY != null) label.place(labelX, labelY)
            }
        }
    }

    state.players.forEachIndexed { index, rp ->
        key(rp.id) {
            TableSeat(rp, index, size, geometry, cardDensity, state, actions, onBust)
        }
    }
}

@Composable
private fun TableSeat(
    rp: RoundPlayer,
    index: Int,
    size: CardSize,
    geometry: TableGeometry,
    cardDensity: Density,
    state: RoundUiState,
    actions: RoundActions,
    onBust: (RoundPlayer) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    // Drop spot shown until the caller's state catches up (async stores) or the move is ignored.
    var pending by remember { mutableStateOf<TablePosition?>(null) }
    LaunchedEffect(rp.position) { pending = null }
    LaunchedEffect(pending) {
        if (pending != null) {
            delay(PENDING_MOVE_MS)
            pending = null
        }
    }
    val target = geometry.topLeftOf(pending ?: rp.position)
    val anim = remember { Animatable(target, Offset.VectorConverter) }
    LaunchedEffect(target, dragging) {
        if (!dragging) anim.animateTo(target, tween(MOVE_MS, easing = EaseOut))
    }

    val drag = remember { DragTracker() }
    val anchor = remember { DragAnchor() }
    val haptics = LocalHapticFeedback.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val arranging = rememberUpdatedState(state.arranging)
    val callbacks = rememberUpdatedState(
        object : ArrangeDragCallbacks {
            // Long-press haptic: the card's combinedClickable already ticks at the long press.
            override fun onEnterArrange() = actions.onEnterArrange()

            override fun onDragStart(afterLongPress: Boolean) {
                // In arrange mode the drag starts on touch (no long press, so no system tick yet).
                if (!afterLongPress) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                drag.raw = anim.value
                drag.start = drag.raw
                dragging = true
            }

            override fun onDrag(delta: Offset) {
                drag.raw += delta
                val shown = geometry.clampTopLeft(drag.raw)
                scope.launch(start = CoroutineStart.UNDISPATCHED) { anim.snapTo(shown) }
            }

            override fun onDragEnd() {
                val position = geometry.positionOf(drag.raw)
                haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                dragging = false
                // A long press without moving (or back to the same spot) stores nothing.
                if (isTableMove(drag.raw - drag.start, touchSlop, rp.position, position)) {
                    pending = position
                    actions.onMoveCard(rp.id, position)
                }
            }

            override fun onDragCancel() {
                dragging = false
            }
        },
    )

    val mode = when {
        dragging -> CardMode.Dragging
        state.arranging -> CardMode.Arrange
        else -> CardMode.Normal
    }
    // Compact cards have no in-card Scan: a double-tap on an open card scans (single tap = sheet).
    val quickScan = size == CardSize.Compact && mode == CardMode.Normal && !rp.player.status.isDone
    Box(
        modifier = Modifier
            .offset { anim.value.round() }
            .zIndex(if (dragging) 10f else 0f)
            .arrangeDrag(arranging, anchor, callbacks),
    ) {
        // The card is laid out at the table scale (see tableCardScale), text and touch areas included.
        CompositionLocalProvider(LocalDensity provides cardDensity) {
            PlayerCard(
                player = rp.player,
                size = size,
                mode = mode,
                index = index,
                onClick = { if (!state.arranging) actions.onCardClick(rp.id) },
                onLongClick = actions::onEnterArrange,
                onScan = { actions.onScan(rp.id) },
                onBust = { onBust(rp) },
                onDoubleClick = if (quickScan) ({ actions.onQuickScan(rp.id) }) else null,
                showScore = !state.seating,
            )
        }
        // Seat number at the card's top-left corner, at full size so it stays readable.
        if (state.seating) SeatBadge(index + 1, Modifier.align(Alignment.TopStart).offset(x = (-6).dp, y = (-6).dp))
    }
}

/** Unclamped drag position (finger-relative), so a card follows again after hitting the clamp. */
private class DragTracker {
    var raw: Offset = Offset.Zero

    /** Table view: [raw] at the drag start. */
    var start: Offset = Offset.Zero
    var rawY: Float = 0f

    /** List view: the slot the row is over (haptic tick on change) and the edge auto-scroll loop. */
    var startY: Float = 0f
    var slot: Int = -1
    var autoScroll: Job? = null
}

/** The list view's scroll state and visible height (px), for the edge auto-scroll while dragging. */
private class ListViewport(val scroll: ScrollState, val height: Float)

/** Edge zone that auto-scrolls the list while a row is dragged, and the top speed per frame. */
private val AutoScrollEdge = 56.dp
private val AutoScrollMaxStep = 12.dp

/** A table drag that ends [moved] away from its start is a real move from [from] to [to]. */
internal fun isTableMove(moved: Offset, touchSlop: Float, from: TablePosition, to: TablePosition): Boolean =
    moved.getDistance() >= touchSlop && to != from

private fun Offset.round(): IntOffset = IntOffset(x.roundToInt(), y.roundToInt())

// ---- List view ----------------------------------------------------------------------------

@Composable
private fun ListView(state: RoundUiState, actions: RoundActions, m: RoundMetrics, onBust: (RoundPlayer) -> Unit) {
    val colors = Scan7Theme.colors
    val rowsTop = m.areaTop + ListRowsOffset
    // Regular rows when they all fit this screen, else Compact (not by player count).
    val size = listRowSize(state.playerCount, (m.areaBottom - rowsTop + BadgeRoom).value, BadgeRoom.value)

    // No "ROUND n" in the seating step (no round yet); the rows keep their place.
    val roundDescription = stringResource(R.string.round_number, state.round)
    if (!state.seating) Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .offset(y = m.areaTop)
            .padding(horizontal = Scan7Spacing.screenMargin)
            .height(40.dp)
            .alpha(if (state.arranging) ARRANGE_DIM else 1f)
            .clearAndSetSemantics {
                contentDescription = roundDescription
                heading()
            },
    ) {
        Text(stringResource(R.string.round_label), color = colors.textMuted, style = Scan7Type.capsLabel, modifier = Modifier.alignByBaseline())
        Text(state.round.toString(), color = colors.text, style = Scan7Type.roundNumberList, modifier = Modifier.alignByBaseline())
    }

    // Rows scroll when they don't fit (10 rows on a small screen); header, strip and buttons stay put.
    val count = state.playerCount
    val contentHeight = BadgeRoom + size.height * count + size.gap * (count - 1).coerceAtLeast(0)
    val viewportHeight = (m.areaBottom - rowsTop + BadgeRoom).coerceAtLeast(size.height)
    val scroll = rememberScrollState()
    val viewport = ListViewport(scroll, with(LocalDensity.current) { viewportHeight.toPx() })
    Box(
        modifier = Modifier
            .offset(y = rowsTop - BadgeRoom)
            .fillMaxWidth()
            .height(viewportHeight)
            .verticalScroll(scroll),
    ) {
        Box(Modifier.fillMaxWidth().height(contentHeight)) {
            state.players.forEachIndexed { index, rp ->
                key(rp.id) {
                    ListSeat(rp, index, size, BadgeRoom, viewport, state, actions, onBust)
                }
            }
        }
    }
}

@Composable
private fun ListSeat(
    rp: RoundPlayer,
    index: Int,
    size: RowSize,
    rowsTop: Dp,
    viewport: ListViewport,
    state: RoundUiState,
    actions: RoundActions,
    onBust: (RoundPlayer) -> Unit,
) {
    val density = LocalDensity.current
    val count = state.playerCount
    val topPx = with(density) { rowsTop.toPx() }
    val pitchPx = with(density) { (size.height + size.gap).toPx() }
    val rowHeightPx = with(density) { size.height.toPx() }
    val overshootPx = with(density) { RowOvershoot.toPx() }
    val edgePx = with(density) { AutoScrollEdge.toPx() }
    val maxStepPx = with(density) { AutoScrollMaxStep.toPx() }

    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(index) { pending = null }
    LaunchedEffect(pending) {
        if (pending != null) {
            delay(PENDING_MOVE_MS)
            pending = null
        }
    }
    val targetY = topPx + (pending ?: index) * pitchPx
    val anim = remember { Animatable(targetY) }
    LaunchedEffect(targetY, dragging) {
        if (!dragging) anim.animateTo(targetY, tween(MOVE_MS, easing = EaseOut))
    }

    val drag = remember { DragTracker() }
    val anchor = remember { DragAnchor() }
    val haptics = LocalHapticFeedback.current
    val arranging = rememberUpdatedState(state.arranging)
    val callbacks = rememberUpdatedState(
        object : ArrangeDragCallbacks {
            // Long-press haptic: the card's combinedClickable already ticks at the long press.
            override fun onEnterArrange() = actions.onEnterArrange()

            /** Shows the row at the drag position; a tick each time it crosses into another slot. */
            fun follow() {
                val shown = topPx + clampRowDrag(drag.rawY - topPx, pitchPx, count, overshootPx)
                scope.launch(start = CoroutineStart.UNDISPATCHED) { anim.snapTo(shown) }
                val slot = dropIndex(shown - topPx, pitchPx, count)
                if (slot != drag.slot) {
                    drag.slot = slot
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                }
            }

            override fun onDragStart(afterLongPress: Boolean) {
                if (!afterLongPress) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                drag.rawY = anim.value
                drag.startY = anim.value
                drag.slot = index
                dragging = true
                // Near the top / bottom edge the list scrolls under the held row (10 rows on a small
                // phone); only once the row was moved, so picking up an edge row doesn't scroll.
                drag.autoScroll?.cancel()
                drag.autoScroll = scope.launch {
                    while (true) {
                        withFrameNanos {}
                        if (abs(drag.rawY - drag.startY) < edgePx / 4) continue
                        val step = autoScrollStep(anim.value - viewport.scroll.value, rowHeightPx, viewport.height, edgePx, maxStepPx)
                        if (step == 0f) continue
                        val scrolled = viewport.scroll.scrollBy(step)
                        if (scrolled != 0f) {
                            drag.rawY += scrolled
                            follow()
                        }
                    }
                }
            }

            override fun onDrag(delta: Offset) {
                drag.rawY += delta.y
                follow()
            }

            override fun onDragEnd() {
                drag.autoScroll?.cancel()
                val shown = clampRowDrag(drag.rawY - topPx, pitchPx, count, overshootPx)
                val to = dropIndex(shown, pitchPx, count)
                haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                dragging = false
                if (to != index) {
                    pending = to
                    actions.onMoveRow(index, to)
                }
            }

            override fun onDragCancel() {
                drag.autoScroll?.cancel()
                dragging = false
            }
        },
    )

    val mode = when {
        dragging -> CardMode.Dragging
        state.arranging -> CardMode.Arrange
        else -> CardMode.Normal
    }
    val name = rp.player.name
    val moveUp = stringResource(R.string.round_move_up, name)
    val moveDown = stringResource(R.string.round_move_down, name)
    Box(
        modifier = Modifier
            .offset { IntOffset(0, anim.value.roundToInt()) }
            .zIndex(if (dragging) 10f else 0f)
            .padding(horizontal = Scan7Spacing.screenMargin),
    ) {
        PlayerRow(
            player = rp.player,
            size = size,
            mode = mode,
            index = index,
            onClick = { if (!state.arranging) actions.onCardClick(rp.id) },
            onLongClick = actions::onEnterArrange,
            onScan = { actions.onScan(rp.id) },
            onBust = { onBust(rp) },
            showScore = !state.seating,
            modifier = Modifier
                .arrangeDrag(arranging, anchor, callbacks)
                .semantics {
                    // TalkBack reorder without dragging (handoff §6).
                    customActions = buildList {
                        if (index > 0) {
                            add(CustomAccessibilityAction(moveUp) { actions.onMoveRow(index, index - 1); true })
                        }
                        if (index < count - 1) {
                            add(CustomAccessibilityAction(moveDown) { actions.onMoveRow(index, index + 1); true })
                        }
                    }
                },
        )
        if (state.seating) SeatBadge(index + 1, Modifier.offset(x = (-6).dp, y = (-6).dp))
    }
}

// ---- Previews -----------------------------------------------------------------------------

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RoundScreenTablePreview() {
    Scan7Theme { RoundScreen(FakeRoundState(count = 4).state, NoRoundActions) }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RoundScreenListPreview() {
    Scan7Theme { RoundScreen(FakeRoundState(count = 10).state, NoRoundActions) }
}
