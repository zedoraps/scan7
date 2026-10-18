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
import net.moesli.scan7.ui.components.PlayerStatus
import net.moesli.scan7.ui.nav.AfterScan
import net.moesli.scan7.ui.nav.Route
import net.moesli.scan7.ui.nav.ScanStart
import net.moesli.scan7.ui.nav.nextPlayer
import net.moesli.scan7.ui.players.playerUis
import net.moesli.scan7.ui.round.BustToastState
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
 * @param onSaved called after saving with the saved state and the "after scan" choice of the
 *   split OK button (also after the camera's Bust tap); the caller continues with
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
    onSaved: (GameState, AfterScan) -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    repository: GameRepository = AppGraph.repository(LocalContext.current),
    debugCapture: (suspend (Context) -> ScanCapture)? = null,
    bustToast: BustToastState? = null,
) {
    val snapshot by remember(gameId) { repository.observeGame(gameId) }.collectAsStateWithLifecycle(initialValue = null)
    val stored by remember { repository.setting(AfterScan.SETTING_KEY) }.collectAsStateWithLifecycle(initialValue = LOADING)
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    // Picked from the ▾ menu in this flow: used right away (the stored value follows).
    var picked by remember { mutableStateOf<AfterScan?>(null) }
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
    if (snap == null || state == null || index < 0 || stored == LOADING || (camera && debugCapture != null && capture == null)) {
        Box(modifier.fillMaxSize().background(Scan7Theme.colors.ground))
        return
    }
    // Read `picked` again when a save completes: the ▾ menu sets it and saves in the same tap, before
    // this composition (and `choice`) catches up, so a captured `choice` would still be the old one.
    val choice = picked ?: AfterScan.fromSetting(stored)
    val roundNumber = round ?: state.currentRound.number
    val players = state.playerUis(snap.players, roundNumber, state.everyoneIds)
    val player = players[index]
    val nextName = Route.Scan(gameId, playerId, start, round).nextPlayer(state)
        ?.let { state.everyoneIds.indexOf(it) }
        ?.let { players.getOrNull(it)?.name }
    val afterScan = AfterScanUi(choice, nextName) { c ->
        picked = c
        scope.launch {
            runCatching { repository.putSetting(AfterScan.SETTING_KEY, c.name) }.onFailure { Log.w(TAG, "saving the choice failed", it) }
        }
    }
    val initialCards = when (start) {
        ScanStart.MANUAL -> emptyList()
        ScanStart.EDIT -> state.result(playerId, roundNumber).cards.map { it.card }
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
    // Bust tap on the camera: only for an open player of the running round (its undo resets to open).
    val canBust = camera && round == null && (player.status == PlayerStatus.Open || bustShown == playerId)
    ScanFlowContent(
        player = player,
        onResult = { outcome ->
            save("saving the result", { scoreCards(gameId, playerId, outcome.toConfirmedCards(), outcome.resultSource, round) }) {
                onSaved(it, picked ?: choice)
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
        // the full-screen overlay first: ~1 s with Undo, then the toast and on as after a save.
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
                                bust = { outlivingUi { repository.bust(gameId, playerId) } },
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
                        if (saved != null) {
                            bustToast?.show(playerId.toString(), name)
                            onSaved(saved, picked ?: choice)
                        }
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
