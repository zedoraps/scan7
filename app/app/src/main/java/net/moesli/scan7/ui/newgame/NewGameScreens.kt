package net.moesli.scan7.ui.newgame

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.moesli.scan7.data.AppGraph
import net.moesli.scan7.data.GameRepository
import net.moesli.scan7.game.DEFAULT_TARGET
import net.moesli.scan7.game.MAX_PLAYERS
import net.moesli.scan7.game.Player
import net.moesli.scan7.ui.components.CheckIcon
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
                    onClick = { onToggle(p.id) },
                    role = Role.Checkbox,
                    supporting = clash?.let { others -> "Same color as ${others.joinToString(", ") { it.name }}" },
                    modifier = Modifier.semantics { stateDescription = if (checked) "Selected" else "Not selected" },
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

/**
 * New game, step 2 (3b): selected players in seating order (drag the handle, or long-press a row,
 * to reorder; TalkBack: "Move up" / "Move down" actions), target stepper (default 200), Start.
 * [note] e.g. warns that an unfinished game will be ended; [error] shows a failed start.
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
    note: String? = null,
    error: String? = null,
    starting: Boolean = false,
) {
    val colors = Scan7Theme.colors
    val density = LocalDensity.current
    val rowHeight = RowSize.Regular.height
    val gap = Scan7Spacing.s
    val spanPx = with(density) { (rowHeight + gap).toPx() }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragIndex by remember { mutableIntStateOf(0) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val count by rememberUpdatedState(seating.size)
    val move by rememberUpdatedState(onMove)

    fun dragBy(dy: Float) {
        dragOffset += dy
        val to = dragTargetIndex(dragIndex, dragOffset, spanPx, count)
        if (to != dragIndex) {
            move(dragIndex, to)
            dragOffset -= (to - dragIndex) * spanPx
            dragIndex = to
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
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState(), enabled = draggingId == null)
                .padding(vertical = Scan7Spacing.l),
        ) {
            SectionLabel("Clockwise around the table", Modifier.padding(bottom = Scan7Spacing.m))
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                seating.forEachIndexed { index, player ->
                    key(player.id) {
                        val dragging = draggingId == player.id
                        val start: (Long) -> Unit = { id ->
                            draggingId = id
                            dragIndex = seating.indexOfFirst { it.id == id }
                            dragOffset = 0f
                        }
                        val end = {
                            draggingId = null
                            dragOffset = 0f
                        }
                        SeatRow(
                            player = player,
                            seat = index + 1,
                            dragging = dragging,
                            canMoveUp = index > 0,
                            canMoveDown = index < seating.lastIndex,
                            onMoveUp = { onMove(index, index - 1) },
                            onMoveDown = { onMove(index, index + 1) },
                            modifier = Modifier
                                .zIndex(if (dragging) 1f else 0f)
                                .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                                .pointerInput(player.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { start(player.id) },
                                        onDragEnd = end,
                                        onDragCancel = end,
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragBy(amount.y)
                                        },
                                    )
                                },
                            handleModifier = Modifier.pointerInput(player.id) {
                                detectDragGestures(
                                    onDragStart = { start(player.id) },
                                    onDragEnd = end,
                                    onDragCancel = end,
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragBy(amount.y)
                                    },
                                )
                            },
                        )
                    }
                }
            }
            TargetStepper(
                target = target,
                onChange = onTargetChange,
                modifier = Modifier.padding(top = Scan7Spacing.xl),
            )
        }
        val message = error ?: note
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
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
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
            .clearAndSetSemantics {
                contentDescription = "Seat $seat, ${player.name}"
                customActions = buildList {
                    if (canMoveUp) add(CustomAccessibilityAction("Move up") { onMoveUp(); true })
                    if (canMoveDown) add(CustomAccessibilityAction("Move down") { onMoveDown(); true })
                }
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

/** Target stepper: "TARGET / points to win", [−] 200 [+] in steps of [TARGET_STEP]. */
@Composable
fun TargetStepper(target: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = Scan7Theme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
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
            modifier = Modifier
                .width(80.dp)
                .semantics { contentDescription = "Target $target points" },
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
    var editor by remember { mutableStateOf<EditorTarget?>(null) }
    var starting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val list = players ?: return
    val byId = list.associateBy { it.id }
    // Players deleted meanwhile drop out of the selection.
    val liveSelected = selected.filter { it in byId }

    BackHandler(enabled = step == 2) { step = 1 }
    when (step) {
        1 -> PickPlayersScreen(
            players = list,
            selected = liveSelected,
            onToggle = { selected = toggleSelection(liveSelected, it) },
            onNewPlayer = { editor = EditorTarget.New },
            onFixColor = { editor = EditorTarget.Edit(it) },
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
            onStart = {
                starting = true
                error = null
                scope.launch {
                    runCatching { repository.startGame(seating.filter { it in byId }, target) }
                        .onSuccess(onGameStarted)
                        .onFailure { error = it.message ?: "Couldn't start the game" }
                    starting = false
                }
            },
            onBack = { step = 1 },
            note = active?.let { "Starting ends the unfinished game (round ${it.state.currentRound.number})." },
            error = error,
            starting = starting,
            modifier = modifier,
        )
    }
    editor?.let { t ->
        PlayerEditorHost(
            target = t,
            players = list,
            repository = repository,
            onClose = { editor = null },
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
        SeatingScreen(SampleStoredPlayers.take(4), 200, { _, _ -> }, {}, {}, {}, note = "Starting ends the unfinished game (round 3).")
    }
}
