package net.moesli.scan7.ui.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import net.moesli.scan7.data.AppGraph
import net.moesli.scan7.data.GameRepository
import net.moesli.scan7.multiplayer.nearby.JoinLink
import net.moesli.scan7.share.LinkMode

/**
 * The scan screen restored straight onto the camera after the app was killed: rejoins (guest) or
 * shares again (host) the game's saved session, silently and only when the radio permissions are
 * granted. Without it the scan confirmed there hit the read-only joined copy (lost) or never
 * reached the guests, because only the round screen resumed sessions (it also asks for permissions).
 */
@Composable
fun ResumeSavedSession(gameId: Long, isJoined: Boolean, isOver: Boolean, repository: GameRepository) {
    val context = LocalContext.current
    val permissions = remember { RadioPermissionRequest(context) }
    LaunchedEffect(gameId, isJoined) {
        val row = repository.shareSession(gameId) ?: return@LaunchedEffect
        val runtime = AppGraph.share(context)
        val state = runtime.state.value
        if (!runtime.radioAvailable || permissions.missing().isNotEmpty() || state.host != null || state.guest != null) return@LaunchedEffect
        when {
            isJoined && row.role == "GUEST" -> runtime.join(JoinLink(row.sessionId, row.token), LinkMode.RADIO)
            !isJoined && !isOver && row.role == "HOST" -> runtime.host(gameId, LinkMode.RADIO)
        }
    }
}
