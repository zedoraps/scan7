package net.moesli.scan7.ui.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.moesli.scan7.R
import net.moesli.scan7.data.AppGraph
import net.moesli.scan7.data.GameSnapshot
import net.moesli.scan7.data.outlivingUi
import net.moesli.scan7.multiplayer.PlayerImport
import net.moesli.scan7.ui.components.Scan7ConfirmDialog

/**
 * A joined game → "Save players to my list" (U17): the players this phone doesn't have yet
 * (matched by name) become normal stored players; the others are already in the list.
 */
@Composable
fun SavePlayersDialog(snap: GameSnapshot, onDone: () -> Unit) {
    val repository = AppGraph.repository(LocalContext.current)
    val mine by repository.players.collectAsStateWithLifecycle(initialValue = emptyList())
    val candidates = remember(snap, mine) { PlayerImport.candidates(snap.everyone, mine) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    if (candidates.isEmpty()) {
        Scan7ConfirmDialog(
            title = stringResource(R.string.save_players_none),
            confirmLabel = stringResource(R.string.refusal_ok),
            onConfirm = onDone,
            onCancel = onDone,
        )
    } else {
        Scan7ConfirmDialog(
            title = stringResource(R.string.save_players_title),
            body = candidates.joinToString(", ") { it.name },
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = {
                scope.launch { runCatching { outlivingUi { repository.keepJoinedPlayers(candidates.map { it.id }) } } }
                onDone()
            },
            onCancel = onDone,
        )
    }
}
