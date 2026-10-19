package net.moesli.scan7.ui.scan

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.moesli.scan7.data.AppGraph
import net.moesli.scan7.data.GameRepository
import net.moesli.scan7.data.outlivingUi
import net.moesli.scan7.game.GameState
import androidx.compose.runtime.key
import net.moesli.scan7.skyjo.toConfirmedCards
import net.moesli.scan7.skyjo.skyjoGridOf
import net.moesli.scan7.skyjo.emptySkyjoGrid
import net.moesli.scan7.game.ResultSource
import net.moesli.scan7.game.GameKind
import net.moesli.scan7.game.flip7Cards
import net.moesli.scan7.ui.components.PlayerStatus
import net.moesli.scan7.ui.nav.AfterScan
import net.moesli.scan7.ui.nav.EntryMode
import net.moesli.scan7.ui.nav.Route
import net.moesli.scan7.ui.nav.ScanStart
import net.moesli.scan7.ui.nav.nextPlayer
import net.moesli.scan7.ui.players.playerUis
import net.moesli.scan7.ui.round.BustToastState
import net.moesli.scan7.ui.share.ResumeSavedSession
import net.moesli.scan7.ui.theme.Scan7Theme

private const val TAG = "ScanRoute"

/** How long the camera's full-screen bust overlay stays before the flow moves on. */
private const val BUST_OVERLAY_MS = 1_000L

/** Stand-in for "setting not loaded yet" (the stored value is never this). */
private const val LOADING = "\u0000"

/**
 * [ScanFlowContent] for one player of a stored game. The confirmed outcome is saved as the player's
 * result (cards incl. action cards, source SCAN / MANUAL; duplicates → bust with its cards).
 *
 * @param round the round to score; `null` = the running round (editing a closed round recomputes
 *   totals and game over).
 * @param onSaved called after saving with the saved state, the "after scan" choice of the
 *   split OK button (also after the camera's Bust tap) and the entry mode; the caller continues with
 *   [net.moesli.scan7.ui.nav.afterSave].
 * @param onLeave cancel / ← back to the table.
 * @param debugCapture DEBUG: start camera scans in Review on this capture (photo file hook).
 * @param bustToast shared bust undo toast: the camera's Bust tap shows it (on the next player's
 *   camera, or on the round screen after returning) and its Undo reopens the busted player.
 */
@Composable
fun ScanRoute(
    gameId: Long,
    playerId: Long,
    start: ScanStart,
    round: Int?,
    onSaved: (GameState, AfterScan, EntryMode) -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    repository: GameRepository = AppGraph.repository(LocalContext.current),
    debugCapture: (suspend (Context) -> ScanCapture)? = null,
    bustToast: BustToastState? = null,
) {
    val snapshot by remember(gameId) { repository.observeGame(gameId) }.collectAsStateWithLifecycle(initialValue = null)
    val stored by remember { repository.setting(AfterScan.SETTING_KEY) }.collectAsStateWithLifecycle(initialValue = LOADING)
    val storedEntry by remember { repository.setting(EntryMode.SETTING_KEY) }.collectAsStateWithLifecycle(initialValue = LOADING)
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    // Full-screen bust overlay on the camera (player id while shown) and its Undo signal.
    var bustShown by remember { mutableStateOf<Long?>(null) }
    var bustUndo by remember { mutableStateOf<CompletableDeferred<Unit>?>(null) }
    val snap = snapshot
    val state = snap?.state
    // Everyone of the game: a scoreboard edit can open a player who left mid-game.
    val index = state?.everyoneIds?.indexOf(playerId) ?: -1
    val camera = start == ScanStart.CAMERA || start == ScanStart.NEXT
    val context = LocalContext.current
    val capture by produceState<ScanCapture?>(null, debugCapture, playerId) {
        if (camera && debugCapture != null) value = debugCapture(context)
    }
    if (snap == null || state == null || index < 0 || stored == LOADING || storedEntry == LOADING || (camera && debugCapture != null && capture == null)) {
        Box(modifier.fillMaxSize().background(Scan7Theme.colors.ground))
        return
    }
    ResumeSavedSession(gameId, snap.isJoined, state.isOver, repository)
    // The ▾ menu only selects (stores the setting); OK is a separate tap, so `choice` is current then.
    val choice = AfterScan.fromSetting(stored)
    val entry = EntryMode.fromSetting(storedEntry)
    // The round the screen opened in, kept: on a shared game the host may close it meanwhile, and
    // the confirmed scan must then be refused for that round, not land in the next one.
    val roundNumber = round ?: remember(gameId, playerId, start) { state.currentRound.number }
    val players = state.playerUis(snap.players, roundNumber, state.everyoneIds)
    val player = players[index]
    val nextName = Route.Scan(gameId, playerId, start, round).nextPlayer(state)
        ?.let { state.everyoneIds.indexOf(it) }
        ?.let { players.getOrNull(it)?.name }
    val afterScan = AfterScanUi(choice, nextName) { c ->
        scope.launch {
            runCatching { repository.putSetting(AfterScan.SETTING_KEY, c.name) }.onFailure { Log.w(TAG, "saving the choice failed", it) }
        }
    }
    val initialCards = when (start) {
        ScanStart.MANUAL -> emptyList()
        ScanStart.EDIT -> state.result(playerId, roundNumber).cards.flip7Cards()
        ScanStart.CAMERA, ScanStart.NEXT -> null
    }
    val save = { what: String, block: suspend GameRepository.() -> GameState, then: (GameState) -> Unit ->
        if (!saving) {
            saving = true
            scope.launch {
                // outlivingUi: leaving the screen must not roll the write back; a cancelled wait
                // (the route is gone) must not call onLeave, which would pop a second time.
                runCatching { outlivingUi { repository.block() } }.onSuccess(then).onFailure {
                    if (it is CancellationException) throw it
                    Log.w(TAG, "$what failed", it)
                    onLeave()
                }
                saving = false
            }
        }
    }
    if (state.kind == GameKind.SKYJO) {
        // No Skyjo detector yet: every scan / edit is the grid entry (prefilled when editing).
        val grid = remember(playerId, roundNumber) {
            if (start == ScanStart.EDIT) skyjoGridOf(state.result(playerId, roundNumber).cards) else emptySkyjoGrid()
        }
        key(playerId) {
            SkyjoEntryStep(
                player = player,
                start = grid,
                onSave = { g ->
                    save("saving the grid", { scoreCards(gameId, playerId, g.toConfirmedCards(), ResultSource.MANUAL, roundNumber) }) {
                        onSaved(it, choice, entry)
                    }
                },
                onSaveTotal = { points ->
                    save("saving the total", { scorePoints(gameId, playerId, points, roundNumber) }) { onSaved(it, choice, entry) }
                },
                onCancel = onLeave,
                modifier = modifier,
                afterScan = afterScan,
            )
        }
        return
    }
    // Bust tap on the camera: only for an open player of the running round (its undo resets to open).
    val canBust = camera && round == null && (player.status == PlayerStatus.Open || bustShown == playerId)
    ScanFlowContent(
        player = player,
        onResult = { outcome ->
            save("saving the result", { scoreCards(gameId, playerId, outcome.toConfirmedCards(), outcome.resultSource, roundNumber) }) {
                onSaved(it, choice, entry)
            }
        },
        onCancel = onLeave,
        modifier = modifier,
        mode = if (start == ScanStart.NEXT) ScanMode.ScanNext else ScanMode.Normal,
        initialCards = initialCards,
        roundNumber = roundNumber,
        onBackToTable = onLeave,
        initialCapture = capture,
        // Same bust / undo as the round screen (GameRepository.bust → BUST_TAP; undoBust), with
        // the full-screen overlay first: ~1 s with Undo, then on as after a save (no toast: the
        // overlay was the confirmation; only Back during it shows the toast, see bustWithOverlay).
        onBust = if (canBust) {
            {
                if (!saving && bustShown == null) {
                    saving = true
                    bustShown = playerId
                    val undo = CompletableDeferred<Unit>().also { bustUndo = it }
                    val tappedAt = SystemClock.elapsedRealtime()
                    val name = player.name
                    scope.launch {
                        val saved = runCatching {
                            bustWithOverlay(
                                bust = { outlivingUi { repository.bust(gameId, playerId, roundNumber) } },
                                undoBust = {
                                    runCatching { outlivingUi { repository.undoBust(gameId, playerId) } }
                                        .onFailure { if (it !is CancellationException) Log.w(TAG, "undo bust failed", it) }
                                },
                                undo = undo,
                                overlayMs = { BUST_OVERLAY_MS - (SystemClock.elapsedRealtime() - tappedAt) },
                                // Back during the overlay: the bust stays, so its Undo toast must show.
                                onLeftBusted = { bustToast?.show(playerId.toString(), name) },
                            )
                        }.getOrElse {
                            if (it is CancellationException) throw it
                            Log.w(TAG, "bust failed", it)
                            bustShown = null
                            saving = false
                            return@launch
                        }
                        // The full overlay (with its Undo) already confirmed the bust: no toast on top.
                        if (saved != null) onSaved(saved, choice, entry)
                        bustShown = null
                        bustUndo = null
                        saving = false
                    }
                }
            }
        } else {
            null
        },
        bustToast = bustToast,
        onUndoBust = { id ->
            scope.launch {
                runCatching { outlivingUi { repository.undoBust(gameId, id.toLong()) } }.onFailure { Log.w(TAG, "undo bust failed", it) }
            }
        },
        afterScan = afterScan,
        bustOverlay = player.name.takeIf { bustShown == playerId },
        onUndoBustOverlay = { bustUndo?.complete(Unit) },
    )
}

/**
 * The camera Bust tap: [bust], then the overlay for [overlayMs] (read after the write) unless
 * [undo] completes first, which runs [undoBust].
 *
 * @return the saved state if the bust stays, `null` if it was undone.
 * @param onLeftBusted the caller was cancelled (Back during the overlay) while the bust stays:
 *   show its Undo toast. An Undo tapped just before still runs [undoBust].
 */
internal suspend fun <T : Any> bustWithOverlay(
    bust: suspend () -> T,
    undoBust: suspend () -> Unit,
    undo: Deferred<Unit>,
    overlayMs: () -> Long,
    onLeftBusted: () -> Unit,
): T? {
    var undoing = false
    try {
        val saved = bust()
        val undone = undo.isCompleted || withTimeoutOrNull(overlayMs().coerceAtLeast(1)) { undo.await() } != null
        if (!undone) return saved
        undoing = true
        undoBust()
        return null
    } catch (e: CancellationException) {
        if (!undo.isCompleted) onLeftBusted() else if (!undoing) withContext(NonCancellable) { undoBust() }
        throw e
    }
}
