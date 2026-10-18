package net.moesli.scan7.ui.round

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.moesli.scan7.data.AppGraph
import net.moesli.scan7.data.GameRepository
import net.moesli.scan7.data.GameSnapshot
import net.moesli.scan7.game.TablePosition
import net.moesli.scan7.ui.components.RoundView
import net.moesli.scan7.ui.components.Scan7BottomSheet
import net.moesli.scan7.ui.components.SecondaryButton
import net.moesli.scan7.ui.components.SectionLabel
import net.moesli.scan7.ui.nav.ScanStart
import net.moesli.scan7.ui.players.playerUis
import net.moesli.scan7.ui.sheet.CloseRoundDialog
import net.moesli.scan7.ui.sheet.EndGameDialog
import net.moesli.scan7.ui.sheet.PlayerSheet
import net.moesli.scan7.ui.sheet.PlayerSheetUi
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

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
            save = { listOf(it.arranging, it.sheetPlayer ?: -1L, it.confirmClose, it.menu, it.confirmEnd) },
            restore = {
                RoundFlags(it[0] as Boolean, (it[1] as Long).takeIf { id -> id >= 0 }, it[2] as Boolean, it[3] as Boolean, it[4] as Boolean)
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
    /** Shared "<name> marked bust · Undo" toast (in-card Bust and the sheet's Bust). */
    val toast: BustToastState,
) : RoundActions {
    /** Latest snapshot (set by the route on every composition; read only by the event handlers). */
    var snapshot: GameSnapshot? = null

    private fun run(what: String, block: suspend GameRepository.() -> Unit) {
        scope.launch {
            runCatching { repository.block() }.onFailure { Log.w(TAG, "$what failed", it) }
        }
    }

    private val roundOver: Boolean get() = snapshot?.state?.currentRound?.isClosed == true

    override fun onScan(playerId: String) = navigator.scan(playerId.toLong(), ScanStart.CAMERA)

    override fun onBust(playerId: String) = run("bust") { bust(gameId, playerId.toLong()) }

    override fun onUndoBust(playerId: String) = run("undo bust") { undoBust(gameId, playerId.toLong()) }

    override fun onCardClick(playerId: String) {
        if (snapshot == null) return
        // Open card → scan / manual / bust; done card (also on the end-of-round view) → its result.
        flags.sheetPlayer = playerId.toLong()
    }

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
        navigator.scan(next.id.toLong(), ScanStart.NEXT)
    }

    override fun onCloseRound() {
        val state = snapshot?.state ?: return
        if (state.currentRound.isClosed) return
        if (state.openPlayers().isNotEmpty()) flags.confirmClose = true else closeRound()
    }

    fun closeRound() {
        flags.confirmClose = false
        flags.arranging = false
        run("close round") { closeRound(gameId) }
    }

    override fun onNextRound() {
        val state = snapshot?.state ?: return
        if (state.isOver) navigator.gameOver() else run("next round") { nextRound(gameId) }
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
            runCatching { repository.endGame(gameId) }
                .onSuccess { navigator.gameOver() }
                .onFailure { Log.w(TAG, "end game failed", it) }
        }
    }

    // Player sheet.

    fun sheetAction(playerId: Long, action: SheetAction) {
        flags.sheetPlayer = null
        // End of round: Edit / Rescan change the closed round's result (totals, ranks recomputed).
        val closedRound = snapshot?.state?.currentRound?.takeIf { it.isClosed }?.number
        when (action) {
            SheetAction.Scan, SheetAction.Rescan -> navigator.scan(playerId, ScanStart.CAMERA, closedRound)
            SheetAction.Manual -> navigator.scan(playerId, ScanStart.MANUAL)
            SheetAction.Edit -> navigator.scan(playerId, ScanStart.EDIT, closedRound)
            SheetAction.Bust -> {
                run("bust") { bust(gameId, playerId) }
                snapshot?.players?.get(playerId)?.let { toast.show(playerId.toString(), it.name) }
            }
            SheetAction.Reset -> run("reset") { resetToOpen(gameId, playerId) }
        }
    }
}

enum class SheetAction { Scan, Manual, Bust, Edit, Rescan, Reset }

/**
 * The Round screen on a stored game: [RoundScreen] on [GameSnapshot.toRoundUiState], the player
 * sheet, the close-round dialog and the header menu (scoreboard, home).
 */
@Composable
fun RoundRoute(
    gameId: Long,
    navigator: RoundNavigator,
    modifier: Modifier = Modifier,
    repository: GameRepository = AppGraph.repository(LocalContext.current),
) {
    val snapshot by remember(gameId) { repository.observeGame(gameId) }.collectAsStateWithLifecycle(initialValue = null)
    val flags = rememberSaveable(saver = RoundFlags.Saver) { RoundFlags() }
    val scope = rememberCoroutineScope()
    val toast = rememberBustToastState()
    val controller = remember(gameId, navigator) { RoundController(repository, gameId, scope, navigator, flags, toast) }
    val haptics = LocalHapticFeedback.current
    controller.snapshot = snapshot

    val snap = snapshot
    if (snap == null) {
        Box(modifier.fillMaxSize().background(Scan7Theme.colors.ground))
        return
    }
    RoundScreen(state = snap.toRoundUiState(flags.arranging), actions = controller, modifier = modifier, toast = toast)

    val uis = snap.state.playerIds.zip(snap.state.playerUis(snap.players)).toMap()
    flags.sheetPlayer?.let { id ->
        val player = uis[id]
        if (player != null) {
            val closed = snap.state.currentRound.isClosed
            PlayerSheet(
                state = PlayerSheetUi(player, snap.state.currentRound.number, snap.state.result(id).cards),
                onScan = { controller.sheetAction(id, SheetAction.Scan) },
                onEnterManually = { controller.sheetAction(id, SheetAction.Manual) },
                onBust = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    controller.sheetAction(id, SheetAction.Bust)
                },
                onEdit = { controller.sheetAction(id, SheetAction.Edit) },
                onRescan = { controller.sheetAction(id, SheetAction.Rescan) },
                // A closed round has no open players: "Reset to open" would mean reopening the round.
                onResetToOpen = if (closed) null else ({ controller.sheetAction(id, SheetAction.Reset) }),
                onDismiss = { flags.sheetPlayer = null },
            )
        }
    }
    if (flags.confirmClose) {
        CloseRoundDialog(
            round = snap.state.currentRound.number,
            openPlayers = snap.state.openPlayers().mapNotNull { uis[it] },
            onCancel = { flags.confirmClose = false },
            onConfirm = controller::closeRound,
        )
    }
    if (flags.confirmEnd) {
        EndGameDialog(onCancel = { flags.confirmEnd = false }, onConfirm = controller::endGame)
    }
    if (flags.menu) {
        val close = { flags.menu = false }
        Scan7BottomSheet(onDismiss = close) {
            Column(verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
                SectionLabel("Round ${snap.state.currentRound.number} · to ${snap.state.target}")
                SecondaryButton(
                    "Scoreboard",
                    {
                        close()
                        navigator.scoreboard()
                    },
                    Modifier.fillMaxWidth(),
                    height = Scan7Spacing.minTouch,
                    textStyle = Scan7Type.text13,
                )
                if (!snap.state.isOver) {
                    SecondaryButton(
                        "End game",
                        controller::onEndGame,
                        Modifier.fillMaxWidth(),
                        height = Scan7Spacing.minTouch,
                        textStyle = Scan7Type.text13,
                        contentDescription = "End game now (the leader wins)",
                    )
                }
                SecondaryButton(
                    "Home",
                    {
                        close()
                        navigator.home()
                    },
                    Modifier.fillMaxWidth(),
                    height = Scan7Spacing.minTouch,
                    textStyle = Scan7Type.text13,
                    contentDescription = "Home (the game stays resumable)",
                )
            }
        }
    }
}
