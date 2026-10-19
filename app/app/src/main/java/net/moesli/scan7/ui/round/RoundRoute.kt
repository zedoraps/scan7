package net.moesli.scan7.ui.round

import kotlinx.coroutines.delay
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import net.moesli.scan7.ui.components.LocalTypeEntry
import net.moesli.scan7.ui.components.RoundView
import net.moesli.scan7.ui.components.Scan7BottomSheet
import net.moesli.scan7.ui.components.Confirmations
import net.moesli.scan7.ui.components.Scan7ConfirmDialog
import net.moesli.scan7.ui.nav.AfterScan
import net.moesli.scan7.ui.nav.EntryMode
import net.moesli.scan7.ui.nav.ScanStart
import net.moesli.scan7.ui.players.playerUis
import net.moesli.scan7.ui.sheet.AdjustActions
import net.moesli.scan7.ui.sheet.CloseRoundDialog
import net.moesli.scan7.ui.sheet.PlayerSheet
import net.moesli.scan7.ui.sheet.PlayerSheetUi
import net.moesli.scan7.ui.theme.Scan7Theme
import androidx.compose.ui.res.stringResource
import net.moesli.scan7.BuildConfig
import net.moesli.scan7.R
import net.moesli.scan7.multiplayer.ClientStatus
import net.moesli.scan7.share.LinkMode
import net.moesli.scan7.share.ShareText
import net.moesli.scan7.ui.share.ClaimSheet
import net.moesli.scan7.ui.share.RefusalDialog
import net.moesli.scan7.ui.share.ShareSheet
import net.moesli.scan7.ui.share.rememberRadioPermissionRequest
import net.moesli.scan7.ui.text.uiText

private const val TAG = "RoundRoute"

/** Where the Round screen can lead. */
interface RoundNavigator {
    /** Scan / manual entry for [playerId]; [round] `null` = the running round. */
    fun scan(playerId: Long, start: ScanStart, round: Int? = null)

    fun gameOver()

    fun scoreboard()

    fun home()

    /** Round menu → Players: add / remove players mid-game. */
    fun managePlayers() {}

    /** Multiplayer guest that can't reconnect: Join a game (scan the host's code again). */
    fun join() {}
}

/** Transient Round screen UI (saved across rotation / process death). */
@Stable
class RoundFlags(
    arranging: Boolean = false,
    sheetPlayer: Long? = null,
    confirmClose: Boolean = false,
    menu: Boolean = false,
    confirmEnd: Boolean = false,
    confirmTarget: Int? = null,
    shareSheet: Boolean = false,
    claimSheet: Boolean = false,
    confirmLeave: Boolean = false,
) {
    var arranging by mutableStateOf(arranging)
    var sheetPlayer by mutableStateOf(sheetPlayer)
    var confirmClose by mutableStateOf(confirmClose)
    var menu by mutableStateOf(menu)

    /** "End game" confirmation (round menu). */
    var confirmEnd by mutableStateOf(confirmEnd)

    /** "Lower the target to N?" confirmation (round menu; the new target), `null` = not asking. */
    var confirmTarget by mutableStateOf(confirmTarget)

    /** Multiplayer: share sheet (host), "Who are you · Who do you scan" (guest), "Leave game?" (guest). */
    var shareSheet by mutableStateOf(shareSheet)
    var claimSheet by mutableStateOf(claimSheet)
    var confirmLeave by mutableStateOf(confirmLeave)

    companion object {
        val Saver = listSaver<RoundFlags, Any>(
            save = {
                listOf(
                    it.arranging, it.sheetPlayer ?: -1L, it.confirmClose, it.menu, it.confirmEnd, it.confirmTarget ?: -1,
                    it.shareSheet, it.claimSheet, it.confirmLeave,
                )
            },
            restore = {
                RoundFlags(
                    arranging = it[0] as Boolean,
                    sheetPlayer = (it[1] as Long).takeIf { id -> id >= 0 },
                    confirmClose = it[2] as Boolean,
                    menu = it[3] as Boolean,
                    confirmEnd = it[4] as Boolean,
                    confirmTarget = (it[5] as Int).takeIf { t -> t > 0 },
                    shareSheet = it[6] as Boolean,
                    claimSheet = it[7] as Boolean,
                    confirmLeave = it[8] as Boolean,
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
    /** Shared toast: "<name> marked bust · Undo" (in-card Bust, the sheet's Bust). */
    val toast: BustToastState,
) : RoundActions {
    /** Latest snapshot (set by the route on every composition; read only by the event handlers). */
    var snapshot: GameSnapshot? = null

    /** Entry mode setting (set by the route like [snapshot]): Type turns every scan into manual entry. */
    var entry: EntryMode = EntryMode.SCAN

    /** `false` on a multiplayer guest (set by the route like [snapshot]): only the host arranges seats. */
    var canArrange: Boolean = true

    /**
     * Repository writes run via [outlivingUi]: leaving the screen (e.g. Scan next right after an
     * in-card Bust) cancels only the waiting, never the write.
     */
    private fun run(what: String, block: suspend GameRepository.() -> Unit) {
        scope.launch {
            runCatching { outlivingUi { repository.block() } }.onFailure { Log.w(TAG, "$what failed", it) }
        }
    }

    /** A scan of the running round starts (Type entry mode: the manual entry instead of the camera). */
    private fun scanRunning(playerId: Long, start: ScanStart) = navigator.scan(playerId, entry.startFor(start))

    override fun onScan(playerId: String) = scanRunning(playerId.toLong(), ScanStart.CAMERA)

    override fun onBust(playerId: String) = run("bust") { bust(gameId, playerId.toLong()) }

    override fun onUndoBust(playerId: String) = run("undo bust") { undoBust(gameId, playerId.toLong()) }

    override fun onCardClick(playerId: String) {
        if (snapshot == null) return
        // Open card → scan / manual / bust; done card → its result.
        flags.sheetPlayer = playerId.toLong()
    }

    override fun onHome() = navigator.home()

    override fun onToggleView(view: RoundView) = run("view") { setViewMode(gameId, view.toViewMode()) }

    override fun onEnterArrange() {
        if (!canArrange) return
        flags.arranging = true
    }

    override fun onDone() {
        flags.arranging = false
    }

    override fun onRejoin() = navigator.join()

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
     * Closes the running round and starts the next one (one transaction), or Game over when
     * someone reached the target. No toast: the new round's screen says enough.
     */
    private fun finishRound() {
        if (finishing) return
        finishing = true
        scope.launch {
            runCatching { outlivingUi { repository.closeRoundAndStartNext(gameId) } }
                .onSuccess { s -> if (s.isOver) navigator.gameOver() else roundStarted() }
                .onFailure { Log.w(TAG, "close round failed", it) }
            finishing = false
        }
    }

    /** A new round runs: no sheet, and a bust toast of the closed round can't undo any more. */
    private fun roundStarted() {
        flags.sheetPlayer = null
        toast.dismiss()
    }

    /**
     * The stored game's running round is already closed (an older version's end-of-round step, or
     * a scoreboard edit took a finished game below the target again): Game over if it is over,
     * else start the next round.
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
                .onSuccess { roundStarted() }
                .onFailure { Log.w(TAG, "next round failed", it) }
            finishing = false
        }
    }

    /**
     * Round menu: the game's target points. Lowering it to or below the leader's total asks first
     * ("The game ends after this round", [crossesTarget]); otherwise it is stored at once.
     */
    fun onTarget(target: Int) {
        val state = snapshot?.state ?: return
        if (target == state.target) return
        if (Confirmations.lowerTarget(target, crossesTarget(state, target)) != null) {
            flags.confirmTarget = target
        } else {
            setTarget(target)
        }
    }

    /** Stores [target] as the game's target (confirmed, or no confirmation needed). */
    fun setTarget(target: Int) {
        flags.confirmTarget = null
        run("set target") { setTarget(gameId, target) }
    }

    /** Round menu: what a save does next (same setting as the review's split OK ▾). */
    fun onAfterScan(choice: AfterScan) = run("after scan") { putSetting(AfterScan.SETTING_KEY, choice.name) }

    /** Round menu: Scan | Type (app setting). */
    fun onEntryMode(mode: EntryMode) = run("entry mode") { putSetting(EntryMode.SETTING_KEY, mode.name) }

    /** Round menu: the "Scan next" direction (app setting). */
    fun onScanDirection(direction: ScanDirection) {
        if (snapshot?.state?.scanDirection == direction) return
        run("scan direction") { setScanDirection(direction) }
    }

    override fun onMenu() {
        flags.menu = true
    }

    /** Multiplayer host: header share icon / menu row → the share sheet (the route starts sharing). */
    override fun onShare() {
        flags.menu = false
        flags.arranging = false
        flags.shareSheet = true
    }

    /** Menu "Players" → the players picker (leaves arrange mode). */
    fun onManagePlayers() {
        flags.arranging = false
        flags.sheetPlayer = null
        navigator.managePlayers()
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
    val afterScan by remember { repository.setting(AfterScan.SETTING_KEY) }.collectAsStateWithLifecycle(initialValue = null)
    val entrySetting by remember { repository.setting(EntryMode.SETTING_KEY) }.collectAsStateWithLifecycle(initialValue = null)
    val entry = EntryMode.fromSetting(entrySetting)
    val flags = rememberSaveable(saver = RoundFlags.Saver) { RoundFlags() }
    val scope = rememberCoroutineScope()
    val controller = remember(gameId, navigator) { RoundController(repository, gameId, scope, navigator, flags, toast) }
    val haptics = LocalHapticFeedback.current
    controller.snapshot = snapshot
    controller.entry = entry
    // Multiplayer (docs/MULTI_DEVICE.md §2): this phone hosts this game, or it is a joined copy.
    val context = LocalContext.current
    val shareRuntime = remember { AppGraph.share(context) }
    val shareState by shareRuntime.state.collectAsStateWithLifecycle()
    val host = shareState.host?.takeIf { it.gameId == gameId }
    val guest = shareState.guest?.takeIf { it.localGameId == gameId }
    val permissions = rememberRadioPermissionRequest()
    // Sharing is possible with a radio (play flavor) or, in debug builds, over the in-process demo network.
    val canShare = shareRuntime.radioAvailable || BuildConfig.DEBUG

    val snap = snapshot
    if (snap == null) {
        Box(modifier.fillMaxSize().background(Scan7Theme.colors.ground))
        return
    }
    // A joined copy without its live session (e.g. after an app restart) is read-only like a guest:
    // no host actions; with a radio it rejoins the saved session by itself.
    val readOnly = guest != null || snap.isJoined
    controller.canArrange = !readOnly
    LaunchedEffect(snap.isJoined, guest == null) {
        if (snap.isJoined && guest == null && shareRuntime.radioAvailable) {
            repository.shareSession(gameId)?.takeIf { it.role == "GUEST" }?.let { row ->
                permissions.run { shareRuntime.join(net.moesli.scan7.multiplayer.nearby.JoinLink(row.sessionId, row.token), LinkMode.RADIO) }
            }
        }
    }
    // Reconnecting a joined game failed for a while (host gone, radio off, permission declined):
    // say that this is only this phone's copy and offer Join again.
    var offline by remember(gameId) { mutableStateOf(false) }
    LaunchedEffect(snap.isJoined, guest?.status) {
        offline = false
        if (snap.isJoined && guest?.status != ClientStatus.JOINED) {
            delay(REJOIN_WARNING_MS)
            offline = true
        }
    }
    val roundClosed = snap.state.currentRound.isClosed
    // Only the host settles a closed round (a guest's copy follows the host's next round).
    LaunchedEffect(roundClosed) { if (roundClosed && !snap.isJoined) controller.settleClosedRound() }
    // A guest follows the host to Game over.
    val joinedOver = snap.isJoined && snap.state.isOver
    LaunchedEffect(joinedOver) { if (joinedOver) navigator.gameOver() }
    // A guest that hasn't said who it is yet: the claim sheet opens once it is connected.
    val needsClaim = guest != null && guest.status == ClientStatus.JOINED && guest.myPlayer == null && guest.me?.helperName == null
    LaunchedEffect(needsClaim) { if (needsClaim) flags.claimSheet = true }
    val typeEntry = entry == EntryMode.TYPE
    // Type mode: the in-card / bottom scan buttons show the keypad / "Enter next".
    CompositionLocalProvider(LocalTypeEntry provides typeEntry) {
        val base = snap.toRoundUiState(flags.arranging, typeEntry = typeEntry).copy(canArrange = !readOnly)
        val shown = when {
            guest != null -> base.joined(guest)
            snap.isJoined -> base.copy(share = RoundShareUi(guest = true, waiting = uiText(R.string.round_mine_done), banner = uiText(R.string.banner_lost)))
            host != null || (canShare && !snap.isJoined) -> base.hosted(host, snap.players)
            else -> base
        }
        RoundScreen(
            state = if (offline) shown.copy(share = shown.share?.copy(banner = uiText(R.string.banner_offline), rejoin = true)) else shown,
            actions = controller,
            // The phone lies on the table during a game: keep the screen on.
            modifier = modifier.keepScreenOn(),
            toast = toast,
        )
    }

    val uis = snap.state.playerIds.zip(snap.state.playerUis(snap.players)).toMap()
    flags.sheetPlayer?.let { id ->
        val round = snap.state.currentRound.number
        val player = uis[id]
        if (player != null) {
            val closed = snap.state.round(round).isClosed
            val done = snap.state.result(id, round).isDone
            // A guest only changes its own entries; others show "Scored by …" read-only.
            val scoredBy = if (guest != null && done && id !in guest.myEntries) {
                ShareText.phone(guest.scannedBy[id]?.let { guest.labels[it.deviceId] } ?: guest.host)
                    .let { uiText(R.string.sheet_scored_by, it) }
            } else {
                null
            }
            PlayerSheet(
                state = PlayerSheetUi(player, round, snap.state.result(id, round).cards, snap.state.adjustmentsOf(id), scoredBy),
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
                // Guests can't change totals.
                adjust = remember(id) { controller.adjustActions(id) }.takeIf { !readOnly },
                typeEntry = entry == EntryMode.TYPE,
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
    flags.confirmTarget?.let { t ->
        Confirmations.lowerTarget(t, reachesIt = true)?.let { spec ->
            Scan7ConfirmDialog(spec, onConfirm = { controller.setTarget(t) }, onCancel = { flags.confirmTarget = null })
        }
    }
    if (flags.confirmEnd) {
        Scan7ConfirmDialog(Confirmations.endGame(), onConfirm = controller::endGame, onCancel = { flags.confirmEnd = false })
    }
    if (flags.menu) {
        val close = { flags.menu = false }
        // Items that leave the menu slide it down first (hideThen runs the item, then `close`).
        Scan7BottomSheet(onDismiss = close) { sheet ->
            RoundMenuContent(
                settings = RoundMenuSettings(
                    // A finished game (end-of-round view) keeps its target: changing it would reopen / end it.
                    // Guests don't change game settings.
                    target = snap.state.target.takeUnless { snap.state.isOver || readOnly },
                    direction = snap.state.scanDirection,
                    afterScan = AfterScan.fromSetting(afterScan),
                    entry = entry,
                ),
                onScoreboard = { sheet.hideThen(navigator::scoreboard) },
                onArrange = if (readOnly) null else ({ sheet.hideThen(controller::onEnterArrange) }),
                onHome = { sheet.hideThen(navigator::home) },
                onEndGame = if (snap.state.isOver || readOnly) null else ({ sheet.hideThen(controller::onEndGame) }),
                onTarget = controller::onTarget,
                onDirection = controller::onScanDirection,
                onAfterScan = controller::onAfterScan,
                onEntry = controller::onEntryMode,
                onPlayers = if (snap.state.canChangePlayers && !readOnly) ({ sheet.hideThen(controller::onManagePlayers) }) else null,
                onShare = if (guest == null && !snap.isJoined && canShare) ({ sheet.hideThen(controller::onShare) }) else null,
                onWhoScan = if (guest != null) ({ sheet.hideThen({ flags.claimSheet = true }) }) else null,
                onLeave = if (guest != null) ({ sheet.hideThen({ flags.confirmLeave = true }) }) else null,
            )
        }
    }

    // ---- Multiplayer ------------------------------------------------------------------------
    if (flags.shareSheet) {
        // Opening the sheet starts sharing (radio after the permission, else the debug demo network).
        LaunchedEffect(Unit) {
            if (host == null) {
                if (shareRuntime.radioAvailable) permissions.run(onCancel = { flags.shareSheet = false }) { shareRuntime.host(gameId, LinkMode.RADIO) } else shareRuntime.host(gameId, LinkMode.IN_PROCESS)
            }
        }
        ShareSheet(
            host = host,
            players = snap.players,
            radioAvailable = shareRuntime.radioAvailable || BuildConfig.DEBUG,
            onRemove = { shareRuntime.removeGuest(it.deviceId) },
            onStop = { shareRuntime.stopHosting() },
            onDismiss = { flags.shareSheet = false },
            seated = snap.seatedPlayers,
            onClaimSelf = { id -> shareRuntime.hostClaim(id, listOf(id)) },
        )
    }
    if (guest != null && flags.claimSheet) {
        ClaimSheet(
            guest = guest,
            onDone = { me, scans, helper, force -> shareRuntime.claim(me, scans, helper, force) },
            onDismiss = { flags.claimSheet = false },
        )
    }
    guest?.refusal?.let { refusal ->
        RefusalDialog(refusal, roundClosed = false, onDismiss = { shareRuntime.clearRefusal() })
    }
    if (guest != null && flags.confirmLeave) {
        Scan7ConfirmDialog(
            title = stringResource(R.string.leave_title),
            confirmLabel = stringResource(R.string.leave_action),
            body = stringResource(R.string.leave_body),
            destructive = true,
            onConfirm = {
                flags.confirmLeave = false
                shareRuntime.leave()
                navigator.home()
            },
            onCancel = { flags.confirmLeave = false },
        )
    }

}


/** How long a joined game may stay unconnected before the "only a copy" warning. */
private const val REJOIN_WARNING_MS = 20_000L
