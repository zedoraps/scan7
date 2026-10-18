package net.moesli.scan7.ui.round

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.moesli.scan7.game.TablePosition
import net.moesli.scan7.ui.components.BottomButtons
import net.moesli.scan7.ui.components.CardMode
import net.moesli.scan7.ui.components.CardSize
import net.moesli.scan7.ui.components.PlayerCard
import net.moesli.scan7.ui.components.PlayerRow
import net.moesli.scan7.ui.components.PrimaryButton
import net.moesli.scan7.ui.components.ProgressStrip
import net.moesli.scan7.ui.components.RoundView
import net.moesli.scan7.ui.components.RowSize
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/** Card / row move animation (handoff: ~220 ms ease-out). */
private const val MOVE_MS = 220

/** Opacity of the center label, progress strip and bottom buttons in arrange mode. */
private const val ARRANGE_DIM = 0.25f

/** How long the bust toast stays (before accessibility timeout adjustment). */
private const val TOAST_MS = 4_000L

/** How long a dropped card keeps its drop spot while the caller hasn't confirmed the move. */
private const val PENDING_MOVE_MS = 1_000L

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
    val buttonsTop: Dp = height - max(Scan7Spacing.bottomButtonsBottom, navBottom) - Scan7Spacing.bottomButtonsHeight
    val stripTop: Dp = buttonsTop - 36.dp
    val areaBottom: Dp = stripTop - Scan7Spacing.l
    val toastTop: Dp = buttonsTop - Scan7Spacing.s - Scan7Spacing.minTouch
}

private data class ToastData(val playerId: String, val name: String)

/**
 * The Round screen (main screen, handoff §4): header, table or list of players in seating order,
 * progress strip and bottom buttons; arrange mode; end-of-round variant ([RoundUiState.roundOver]).
 * Stateless apart from transient UI (drag in progress, the bust undo toast).
 */
@Composable
fun RoundScreen(state: RoundUiState, actions: RoundActions, modifier: Modifier = Modifier) {
    val colors = Scan7Theme.colors
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var toast by remember { mutableStateOf<ToastData?>(null) }
    val onBust: (RoundPlayer) -> Unit = {
        actions.onBust(it.id)
        toast = ToastData(it.id, it.player.name)
    }
    BackHandler(enabled = state.arranging, onBack = actions::onDone)

    BoxWithConstraints(modifier.fillMaxSize().background(colors.ground)) {
        val m = RoundMetrics(maxWidth, maxHeight, statusTop, navBottom)
        val dim = if (state.arranging) ARRANGE_DIM else 1f

        when (state.view) {
            RoundView.Table -> TableView(state, actions, m, onBust)
            RoundView.List -> ListView(state, actions, m, onBust)
        }

        RoundHeader(
            view = state.view,
            arranging = state.arranging,
            actions = actions,
            modifier = Modifier.offset(y = m.headerTop),
        )

        ProgressStrip(
            players = state.players.map { it.player },
            target = state.target,
            modifier = Modifier
                .offset(y = m.stripTop)
                .padding(horizontal = Scan7Spacing.screenMargin)
                .alpha(dim),
        )

        Box(
            modifier = Modifier
                .offset(y = m.buttonsTop)
                .padding(horizontal = Scan7Spacing.screenMargin)
                .fillMaxWidth()
                .height(Scan7Spacing.bottomButtonsHeight)
                .alpha(dim)
                .then(if (state.arranging) Modifier.clearAndSetSemantics {} else Modifier),
        ) {
            val enabled = !state.arranging
            if (state.roundOver) {
                PrimaryButton(
                    text = "Next round",
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

        toast?.let { t ->
            val a11y = LocalAccessibilityManager.current
            val timeout = a11y?.calculateRecommendedTimeoutMillis(
                TOAST_MS,
                containsIcons = false,
                containsText = true,
                containsControls = true,
            ) ?: TOAST_MS
            LaunchedEffect(t) {
                delay(timeout)
                toast = null
            }
            BustToast(
                name = t.name,
                onUndo = {
                    actions.onUndoBust(t.playerId)
                    toast = null
                },
                modifier = Modifier
                    .offset(y = m.toastTop)
                    .padding(horizontal = Scan7Spacing.screenMargin)
                    .zIndex(20f),
            )
        }
    }
}

private fun roundLabel(state: RoundUiState): String = if (state.roundOver) "END OF ROUND" else "ROUND"

// ---- Table view ---------------------------------------------------------------------------

@Composable
private fun TableView(state: RoundUiState, actions: RoundActions, m: RoundMetrics, onBust: (RoundPlayer) -> Unit) {
    val size = CardSize.forPlayerCount(state.playerCount)
    val density = LocalDensity.current
    val geometry = with(density) {
        TableGeometry(
            left = TableSideMargin.toPx(),
            top = m.areaTop.toPx(),
            width = (m.width - TableSideMargin * 2).toPx(),
            height = (m.areaBottom - m.areaTop).coerceAtLeast(size.height).toPx(),
            cardWidth = size.width.toPx(),
            cardHeight = size.height.toPx(),
        )
    }

    // Center: "ROUND" + number (72). No table graphic.
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .offset(y = m.areaTop)
            .fillMaxWidth()
            .height(m.areaBottom - m.areaTop)
            .alpha(if (state.arranging) ARRANGE_DIM else 1f),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s),
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = (if (state.roundOver) "End of round " else "Round ") + state.round
                heading()
            },
        ) {
            Text(roundLabel(state), color = Scan7Theme.colors.textMuted, style = Scan7Type.capsLabel)
            Text(state.round.toString(), color = Scan7Theme.colors.text, style = Scan7Type.roundNumberTable)
        }
    }

    state.players.forEachIndexed { index, rp ->
        key(rp.id) {
            TableSeat(rp, index, size, geometry, state, actions, onBust)
        }
    }
}

@Composable
private fun TableSeat(
    rp: RoundPlayer,
    index: Int,
    size: CardSize,
    geometry: TableGeometry,
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
    val arranging = rememberUpdatedState(state.arranging)
    val callbacks = rememberUpdatedState(
        object : ArrangeDragCallbacks {
            override fun onEnterArrange() = actions.onEnterArrange()

            override fun onDragStart() {
                drag.raw = anim.value
                dragging = true
            }

            override fun onDrag(delta: Offset) {
                drag.raw += delta
                val shown = geometry.clampTopLeft(drag.raw)
                scope.launch(start = CoroutineStart.UNDISPATCHED) { anim.snapTo(shown) }
            }

            override fun onDragEnd() {
                val position = geometry.positionOf(drag.raw)
                pending = position
                dragging = false
                actions.onMoveCard(rp.id, position)
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
    Box(
        modifier = Modifier
            .offset { anim.value.round() }
            .zIndex(if (dragging) 10f else 0f)
            .arrangeDrag(arranging, callbacks),
    ) {
        PlayerCard(
            player = rp.player,
            size = size,
            mode = mode,
            index = index,
            onClick = { if (!state.arranging) actions.onCardClick(rp.id) },
            onLongClick = actions::onEnterArrange,
            onScan = { actions.onScan(rp.id) },
            onBust = { onBust(rp) },
        )
        if (state.roundOver && rp.rank != null) {
            RankBadge(rp.rank, Modifier.offset((-6).dp, (-6).dp))
        }
    }
}

/** Unclamped drag position (finger-relative), so a card follows again after hitting the clamp. */
private class DragTracker {
    var raw: Offset = Offset.Zero
    var rawY: Float = 0f
}

private fun Offset.round(): IntOffset = IntOffset(x.roundToInt(), y.roundToInt())

// ---- List view ----------------------------------------------------------------------------

@Composable
private fun ListView(state: RoundUiState, actions: RoundActions, m: RoundMetrics, onBust: (RoundPlayer) -> Unit) {
    val size = RowSize.forPlayerCount(state.playerCount)
    val colors = Scan7Theme.colors

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .offset(y = m.areaTop)
            .padding(horizontal = Scan7Spacing.screenMargin)
            .height(40.dp)
            .alpha(if (state.arranging) ARRANGE_DIM else 1f)
            .clearAndSetSemantics {
                contentDescription = (if (state.roundOver) "End of round " else "Round ") + state.round
                heading()
            },
    ) {
        Text(roundLabel(state), color = colors.textMuted, style = Scan7Type.capsLabel, modifier = Modifier.alignByBaseline())
        Text(state.round.toString(), color = colors.text, style = Scan7Type.roundNumberList, modifier = Modifier.alignByBaseline())
    }

    val rowsTop = m.areaTop + ListRowsOffset
    state.players.forEachIndexed { index, rp ->
        key(rp.id) {
            ListSeat(rp, index, size, rowsTop, state, actions, onBust)
        }
    }
}

@Composable
private fun ListSeat(
    rp: RoundPlayer,
    index: Int,
    size: RowSize,
    rowsTop: Dp,
    state: RoundUiState,
    actions: RoundActions,
    onBust: (RoundPlayer) -> Unit,
) {
    val density = LocalDensity.current
    val count = state.playerCount
    val topPx = with(density) { rowsTop.toPx() }
    val pitchPx = with(density) { (size.height + size.gap).toPx() }
    val overshootPx = with(density) { RowOvershoot.toPx() }

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
    val arranging = rememberUpdatedState(state.arranging)
    val callbacks = rememberUpdatedState(
        object : ArrangeDragCallbacks {
            override fun onEnterArrange() = actions.onEnterArrange()

            override fun onDragStart() {
                drag.rawY = anim.value
                dragging = true
            }

            override fun onDrag(delta: Offset) {
                drag.rawY += delta.y
                val shown = topPx + clampRowDrag(drag.rawY - topPx, pitchPx, count, overshootPx)
                scope.launch(start = CoroutineStart.UNDISPATCHED) { anim.snapTo(shown) }
            }

            override fun onDragEnd() {
                val shown = clampRowDrag(drag.rawY - topPx, pitchPx, count, overshootPx)
                val to = dropIndex(shown, pitchPx, count)
                dragging = false
                if (to != index) {
                    pending = to
                    actions.onMoveRow(index, to)
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
    val name = rp.player.name
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
            modifier = Modifier
                .arrangeDrag(arranging, callbacks)
                .semantics {
                    // TalkBack reorder without dragging (handoff §6).
                    customActions = buildList {
                        if (index > 0) {
                            add(CustomAccessibilityAction("Move $name up") { actions.onMoveRow(index, index - 1); true })
                        }
                        if (index < count - 1) {
                            add(CustomAccessibilityAction("Move $name down") { actions.onMoveRow(index, index + 1); true })
                        }
                    }
                },
        )
        if (state.roundOver && rp.rank != null) {
            RankBadge(rp.rank, Modifier.offset((-6).dp, (-6).dp))
        }
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

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RoundScreenEndPreview() {
    Scan7Theme { RoundScreen(FakeRoundState(count = 6, preset = FakeRoundState.Preset.EndOfRound).state, NoRoundActions) }
}
