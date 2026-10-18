package net.moesli.scan7.ui.scan

import android.content.Context
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
import kotlinx.coroutines.launch
import net.moesli.scan7.data.AppGraph
import net.moesli.scan7.data.GameRepository
import net.moesli.scan7.game.GameState
import net.moesli.scan7.ui.components.PlayerStatus
import net.moesli.scan7.ui.nav.ScanStart
import net.moesli.scan7.ui.players.playerUis
import net.moesli.scan7.ui.round.BustToastState
import net.moesli.scan7.ui.theme.Scan7Theme

private const val TAG = "ScanRoute"

/**
 * [ScanFlow] for one player of a stored game. The confirmed outcome is saved as the player's
 * result (cards incl. ignored ones, source SCAN / MANUAL; duplicates → bust with its cards).
 *
 * @param round the round to score; `null` = the running round (editing a closed round recomputes
 *   totals and game over).
 * @param onSaved called after saving with the saved state (also after the camera's Bust tap); for
 *   [ScanStart.NEXT] the caller continues with [net.moesli.scan7.ui.nav.afterSave].
 * @param onLeave cancel / "Back to table".
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
    onSaved: (GameState) -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    repository: GameRepository = AppGraph.repository(LocalContext.current),
    debugCapture: (suspend (Context) -> ScanCapture)? = null,
    bustToast: BustToastState? = null,
) {
    val snapshot by remember(gameId) { repository.observeGame(gameId) }.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    val snap = snapshot
    val state = snap?.state
    val index = state?.playerIds?.indexOf(playerId) ?: -1
    val camera = start == ScanStart.CAMERA || start == ScanStart.NEXT
    val context = LocalContext.current
    val capture by produceState<ScanCapture?>(null, debugCapture, playerId) {
        if (camera && debugCapture != null) value = debugCapture(context)
    }
    if (snap == null || state == null || index < 0 || (camera && debugCapture != null && capture == null)) {
        Box(modifier.fillMaxSize().background(Scan7Theme.colors.ground))
        return
    }
    val roundNumber = round ?: state.currentRound.number
    val player = state.playerUis(snap.players, roundNumber)[index]
    val initialCards = when (start) {
        ScanStart.MANUAL -> emptyList()
        ScanStart.EDIT -> state.result(playerId, roundNumber).cards.map { it.card }
        ScanStart.CAMERA, ScanStart.NEXT -> null
    }
    val save = { what: String, block: suspend GameRepository.() -> GameState, then: (GameState) -> Unit ->
        if (!saving) {
            saving = true
            scope.launch {
                runCatching { repository.block() }.onSuccess(then).onFailure {
                    Log.w(TAG, "$what failed", it)
                    onLeave()
                }
                saving = false
            }
        }
    }
    // Bust tap on the camera: only for an open player of the running round (its undo resets to open).
    val canBust = camera && round == null && player.status == PlayerStatus.Open
    ScanFlowContent(
        player = player,
        onResult = { outcome ->
            save("saving the result", { scoreCards(gameId, playerId, outcome.toConfirmedCards(), outcome.resultSource, round) }, onSaved)
        },
        onCancel = onLeave,
        modifier = modifier,
        mode = if (start == ScanStart.NEXT) ScanMode.ScanNext else ScanMode.Normal,
        initialCards = initialCards,
        roundNumber = roundNumber,
        onBackToTable = onLeave,
        initialCapture = capture,
        // Same bust / undo as the round screen (GameRepository.bust → BUST_TAP; undoBust).
        onBust = if (canBust) {
            {
                save("bust", { bust(gameId, playerId) }) { saved ->
                    bustToast?.show(playerId.toString(), player.name)
                    onSaved(saved)
                }
            }
        } else {
            null
        },
        bustToast = bustToast,
        onUndoBust = { id ->
            scope.launch {
                runCatching { repository.undoBust(gameId, id.toLong()) }.onFailure { Log.w(TAG, "undo bust failed", it) }
            }
        },
    )
}
