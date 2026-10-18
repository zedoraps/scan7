package net.moesli.scan7.ui.newgame

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.moesli.scan7.data.AppGraph
import net.moesli.scan7.data.GameRepository
import net.moesli.scan7.data.outlivingUi
import net.moesli.scan7.game.DEFAULT_TARGET
import net.moesli.scan7.game.MAX_PLAYERS
import net.moesli.scan7.game.Player
import net.moesli.scan7.ui.components.CheckIcon
import net.moesli.scan7.ui.components.Confirmations
import net.moesli.scan7.ui.components.Scan7ConfirmDialog
import net.moesli.scan7.ui.components.DragHandleIcon
import net.moesli.scan7.ui.components.IconButton
import net.moesli.scan7.ui.components.MinusIcon
import net.moesli.scan7.ui.components.PlayerToken
import net.moesli.scan7.ui.components.PlusIcon
import net.moesli.scan7.ui.components.PrimaryButton
import net.moesli.scan7.ui.components.RowSize
import net.moesli.scan7.ui.components.ScreenHeader
import net.moesli.scan7.ui.components.SecondaryButton
import net.moesli.scan7.ui.components.SectionLabel
import net.moesli.scan7.ui.components.bottomButtonsInsets
import net.moesli.scan7.ui.components.draggingCardShadow
import net.moesli.scan7.ui.components.headerInsets
import net.moesli.scan7.ui.components.openCardShadow
import net.moesli.scan7.ui.players.AddPlayerRow
import net.moesli.scan7.ui.players.EditorTarget
import net.moesli.scan7.ui.players.PlayerEditorHost
import net.moesli.scan7.ui.players.SampleStoredPlayers
import net.moesli.scan7.ui.players.SetupPlayerRow
import net.moesli.scan7.ui.players.color
import net.moesli.scan7.ui.players.colorConflicts
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

// ---- Step 1: pick players ------------------------------------------------------------------

/**
 * New game, step 1 (3): stored players with checkboxes (pick order = initial seating order),
 * "+ New player" inline, max [MAX_PLAYERS]. Players who share a color with another selected player
 * show "Same color as …" and a Change button ([onFixColor] → editor); Next stays disabled until
 * every selected player has their own color.
 */
@Composable
fun PickPlayersScreen(
    players: List<Player>,
    selected: List<Long>,
    onToggle: (Long) -> Unit,
    onNewPlayer: () -> Unit,
    onFixColor: (Player) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Scan7Theme.colors
    val haptics = LocalHapticFeedback.current
    val byId = players.associateBy { it.id }
    val selectedPlayers = selected.mapNotNull { byId[it] }
    val conflicts = colorConflicts(selectedPlayers)
    val full = selectedPlayers.size >= MAX_PLAYERS
    val hint = when {
        conflicts.isNotEmpty() -> "Two selected players share a color. Change one to continue."
        selectedPlayers.size < MIN_PLAYERS && players.isNotEmpty() -> "Pick at least $MIN_PLAYERS players."
        full -> "$MAX_PLAYERS players is the maximum."
        else -> null
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground)
            .padding(horizontal = Scan7Spacing.screenMargin),
    ) {
        ScreenHeader(
            title = "New game",
            subtitle = "Who's playing? · ${selectedPlayers.size} of $MAX_PLAYERS",
            onBack = onBack,
            modifier = Modifier.headerInsets(),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = Scan7Spacing.l),
        ) {
            if (players.isEmpty()) {
                Text(
                    "No players yet. Add everyone who's at the table — they're saved for next time.",
                    color = colors.textMuted,
                    style = Scan7Type.text13,
                    modifier = Modifier.padding(bottom = Scan7Spacing.s),
                )
            }
            players.forEach { p ->
                val checked = p.id in selected
                val clash = conflicts[p.id]
                SetupPlayerRow(
                    player = p,
                    active = checked,
                    enabled = checked || !full,
                    onClick = {
                        haptics.performHapticFeedback(if (checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                        onToggle(p.id)
                    },
                    checked = checked,
                    supporting = clash?.let { others -> "Same color as ${others.joinToString(", ") { it.name }}" },
                    leading = { Checkbox(checked) },
                    trailing = {
                        if (clash != null) {
                            SecondaryButton(
                                text = "Change",
                                onClick = { onFixColor(p) },
                                height = Scan7Spacing.minTouch,
                                cornerRadius = Scan7Radii.actionRow,
                                textStyle = Scan7Type.text12,
                                contentDescription = "Change ${p.name}'s color",
                            )
                        }
                    },
                )
            }
            AddPlayerRow(onClick = onNewPlayer, text = if (players.isEmpty()) "Add your first player" else "New player")
        }
        if (hint != null) {
            Text(hint, color = colors.textMuted, style = Scan7Type.text12, modifier = Modifier.padding(bottom = Scan7Spacing.m))
        }
        PrimaryButton(
            text = "Next",
            onClick = onNext,
            enabled = canContinue(selectedPlayers.size, conflicts.isNotEmpty()),
            modifier = Modifier
                .fillMaxWidth()
                .bottomButtonsInsets(),
        )
    }
}

/** 24 dp checkbox: cyan with a dark check when on, `line` outline when off. Decorative (row has the state). */
@Composable
private fun Checkbox(checked: Boolean) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.tagCard)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(24.dp)
            .clip(shape)
            .background(if (checked) colors.accent else colors.ground)
            .border(1.5.dp, if (checked) colors.accent else colors.line, shape),
    ) {
        if (checked) CheckIcon(colors.onAccent)
    }
}

// ---- Step 2: seating order + target --------------------------------------------------------

/** Lazy list items before the first seat (the section label). */
private const val SEAT_ITEM_OFFSET = 1
private val AutoScrollEdge = 64.dp
private val AutoScrollMaxStep = 12.dp
private val SeatGlide = tween<IntOffset>(durationMillis = 220, easing = EaseOut)

/**
 * New game, step 2 (3b): selected players in seating order (drag the handle, or long-press a row,
 * to reorder; TalkBack: "Move up" / "Move down" actions), target stepper (default 200), Start.
 * [error] shows a failed start. Start asks first if a game is unfinished ([NewGameRoute]).
 */
@Composable
fun SeatingScreen(
    seating: List<Player>,
    target: Int,
    onMove: (from: Int, to: Int) -> Unit,
    onTargetChange: (Int) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    starting: Boolean = false,
) {
    val colors = Scan7Theme.colors
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val rowHeight = RowSize.Regular.height
    val gap = Scan7Spacing.s
    val spanPx = with(density) { (rowHeight + gap).toPx() }
    val edgePx = with(density) { AutoScrollEdge.toPx() }
    val maxStepPx = with(density) { AutoScrollMaxStep.toPx() }
    val listState = rememberLazyListState()
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragIndex by remember { mutableIntStateOf(0) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val ids by rememberUpdatedState(seating.map { it.id })
    val move by rememberUpdatedState(onMove)

    // The lazy list keeps its scroll position by the first visible item's key; when that item is
    // the one moving, pin the position by index instead, or the list jumps with it.
    fun moveKeepingScroll(from: Int, to: Int) {
        val first = listState.firstVisibleItemIndex
        if (first == from + SEAT_ITEM_OFFSET || first == to + SEAT_ITEM_OFFSET) {
            listState.requestScrollToItem(first, listState.firstVisibleItemScrollOffset)
        }
        move(from, to)
    }

    fun dragBy(dy: Float) {
        dragOffset += dy
        val to = dragTargetIndex(dragIndex, dragOffset, spanPx, ids.size)
        if (to != dragIndex) {
            moveKeepingScroll(dragIndex, to)
            dragOffset -= (to - dragIndex) * spanPx
            dragIndex = to
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }

    fun startDrag(id: Long, feedback: HapticFeedbackType) {
        draggingId = id
        dragIndex = ids.indexOf(id).coerceAtLeast(0)
        dragOffset = 0f
        haptics.performHapticFeedback(feedback)
    }

    fun endDrag() {
        draggingId = null
        dragOffset = 0f
    }

    // Edge auto-scroll while a row is dragged near the top / bottom of the list (no platform
    // reorder for lazy lists; a library was not approved, see PLATFORM_AUDIT #11).
    LaunchedEffect(draggingId) {
        val id = draggingId ?: return@LaunchedEffect
        while (isActive) {
            withFrameNanos { }
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.key == id } ?: continue
            // The row's middle (near the finger) decides, so picking up an edge row doesn't scroll at once.
            val middle = item.offset + item.size / 2f + dragOffset
            val step = autoScrollStep(middle, middle, info.viewportStartOffset.toFloat(), info.viewportEndOffset.toFloat(), edgePx, maxStepPx)
            if (step != 0f) {
                val consumed = listState.scrollBy(step)
                // The finger stays put while the content scrolls under it: the row follows the finger.
                if (consumed != 0f) dragBy(consumed)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground)
            .padding(horizontal = Scan7Spacing.screenMargin),
    ) {
        ScreenHeader(
            title = "Seating order",
            subtitle = "Drag players into the order they sit",
            onBack = onBack,
            modifier = Modifier.headerInsets(),
        )
        LazyColumn(
            state = listState,
            userScrollEnabled = draggingId == null,
            verticalArrangement = Arrangement.spacedBy(gap),
            contentPadding = PaddingValues(vertical = Scan7Spacing.l),
            modifier = Modifier.weight(1f),
        ) {
            item(key = "label") {
                SectionLabel("Clockwise around the table", Modifier.padding(bottom = Scan7Spacing.m - gap))
            }
            itemsIndexed(seating, key = { _, p -> p.id }) { index, player ->
                val dragging = draggingId == player.id
                SeatRow(
                    player = player,
                    seat = index + 1,
                    dragging = dragging,
                    moves = seatMoves(index, seating.size),
                    onMove = { to -> moveKeepingScroll(index, to) },
                    modifier = Modifier
                        // The dragged row follows the finger itself; the others glide to their slots.
                        .animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = if (dragging) null else SeatGlide)
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                        .pointerInput(player.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { startDrag(player.id, HapticFeedbackType.LongPress) },
                                onDragEnd = ::endDrag,
                                onDragCancel = ::endDrag,
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragBy(amount.y)
                                },
                            )
                        },
                    handleModifier = Modifier.pointerInput(player.id) {
                        detectDragGestures(
                            onDragStart = { startDrag(player.id, HapticFeedbackType.GestureThresholdActivate) },
                            onDragEnd = ::endDrag,
                            onDragCancel = ::endDrag,
                            onDrag = { change, amount ->
                                change.consume()
                                dragBy(amount.y)
                            },
                        )
                    },
                )
            }
            item(key = "target") {
                TargetStepper(
                    target = target,
                    onChange = onTargetChange,
                    modifier = Modifier.padding(top = Scan7Spacing.xl - gap),
                )
            }
        }
        val message = error
        if (message != null) {
            Text(message, color = colors.textMuted, style = Scan7Type.text12, modifier = Modifier.padding(bottom = Scan7Spacing.m))
        }
        PrimaryButton(
            text = "Start game",
            onClick = onStart,
            enabled = !starting && seating.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .bottomButtonsInsets(),
        )
    }
}

@Composable
private fun SeatRow(
    player: Player,
    seat: Int,
    dragging: Boolean,
    moves: List<SeatMove>,
    onMove: (to: Int) -> Unit,
    modifier: Modifier,
    handleModifier: Modifier,
) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.cardLarge)
    Row(
        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(RowSize.Regular.height)
            .graphicsLayer {
                val s = if (dragging) 1.06f else 1f
                scaleX = s
                scaleY = s
            }
            .then(if (dragging) Modifier.draggingCardShadow(shape) else Modifier.openCardShadow(shape))
            .clip(shape)
            .background(colors.surface)
            .border(if (dragging) 2.dp else 1.5.dp, if (dragging) colors.accent else colors.line, shape)
            // TalkBack: one stop per row; reorder with the actions menu (drag needs sight).
            .clearAndSetSemantics {
                contentDescription = "Seat $seat, ${player.name}"
                customActions = moves.map { m -> CustomAccessibilityAction(m.label) { onMove(m.to); true } }
            }
            .padding(start = Scan7Spacing.l),
    ) {
        Text(
            seat.toString(),
            color = colors.textMuted,
            style = Scan7Type.total15,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(24.dp),
        )
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp, 40.dp)) {
            PlayerToken(player.initial, player.color, RowSize.Regular.token)
        }
        Text(
            player.name,
            color = colors.text,
            style = Scan7Type.text15,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = handleModifier
                .fillMaxHeight()
                .width(56.dp),
        ) {
            DragHandleIcon(if (dragging) colors.accent else colors.textMuted)
        }
    }
}

/**
 * Target stepper: "TARGET / points to win", [−] 200 [+] in steps of [TARGET_STEP]. For TalkBack
 * it is one adjustable control (range info + set progress: swipe up / down or volume keys)
 * instead of three stops; the buttons stay for touch.
 */
@Composable
fun TargetStepper(target: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = Scan7Theme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = "Target, points to win"
                stateDescription = "$target points"
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = target.toFloat(),
                    range = MIN_TARGET.toFloat()..MAX_TARGET.toFloat(),
                    steps = TARGET_STEPS,
                )
                setProgress { value ->
                    val next = targetFromProgress(value)
                    if (next != target) onChange(next)
                    true
                }
            },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Scan7Spacing.xxs), modifier = Modifier.weight(1f)) {
            SectionLabel("Target")
            Text("Points to win", color = colors.textMuted, style = Scan7Type.text12)
        }
        IconButton(
            onClick = { onChange(steppedTarget(target, -1)) },
            contentDescription = "Lower target",
            enabled = target > MIN_TARGET,
        ) { MinusIcon(colors.text) }
        Text(
            target.toString(),
            color = colors.text,
            style = Scan7Type.total24,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.widthIn(min = 80.dp),
        )
        IconButton(
            onClick = { onChange(steppedTarget(target, 1)) },
            contentDescription = "Raise target",
            enabled = target < MAX_TARGET,
        ) { PlusIcon(colors.text) }
    }
}

// ---- Wired flow ----------------------------------------------------------------------------

/**
 * New game flow (3 → 3b) wired to the real [GameRepository]: pick players (+ new player / fix a
 * color in the editor), seating order + target, Start → `repository.startGame` → [onGameStarted].
 * With [initialSeating] (e.g. "New game with same players") it opens on step 2, prefilled.
 */
@Composable
fun NewGameRoute(
    onBack: () -> Unit,
    onGameStarted: (gameId: Long) -> Unit,
    modifier: Modifier = Modifier,
    initialSeating: List<Long>? = null,
    initialTarget: Int = DEFAULT_TARGET,
    repository: GameRepository = AppGraph.repository(LocalContext.current),
) {
    val players by repository.players.collectAsStateWithLifecycle(initialValue = null)
    val active by repository.activeGame.collectAsStateWithLifecycle(initialValue = null)
    var step by rememberSaveable { mutableIntStateOf(if (initialSeating.isNullOrEmpty()) 1 else 2) }
    var selected by rememberSaveable(stateSaver = LongListSaver) { mutableStateOf(initialSeating.orEmpty()) }
    var seating by rememberSaveable(stateSaver = LongListSaver) { mutableStateOf(initialSeating.orEmpty()) }
    var target by rememberSaveable { mutableIntStateOf(initialTarget) }
    // Saved as a key, so an open editor survives rotation / process death.
    var editorKey by rememberSaveable { mutableStateOf<Long?>(null) }
    var starting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val list = players ?: return
    val byId = list.associateBy { it.id }
    // Players deleted meanwhile drop out of the selection.
    val liveSelected = selected.filter { it in byId }

    // Start while a game is unfinished asks first (starting ends that game for good).
    var confirmStart by rememberSaveable { mutableStateOf(false) }
    val startConfirm = Confirmations.startGame(active?.state?.currentRound?.number)
    val start = {
        starting = true
        error = null
        scope.launch {
            // Outlives this screen: a started transaction isn't rolled back by Back.
            runCatching { outlivingUi { repository.startGame(seating.filter { it in byId }, target) } }
                .onSuccess(onGameStarted)
                .onFailure { error = it.message ?: "Couldn't start the game" }
            starting = false
        }
    }
    if (confirmStart && startConfirm != null && step == 2) {
        Scan7ConfirmDialog(
            startConfirm,
            onConfirm = {
                confirmStart = false
                start()
            },
            onCancel = { confirmStart = false },
        )
    }

    BackHandler(enabled = step == 2) { step = 1 }
    when (step) {
        1 -> PickPlayersScreen(
            players = list,
            selected = liveSelected,
            onToggle = { selected = toggleSelection(liveSelected, it) },
            onNewPlayer = { editorKey = EditorTarget.New.key },
            onFixColor = { editorKey = EditorTarget.Edit(it).key },
            onNext = {
                val picked = liveSelected
                scope.launch {
                    // Same players as a previous game: start from that game's order (old table spots).
                    seating = if (seating.toSet() == picked.toSet()) seating
                    else repository.previousSeating(picked) ?: seatingFor(picked, seating)
                    error = null
                    step = 2
                }
            },
            onBack = onBack,
            modifier = modifier,
        )
        else -> SeatingScreen(
            seating = seating.filter { it in byId }.map { byId.getValue(it) },
            target = target,
            onMove = { from, to -> seating = seating.filter { it in byId }.moved(from, to) },
            onTargetChange = { target = it },
            onStart = { if (startConfirm == null) start() else confirmStart = true },
            onBack = { step = 1 },
            error = error,
            starting = starting,
            modifier = modifier,
        )
    }
    EditorTarget.fromKey(editorKey, list)?.let { t ->
        PlayerEditorHost(
            target = t,
            players = list,
            repository = repository,
            onClose = { editorKey = null },
            onSaved = { id -> if (t == EditorTarget.New && id !in selected) selected = toggleSelection(liveSelected, id) },
        )
    }
}

private val LongListSaver = Saver<List<Long>, LongArray>(save = { it.toLongArray() }, restore = { it.toList() })

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PickPlayersPreview() {
    Scan7Theme {
        PickPlayersScreen(SampleStoredPlayers, listOf(2, 3, 6), {}, {}, {}, {}, {})
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SeatingPreview() {
    Scan7Theme {
        SeatingScreen(SampleStoredPlayers.take(4), 200, { _, _ -> }, {}, {}, {})
    }
}
