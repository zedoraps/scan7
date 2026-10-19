package net.moesli.scan7.ui.newgame

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import net.moesli.scan7.R
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.moesli.scan7.data.AppGraph
import net.moesli.scan7.data.GameRepository
import net.moesli.scan7.data.outlivingUi
import net.moesli.scan7.game.DEFAULT_TARGET
import net.moesli.scan7.game.MAX_PLAYERS
import net.moesli.scan7.game.MIN_PLAYERS
import net.moesli.scan7.game.Player
import net.moesli.scan7.game.TablePosition
import net.moesli.scan7.game.ViewMode
import net.moesli.scan7.ui.components.ContentWithBottomBar
import net.moesli.scan7.ui.components.CheckIcon
import net.moesli.scan7.ui.components.Confirmations
import net.moesli.scan7.ui.components.IconButton
import net.moesli.scan7.ui.components.MinusIcon
import net.moesli.scan7.ui.components.PlusIcon
import net.moesli.scan7.ui.components.PrimaryButton
import net.moesli.scan7.ui.components.RoundView
import net.moesli.scan7.ui.components.Scan7ConfirmDialog
import net.moesli.scan7.ui.components.ScreenHeader
import net.moesli.scan7.ui.components.SecondaryButton
import net.moesli.scan7.ui.components.SectionLabel
import net.moesli.scan7.ui.components.ViewToggle
import net.moesli.scan7.ui.components.bottomButtonsInsets
import net.moesli.scan7.ui.components.headerInsets
import net.moesli.scan7.ui.components.centeredMaxWidth
import net.moesli.scan7.ui.players.AddPlayerRow
import net.moesli.scan7.ui.players.EditorTarget
import net.moesli.scan7.ui.players.PlayerEditorHost
import net.moesli.scan7.ui.players.SampleStoredPlayers
import net.moesli.scan7.ui.players.SetupPlayerRow
import net.moesli.scan7.ui.players.toUi
import net.moesli.scan7.ui.round.RoundActions
import net.moesli.scan7.ui.round.RoundPlayer
import net.moesli.scan7.ui.round.RoundScreen
import net.moesli.scan7.ui.round.RoundUiState
import net.moesli.scan7.ui.round.toRoundView
import net.moesli.scan7.ui.round.toViewMode
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

// ---- Step 1: pick players + target -----------------------------------------------------------

/**
 * New game, step 1 (3): stored players with checkboxes (pick order = initial seating order),
 * "+ New player" inline, max [MAX_PLAYERS]. Pinned above the buttons: the compact target row.
 * Bottom: full-width **Next** (seating step; arranging there is optional, Start game works as is).
 * Avatar colors may repeat within a game (the editor only marks the colors the selected players
 * already use). [error] shows a failed start.
 *
 * Also the round menu's "Players" step ([GamePlayersRoute]): there with its own [title] /
 * [subtitle], no target row ([onTargetChange] `null`), a
 * [nextLabel] "Done" and a [supporting] line per row ("Joins in round 4" / "Leaves the game").
 */
@Composable
fun PickPlayersScreen(
    players: List<Player>,
    selected: List<Long>,
    target: Int,
    onToggle: (Long) -> Unit,
    onNewPlayer: () -> Unit,
    onTargetChange: ((Int) -> Unit)?,
    onNext: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    starting: Boolean = false,
    title: String = stringResource(R.string.newgame_title),
    subtitle: String? = null,
    nextLabel: String = stringResource(R.string.action_next),
    supporting: (Player) -> String? = { null },
) {
    val colors = Scan7Theme.colors
    val haptics = LocalHapticFeedback.current
    val byId = players.associateBy { it.id }
    val selectedPlayers = selected.mapNotNull { byId[it] }
    val full = selectedPlayers.size >= MAX_PLAYERS
    val hint = when {
        error != null -> error
        selectedPlayers.size < MIN_PLAYERS && players.isNotEmpty() -> stringResource(R.string.newgame_min_players, MIN_PLAYERS)
        full -> stringResource(R.string.newgame_max_players, MAX_PLAYERS)
        else -> null
    }
    val canGo = canContinue(selectedPlayers.size) && !starting
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground)
            .centeredMaxWidth()
            .padding(horizontal = Scan7Spacing.screenMargin),
    ) {
        ScreenHeader(
            title = title,
            subtitle = subtitle ?: stringResource(R.string.newgame_subtitle, selectedPlayers.size, MAX_PLAYERS),
            onBack = onBack,
            modifier = Modifier.headerInsets(),
        )
        ContentWithBottomBar(
            bottomBar = {
                // Floats over the list end (the list scrolls clear of it, at any font scale).
                if (hint != null) {
                    Text(hint, color = colors.textMuted, style = Scan7Type.text12, modifier = Modifier.padding(bottom = Scan7Spacing.s))
                }
                if (onTargetChange != null) {
                    TargetStepper(target = target, onChange = onTargetChange, modifier = Modifier.padding(bottom = Scan7Spacing.m))
                }
                PrimaryButton(text = nextLabel, onClick = onNext, enabled = canGo, modifier = Modifier.fillMaxWidth())
            },
        ) { bottomPadding ->
            Column(
                verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = Scan7Spacing.l, bottom = Scan7Spacing.l + bottomPadding),
            ) {
                if (players.isEmpty()) {
                    Text(
                        stringResource(R.string.newgame_no_players),
                        color = colors.textMuted,
                        style = Scan7Type.text13,
                        modifier = Modifier.padding(bottom = Scan7Spacing.s),
                    )
                }
                players.forEach { p ->
                    val checked = p.id in selected
                    SetupPlayerRow(
                        player = p,
                        active = checked,
                        enabled = checked || !full,
                        onClick = {
                            haptics.performHapticFeedback(if (checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                            onToggle(p.id)
                        },
                        checked = checked,
                        supporting = supporting(p),
                        leading = { Checkbox(checked) },
                    )
                }
                AddPlayerRow(onClick = onNewPlayer, text = stringResource(if (players.isEmpty()) R.string.players_add_first else R.string.players_new))
            }
        }
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

/**
 * Compact target row: "TARGET" · [−] 200 [+] in steps of [TARGET_STEP], on a `surface` strip. For
 * TalkBack it is one adjustable control ("Target, points to win"; range info + set progress: swipe
 * up / down or volume keys) instead of three stops; the buttons stay for touch.
 */
@Composable
fun TargetStepper(target: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = Scan7Theme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Scan7Spacing.minTouch + Scan7Spacing.s)
            .clip(RoundedCornerShape(Scan7Radii.card))
            .background(colors.surface)
            .padding(start = Scan7Spacing.l, end = Scan7Spacing.xxs)
            .targetAdjustable(target, stringResource(R.string.newgame_target_description), onChange),
    ) {
        SectionLabel(stringResource(R.string.newgame_target), Modifier.weight(1f))
        TargetButtons(target, onChange)
    }
}

/**
 * The target as one adjustable TalkBack control ([description], "200 points"; range info + set
 * progress: swipe up / down or volume keys) instead of three stops (New game row, round menu).
 */
@Composable
fun Modifier.targetAdjustable(target: Int, description: String, onChange: (Int) -> Unit): Modifier {
    val state = pluralStringResource(R.plurals.points, target, target)
    return clearAndSetSemantics {
        contentDescription = description
        stateDescription = state
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
    }
}

/** [−] 200 [+] in steps of [TARGET_STEP] between [MIN_TARGET] and [MAX_TARGET]. */
@Composable
fun TargetButtons(target: Int, onChange: (Int) -> Unit) {
    val colors = Scan7Theme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.xxs)) {
        IconButton(
            onClick = { onChange(steppedTarget(target, -1)) },
            contentDescription = stringResource(R.string.newgame_target_lower),
            enabled = target > MIN_TARGET,
        ) { MinusIcon(colors.text) }
        Text(
            target.toString(),
            color = colors.text,
            style = Scan7Type.total18,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.widthIn(min = 56.dp),
        )
        IconButton(
            onClick = { onChange(steppedTarget(target, 1)) },
            contentDescription = stringResource(R.string.newgame_target_raise),
            enabled = target < MAX_TARGET,
        ) { PlusIcon(colors.text) }
    }
}

// ---- Step 2: seats -----------------------------------------------------------------------------

/**
 * New game, step 2 (3b): the round screen's table / list in arrange mode (same cards, drag rules
 * and Table | List toggle; seat numbers on the cards), so the spots set here are exactly the
 * game's table. Header: back · "Seats" · view toggle; bottom: drag hint + Start game.
 * [players] are in seating order, [positions] their table spots. Start asks first if a game is
 * unfinished ([NewGameRoute]); [error] shows a failed start. The round menu's "Players" flow
 * reuses it with [startLabel] "Done" and the players' game [totals].
 */
@Composable
fun SeatingScreen(
    players: List<Player>,
    positions: List<TablePosition>,
    view: RoundView,
    target: Int,
    onToggleView: (RoundView) -> Unit,
    onMoveCard: (playerId: Long, position: TablePosition) -> Unit,
    onMoveRow: (from: Int, to: Int) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    starting: Boolean = false,
    startLabel: String = stringResource(R.string.seats_start),
    totals: Map<Long, Int> = emptyMap(),
) {
    val colors = Scan7Theme.colors
    val haptics = LocalHapticFeedback.current
    val state = RoundUiState(
        round = 1,
        target = target,
        players = players.zip(positions) { p, pos -> RoundPlayer(p.toUi(total = totals[p.id] ?: 0, target = target), pos) },
        view = view,
        arranging = true,
        seating = true,
    )
    val actions = object : RoundActions {
        override fun onMoveCard(playerId: String, position: TablePosition) = onMoveCard(playerId.toLong(), position)

        override fun onMoveRow(from: Int, to: Int) = onMoveRow(from, to)

        // Back in arrange mode = back to step 1.
        override fun onDone() = onBack()
    }
    RoundScreen(
        state = state,
        actions = actions,
        modifier = modifier,
        header = { headerModifier ->
            ScreenHeader(
                title = stringResource(R.string.seats_title),
                onBack = onBack,
                modifier = headerModifier.padding(horizontal = Scan7Spacing.screenMargin),
            ) {
                ViewToggle(
                    selected = view,
                    onSelect = {
                        if (it != view) {
                            haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                            onToggleView(it)
                        }
                    },
                )
            }
        },
        bottomBar = { barModifier ->
            Column(barModifier) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Text(
                        text = error ?: stringResource(if (view == RoundView.Table) R.string.seats_hint_table else R.string.seats_hint_list),
                        color = colors.textMuted,
                        style = Scan7Type.text12,
                        textAlign = TextAlign.Center,
                        // "…, or just start" says arranging is optional: may wrap to a second line.
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                PrimaryButton(
                    text = startLabel,
                    onClick = onStart,
                    enabled = !starting && players.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

// ---- Wired flow ----------------------------------------------------------------------------

/** Which step asked to confirm the start (a game is unfinished): none, or Seats (2). */
private const val CONFIRM_NONE = 0

/**
 * New game flow (3 → 3b) wired to the real [GameRepository]: pick players (+ new player / fix a
 * color in the editor) and the target;
 * Next → seats (table / list arrange, starting from the last game's spots when the same players
 * played before) → Start → `repository.startGame` with those positions → [onGameStarted].
 * With [initialSeating] ("New game with same players") it opens on the seats step, prefilled.
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
    val preferredView by repository.preferredViewMode.collectAsStateWithLifecycle(initialValue = null)
    var step by rememberSaveable { mutableIntStateOf(if (initialSeating.isNullOrEmpty()) 1 else 2) }
    var selected by rememberSaveable(stateSaver = LongListSaver) { mutableStateOf(initialSeating.orEmpty()) }
    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf<SeatingDraft?>(null) }
    var target by rememberSaveable { mutableIntStateOf(initialTarget) }
    // Saved as a key, so an open editor survives rotation / process death.
    var editorKey by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmFrom by rememberSaveable { mutableIntStateOf(CONFIRM_NONE) }
    var starting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Opened on the seats step ("New game with same players"): that game's order and spots.
    LaunchedEffect(Unit) {
        if (step == 2 && draft == null) {
            val order = initialSeating.orEmpty()
            draft = SeatingDraft.of(order, repository.previousPositions(order))
        }
    }

    val list = players ?: return
    val byId = list.associateBy { it.id }
    // Players deleted meanwhile drop out of the selection and the seats.
    val liveSelected = selected.filter { it in byId }
    val liveDraft = draft?.retain(byId.keys)

    // Start while a game is unfinished asks first (starting ends that game for good).
    val startConfirm = Confirmations.startGame(active?.state?.currentRound?.number)
    val startError = stringResource(R.string.seats_error_start)
    fun start() {
        val seats = liveDraft ?: return
        if (seats.ids.isEmpty()) return
        starting = true
        error = null
        scope.launch {
            // Outlives this screen: a started transaction isn't rolled back by Back.
            runCatching { outlivingUi { repository.startGame(seats.ids, target, seats.positions) } }
                .onSuccess(onGameStarted)
                .onFailure { error = startError }
            starting = false
        }
    }
    fun requestStart() {
        if (startConfirm == null) start() else confirmFrom = step
    }
    if (confirmFrom == step && startConfirm != null) {
        Scan7ConfirmDialog(
            startConfirm,
            onConfirm = {
                confirmFrom = CONFIRM_NONE
                start()
            },
            onCancel = { confirmFrom = CONFIRM_NONE },
        )
    }

    BackHandler(enabled = step == 2) { step = 1 }
    when (step) {
        1 -> PickPlayersScreen(
            players = list,
            selected = liveSelected,
            target = target,
            onToggle = { selected = toggleSelection(liveSelected, it) },
            onNewPlayer = { editorKey = EditorTarget.New.key },
            onTargetChange = { target = it },
            onNext = {
                val picked = liveSelected
                scope.launch {
                    val current = liveDraft
                    draft = if (current != null && current.ids.toSet() == picked.toSet()) {
                        current
                    } else {
                        // Same players as a previous game: start from that game's order and table spots.
                        val previous = repository.previousSeating(picked)
                        if (previous != null) {
                            SeatingDraft.of(previous, repository.previousPositions(picked))
                        } else {
                            SeatingDraft.of(seatingFor(picked, current?.ids.orEmpty()))
                        }
                    }
                    error = null
                    step = 2
                }
            },
            onBack = onBack,
            error = error,
            starting = starting,
            modifier = modifier,
        )
        else -> {
            val seats = liveDraft
            if (seats == null) {
                // Loading the previous game's spots (one frame).
                Box(modifier.fillMaxSize().background(Scan7Theme.colors.ground))
            } else {
                SeatingScreen(
                    players = seats.ids.map { byId.getValue(it) },
                    positions = seats.positions,
                    view = (preferredView ?: ViewMode.defaultFor(seats.seats.size)).toRoundView(),
                    target = target,
                    onToggleView = { v -> scope.launch { repository.setPreferredViewMode(v.toViewMode()) } },
                    onMoveCard = { id, position -> draft = seats.moveCard(id, position) },
                    onMoveRow = { from, to -> draft = seats.swapRows(from, to) },
                    onStart = { if (!starting) requestStart() },
                    onBack = { step = 1 },
                    error = error,
                    starting = starting,
                    modifier = modifier,
                )
            }
        }
    }
    EditorTarget.fromKey(editorKey, list)?.let { t ->
        PlayerEditorHost(
            target = t,
            // The game being set up: a new player gets a color none of them uses; their colors get a dot.
            players = liveSelected.map { byId.getValue(it) },
            repository = repository,
            dotPlayers = liveSelected.map { byId.getValue(it) },
            onClose = { editorKey = null },
            onSaved = { id -> if (t == EditorTarget.New && id !in selected) selected = toggleSelection(liveSelected, id) },
        )
    }
}

private val LongListSaver = Saver<List<Long>, LongArray>(save = { it.toLongArray() }, restore = { it.toList() })

private val DraftSaver = Saver<SeatingDraft?, LongArray>(
    save = { d -> d?.let(SeatingDraft::save) },
    restore = { SeatingDraft.restore(it) },
)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PickPlayersPreview() {
    Scan7Theme {
        PickPlayersScreen(SampleStoredPlayers, listOf(2, 3, 6), 200, {}, {}, {}, {}, {})
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SeatingPreview() {
    Scan7Theme {
        val players = SampleStoredPlayers.take(4)
        val draft = SeatingDraft.of(players.map { it.id })
        SeatingScreen(players, draft.positions, RoundView.Table, 200, {}, { _, _ -> }, { _, _ -> }, {}, {})
    }
}
