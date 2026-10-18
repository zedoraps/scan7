package net.moesli.scan7.ui.newgame

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.moesli.scan7.R
import net.moesli.scan7.data.AppGraph
import net.moesli.scan7.data.GameRepository
import net.moesli.scan7.data.GameSnapshot
import net.moesli.scan7.data.outlivingUi
import net.moesli.scan7.game.GameState
import net.moesli.scan7.game.MAX_PLAYERS
import net.moesli.scan7.game.Player
import net.moesli.scan7.game.ResultStatus
import net.moesli.scan7.ui.components.Confirmations
import net.moesli.scan7.ui.components.ConfirmSpec
import net.moesli.scan7.ui.components.PlayerStatus
import net.moesli.scan7.ui.components.Scan7ConfirmDialog
import net.moesli.scan7.ui.players.EditorTarget
import net.moesli.scan7.ui.players.PlayerEditorHost
import net.moesli.scan7.ui.round.toRoundView
import net.moesli.scan7.ui.round.toViewMode
import net.moesli.scan7.ui.theme.Scan7Theme

// ---- Logic (plain Kotlin, JVM-tested) ------------------------------------------------------------

/**
 * Players to pick from in the round menu's "Players" step: the stored players plus anyone seated
 * in [state] who was deleted from the list meanwhile (archived), so unticking stays explicit.
 */
fun gamePickList(stored: List<Player>, state: GameState, gamePlayers: Map<Long, Player>): List<Player> {
    val ids = stored.map { it.id }.toSet()
    return stored + state.playerIds.filter { it !in ids }.mapNotNull { gamePlayers[it] }
}

/**
 * The lineup picked in step 1 in seating order: the seated players who stay (their order), then
 * the new ones in tick order (they join after the last seat, see [GameState.seatsFor]).
 */
fun pickedLineup(state: GameState, selected: List<Long>): List<Long> =
    state.playerIds.filter { it in selected } + selected.filter { it !in state.playerIds }

/**
 * The confirmation for unticking [playerId] (seated in [state]): they leave the game; `null` for a
 * player who isn't seated (only ticked in this step: unticks at once).
 */
fun removeConfirm(state: GameState, playerId: Long, name: String): ConfirmSpec? {
    if (playerId !in state.playerIds) return null
    val result = state.result(playerId)
    val status = when (result.status) {
        ResultStatus.OPEN -> PlayerStatus.Open
        ResultStatus.SCORED -> PlayerStatus.Scored(result.points)
        ResultStatus.BUST -> PlayerStatus.Bust
    }
    val playedBefore = state.rounds.any { it.isClosed && playerId in it.results }
    return Confirmations.removePlayer(name, status, playedBefore)
}

// ---- Wired flow ------------------------------------------------------------------------------------

/**
 * Round menu → **Players**: New game's step 1 picker (stored players with checkboxes, "+ New
 * player"), preselected with the seated players. Unticking a seated player asks first
 * ([removeConfirm], warning style). **Done** with a changed lineup → the Seats step (round table /
 * list arrange, new players placed after the last seat) → **Done** → `repository.changePlayers`
 * in one transaction → [onDone] (back to the round). Unchanged lineup: Done goes straight back.
 * Back from Seats returns to the picker; back from the picker leaves without changes.
 */
@Composable
fun GamePlayersRoute(
    gameId: Long,
    onDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    repository: GameRepository = AppGraph.repository(LocalContext.current),
) {
    val stored by repository.players.collectAsStateWithLifecycle(initialValue = null)
    val game by remember(gameId) { repository.observeGame(gameId) }.collectAsStateWithLifecycle(initialValue = null)
    var step by rememberSaveable { mutableIntStateOf(1) }
    // `null` until the game is loaded: then the seated players, in seating order.
    var selected by rememberSaveable(stateSaver = NullableLongListSaver) { mutableStateOf<List<Long>?>(null) }
    var draft by rememberSaveable(stateSaver = GameDraftSaver) { mutableStateOf<SeatingDraft?>(null) }
    var editorKey by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmRemove by rememberSaveable { mutableStateOf<Long?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val snap: GameSnapshot? = game
    val list = stored
    LaunchedEffect(snap != null) {
        if (snap != null && selected == null) selected = snap.state.playerIds
    }
    val picked = selected
    if (snap == null || list == null || picked == null) {
        Box(modifier.fillMaxSize().background(Scan7Theme.colors.ground))
        return
    }
    val state = snap.state
    if (!state.canChangePlayers) {
        // The round closed / the game ended meanwhile: nothing to change any more.
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val players = gamePickList(list, state, snap.players)
    val byId = players.associateBy { it.id }
    val liveSelected = picked.filter { it in byId }
    val saveError = stringResource(R.string.manage_players_error)
    val joinsText = stringResource(R.string.manage_players_joins, state.currentRound.number)
    val leavesText = stringResource(R.string.manage_players_leaves)

    fun save(seats: SeatingDraft) {
        saving = true
        error = null
        scope.launch {
            // Outlives this screen: a started transaction isn't rolled back by Back.
            runCatching { outlivingUi { repository.changePlayers(gameId, seats.seats) } }
                .onSuccess { onDone() }
                .onFailure { error = saveError }
            saving = false
        }
    }

    BackHandler(enabled = step == 2) { step = 1 }
    when (step) {
        1 -> PickPlayersScreen(
            players = players,
            selected = liveSelected,
            target = state.target,
            onToggle = { id ->
                if (id in liveSelected && id in state.playerIds) confirmRemove = id else selected = toggleSelection(liveSelected, id)
            },
            onNewPlayer = { editorKey = EditorTarget.New.key },
            onTargetChange = null,
            onNext = {
                val lineup = pickedLineup(state, liveSelected)
                if (lineup.toSet() == state.playerIds.toSet()) {
                    onBack()
                } else {
                    val current = draft
                    draft = if (current != null && current.ids.toSet() == lineup.toSet()) current else SeatingDraft(state.seatsFor(lineup))
                    error = null
                    step = 2
                }
            },
            onBack = onBack,
            error = error,
            starting = saving,
            title = stringResource(R.string.players_title),
            subtitle = stringResource(R.string.manage_players_subtitle, state.currentRound.number, liveSelected.size, MAX_PLAYERS),
            nextLabel = stringResource(R.string.action_done),
            supporting = { p ->
                when {
                    p.id in liveSelected && p.id !in state.playerIds -> joinsText
                    p.id !in liveSelected && p.id in state.playerIds -> leavesText
                    else -> null
                }
            },
            modifier = modifier,
        )
        else -> {
            // Players deleted meanwhile drop out of the seats.
            val seats = draft?.retain(byId.keys)
            if (seats == null) {
                LaunchedEffect(Unit) { step = 1 }
            } else {
                SeatingScreen(
                    players = seats.ids.map { byId.getValue(it) },
                    positions = seats.positions,
                    view = snap.viewMode.toRoundView(),
                    target = state.target,
                    onToggleView = { v -> scope.launch { repository.setViewMode(gameId, v.toViewMode()) } },
                    onMoveCard = { id, position -> draft = seats.moveCard(id, position) },
                    onMoveRow = { from, to -> draft = seats.swapRows(from, to) },
                    onStart = { if (!saving) save(seats) },
                    onBack = { step = 1 },
                    error = error,
                    starting = saving,
                    startLabel = stringResource(R.string.action_done),
                    totals = state.totals,
                    modifier = modifier,
                )
            }
        }
    }

    confirmRemove?.let { id ->
        val spec = byId[id]?.let { removeConfirm(state, id, it.name) }
        if (spec == null) {
            confirmRemove = null
        } else {
            Scan7ConfirmDialog(
                spec,
                onConfirm = {
                    confirmRemove = null
                    selected = liveSelected - id
                },
                onCancel = { confirmRemove = null },
            )
        }
    }

    EditorTarget.fromKey(editorKey, list)?.let { t ->
        val selectedPlayers = liveSelected.mapNotNull { byId[it] }
        PlayerEditorHost(
            target = t,
            // A new player gets a color none of the picked players uses; their colors get a dot.
            players = selectedPlayers,
            repository = repository,
            dotPlayers = selectedPlayers,
            onClose = { editorKey = null },
            onSaved = { id -> if (t == EditorTarget.New && id !in liveSelected) selected = toggleSelection(liveSelected, id) },
        )
    }
}

private val NullableLongListSaver = Saver<List<Long>?, LongArray>(
    save = { it?.toLongArray() ?: LongArray(0) },
    // An empty list never is a valid lineup (≥ 2 players): read it as "not loaded yet".
    restore = { a -> a.toList().takeIf { it.isNotEmpty() } },
)

private val GameDraftSaver = Saver<SeatingDraft?, LongArray>(
    save = { d -> d?.let(SeatingDraft::save) },
    restore = { SeatingDraft.restore(it) },
)
