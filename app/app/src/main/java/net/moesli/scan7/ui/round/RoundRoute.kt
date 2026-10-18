package net.moesli.scan7.ui.round

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.moesli.scan7.data.AppGraph
import net.moesli.scan7.data.GameRepository
import net.moesli.scan7.data.GameSnapshot
import net.moesli.scan7.data.outlivingUi
import net.moesli.scan7.game.ScanDirection
import net.moesli.scan7.game.TablePosition
import net.moesli.scan7.ui.components.RoundView
import net.moesli.scan7.ui.components.Scan7BottomSheet
import net.moesli.scan7.ui.components.Confirmations
import net.moesli.scan7.ui.components.Scan7ConfirmDialog
import net.moesli.scan7.ui.nav.ScanStart
import net.moesli.scan7.ui.players.playerUis
import net.moesli.scan7.ui.sheet.AdjustActions
import net.moesli.scan7.ui.sheet.CloseRoundDialog
import net.moesli.scan7.ui.sheet.PlayerSheet
import net.moesli.scan7.ui.sheet.PlayerSheetUi
import net.moesli.scan7.ui.theme.Scan7Theme

private const val TAG = "RoundRoute"

/** Where the Round screen can lead. */
interface RoundNavigator {
    /** Scan / manual entry for [playerId]; [round] `null` = the running round. */
    fun scan(playerId: Long, start: ScanStart, round: Int? = null)

    fun gameOver()

    fun scoreboard()

    fun home()
}

/** Transient Round screen UI (saved across rotation / process death). */
@Stable
class RoundFlags(
    arranging: Boolean = false,
    sheetPlayer: Long? = null,
    confirmClose: Boolean = false,
    menu: Boolean = false,
    confirmEnd: Boolean = false,
) {
    var arranging by mutableStateOf(arranging)
    var sheetPlayer by mutableStateOf(sheetPlayer)
    var confirmClose by mutableStateOf(confirmClose)
    var menu by mutableStateOf(menu)

    /** "End game" confirmation (round menu). */
    var confirmEnd by mutableStateOf(confirmEnd)

    companion object {
        val Saver = listSaver<RoundFlags, Any>(
            save = {
                listOf(it.arranging, it.sheetPlayer ?: -1L, it.confirmClose, it.menu, it.confirmEnd)
            },
            restore = {
                RoundFlags(
                    arranging = it[0] as Boolean,
                    sheetPlayer = (it[1] as Long).takeIf { id -> id >= 0 },
                    confirmClose = it[2] as Boolean,
                    menu = it[3] as Boolean,
                    confirmEnd = it[4] as Boolean,
                )
            },
        )
    }
}

/**
 * The Round screen's state holder: maps [RoundActions] to [GameRepository] calls and navigation.
 * The screen state itself is [GameSnapshot.toRoundUiState] of the latest snapshot.
 */
@Stable
class RoundController(
    private val repository: GameRepository,
    private val gameId: Long,
    private val scope: CoroutineScope,
    private val navigator: RoundNavigator,
    val flags: RoundFlags,
    /** Shared toast: "<name> marked bust · Undo" (in-card Bust, the sheet's Bust), "Round N closed". */
    val toast: BustToastState,
) : RoundActions {
    /** Latest snapshot (set by the route on every composition; read only by the event handlers). */
    var snapshot: GameSnapshot? = null

    /**
     * Repository writes run via [outlivingUi]: leaving the screen (e.g. Scan next right after an
     * in-card Bust) cancels only the waiting, never the write.
     */
    private fun run(what: String, block: suspend GameRepository.() -> Unit) {
        scope.launch {
            runCatching { outlivingUi { repository.block() } }.onFailure { Log.w(TAG, "$what failed", it) }
        }
    }

    /** A scan of the running round starts: a "Round N closed" toast goes away. */
    private fun scanRunning(playerId: Long, start: ScanStart) {
        if (toast.current is RoundClosedToast) toast.dismiss()
        navigator.scan(playerId, start)
    }

    override fun onScan(playerId: String) = scanRunning(playerId.toLong(), ScanStart.CAMERA)

    override fun onBust(playerId: String) = run("bust") { bust(gameId, playerId.toLong()) }

    override fun onUndoBust(playerId: String) = run("undo bust") { undoBust(gameId, playerId.toLong()) }

    override fun onCardClick(playerId: String) {
        if (snapshot == null) return
        // Open card → scan / manual / bust; done card → its result.
        flags.sheetPlayer = playerId.toLong()
    }

    override fun onScoreboard(round: Int) = navigator.scoreboard()

    override fun onHome() = navigator.home()

    override fun onToggleView(view: RoundView) = run("view") { setViewMode(gameId, view.toViewMode()) }

    override fun onEnterArrange() {
        flags.arranging = true
    }

    override fun onDone() {
        flags.arranging = false
    }

    override fun onMoveCard(playerId: String, position: TablePosition) =
        run("move card") { movePosition(gameId, playerId.toLong(), position.x, position.y) }

    override fun onMoveRow(from: Int, to: Int) = run("swap rows") { swapSeats(gameId, from, to) }

    override fun onScanNext() {
        val next = snapshot?.toRoundUiState()?.nextOpen ?: return
        scanRunning(next.id.toLong(), ScanStart.NEXT)
    }

    override fun onCloseRound() {
        val state = snapshot?.state ?: return
        if (state.currentRound.isClosed) return
        if (state.openPlayers().isNotEmpty()) flags.confirmClose = true else closeRound()
    }

    /** Confirmed "Close round" (or nobody open): close and start the next round in one step. */
    fun closeRound() {
        flags.confirmClose = false
        flags.arranging = false
        finishRound()
    }

    override fun onNextRound() {
        val state = snapshot?.state ?: return
        if (state.currentRound.isClosed || !state.isRoundComplete) return
        flags.arranging = false
        finishRound()
    }

    private var finishing = false

    /**
     * Closes the running round and starts the next one (one transaction), then shows the
     * "Round N closed · Scoreboard" toast, or Game over when someone reached the target.
     */
    private fun finishRound() {
        if (finishing) return
        finishing = true
        scope.launch {
            runCatching { outlivingUi { repository.closeRoundAndStartNext(gameId) } }
                .onSuccess { s -> if (s.isOver) navigator.gameOver() else showResults(s.currentRound.number - 1) }
                .onFailure { Log.w(TAG, "close round failed", it) }
            finishing = false
        }
    }

    private fun showResults(round: Int) {
        flags.sheetPlayer = null
        toast.showRoundClosed(round)
    }

    /**
     * The stored game's running round is already closed (an older version's end-of-round step, or
     * a scoreboard edit took a finished game below the target again): Game over if it is over,
     * else start the next round (with the "Round N closed" toast).
     */
    fun settleClosedRound() {
        val state = snapshot?.state ?: return
        if (!state.currentRound.isClosed || finishing) return
        if (state.isOver) {
            navigator.gameOver()
            return
        }
        finishing = true
        scope.launch {
            runCatching { outlivingUi { repository.nextRound(gameId) } }
                .onSuccess { s -> showResults(s.currentRound.number - 1) }
                .onFailure { Log.w(TAG, "next round failed", it) }
            finishing = false
        }
    }

    /** Round menu: the "Scan next" direction (app setting). */
    fun onScanDirection(direction: ScanDirection) {
        if (snapshot?.state?.scanDirection == direction) return
        run("scan direction") { setScanDirection(direction) }
    }

    override fun onMenu() {
        flags.menu = true
    }

    /** Menu "End game" → confirmation. */
    fun onEndGame() {
        flags.menu = false
        flags.arranging = false
        flags.confirmEnd = true
    }

    /** Confirmed: close the round as it is, finish the game, show the game over screen. */
    fun endGame() {
        flags.confirmEnd = false
        scope.launch {
            runCatching { outlivingUi { repository.endGame(gameId) } }
                .onSuccess { navigator.gameOver() }
                .onFailure { Log.w(TAG, "end game failed", it) }
        }
    }

    // Player sheet.

    fun sheetAction(playerId: Long, action: SheetAction) {
        flags.sheetPlayer = null
        val scan = { start: ScanStart -> scanRunning(playerId, start) }
        when (action) {
            SheetAction.Scan, SheetAction.Rescan -> scan(ScanStart.CAMERA)
            SheetAction.Manual -> scan(ScanStart.MANUAL)
            SheetAction.Edit -> scan(ScanStart.EDIT)
            SheetAction.Bust -> {
                run("bust") { bust(gameId, playerId) }
                snapshot?.players?.get(playerId)?.let { toast.show(playerId.toString(), it.name) }
            }
            SheetAction.Reset -> run("reset") { resetToOpen(gameId, playerId) }
        }
    }

    // ---- Total adjustments (player sheet "Adjust total"; the sheet stays open) -------------

    fun adjustActions(playerId: Long) = AdjustActions(
        onAdjust = { delta, note -> run("adjust total") { adjustTotal(gameId, playerId, delta, note) } },
        onRemove = { a -> run("remove adjustment") { removeAdjustment(gameId, a.id) } },
        onRestore = { a -> run("restore adjustment") { restoreAdjustment(gameId, a) } },
    )
}

enum class SheetAction { Scan, Manual, Bust, Edit, Rescan, Reset }

/**
 * The Round screen on a stored game: [RoundScreen] on [GameSnapshot.toRoundUiState], the player
 * sheet, the close-round dialog and the header menu (scoreboard, home).
 *
 * @param toast the bust undo toast; the nav host shares it with the scan camera (its Bust tap).
 */
@Composable
fun RoundRoute(
    gameId: Long,
    navigator: RoundNavigator,
    modifier: Modifier = Modifier,
    repository: GameRepository = AppGraph.repository(LocalContext.current),
    toast: BustToastState = rememberBustToastState(),
) {
    val snapshot by remember(gameId) { repository.observeGame(gameId) }.collectAsStateWithLifecycle(initialValue = null)
    val flags = rememberSaveable(saver = RoundFlags.Saver) { RoundFlags() }
    val scope = rememberCoroutineScope()
    val controller = remember(gameId, navigator) { RoundController(repository, gameId, scope, navigator, flags, toast) }
    val haptics = LocalHapticFeedback.current
    controller.snapshot = snapshot

    val snap = snapshot
    if (snap == null) {
        Box(modifier.fillMaxSize().background(Scan7Theme.colors.ground))
        return
    }
    val roundClosed = snap.state.currentRound.isClosed
    LaunchedEffect(roundClosed) { if (roundClosed) controller.settleClosedRound() }
    RoundScreen(
        state = snap.toRoundUiState(flags.arranging),
        actions = controller,
        // The phone lies on the table during a game: keep the screen on.
        modifier = modifier.keepScreenOn(),
        toast = toast,
    )

    val uis = snap.state.playerIds.zip(snap.state.playerUis(snap.players)).toMap()
    flags.sheetPlayer?.let { id ->
        val round = snap.state.currentRound.number
        val player = uis[id]
        if (player != null) {
            val closed = snap.state.round(round).isClosed
            PlayerSheet(
                state = PlayerSheetUi(player, round, snap.state.result(id, round).cards, snap.state.adjustmentsOf(id)),
                onScan = { controller.sheetAction(id, SheetAction.Scan) },
                onEnterManually = { controller.sheetAction(id, SheetAction.Manual) },
                onBust = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    controller.sheetAction(id, SheetAction.Bust)
                },
                onEdit = { controller.sheetAction(id, SheetAction.Edit) },
                onRescan = { controller.sheetAction(id, SheetAction.Rescan) },
                // A closed round has no open players: "Reset to open" would mean reopening the round.
                onResetToOpen = if (closed) null else ({ controller.sheetAction(id, SheetAction.Reset) }),
                onDismiss = { flags.sheetPlayer = null },
                adjust = remember(id) { controller.adjustActions(id) },
            )
        }
    }
    if (flags.confirmClose) {
        CloseRoundDialog(
            round = snap.state.currentRound.number,
            openPlayers = snap.state.openPlayers().mapNotNull { uis[it] },
            playerCount = snap.state.playerIds.size,
            onCancel = { flags.confirmClose = false },
            onConfirm = controller::closeRound,
        )
    }
    if (flags.confirmEnd) {
        Scan7ConfirmDialog(Confirmations.endGame(), onConfirm = controller::endGame, onCancel = { flags.confirmEnd = false })
    }
    if (flags.menu) {
        val close = { flags.menu = false }
        // Items that leave the menu slide it down first (hideThen runs the item, then `close`).
        Scan7BottomSheet(onDismiss = close) { sheet ->
            RoundMenuContent(
                round = snap.state.currentRound.number,
                target = snap.state.target,
                direction = snap.state.scanDirection,
                onArrange = { sheet.hideThen(controller::onEnterArrange) },
                onScoreboard = { sheet.hideThen(navigator::scoreboard) },
                onDirection = controller::onScanDirection,
                onEndGame = if (snap.state.isOver) null else ({ sheet.hideThen(controller::onEndGame) }),
                onHome = { sheet.hideThen(navigator::home) },
            )
        }
    }
}
