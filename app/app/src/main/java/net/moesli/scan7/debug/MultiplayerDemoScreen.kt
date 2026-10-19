package net.moesli.scan7.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.moesli.scan7.data.AppGraph
import net.moesli.scan7.share.LinkMode
import net.moesli.scan7.ui.components.PrimaryButton
import net.moesli.scan7.ui.components.SecondaryButton
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/**
 * Debug → Multiplayer (experimental): exercises the whole multiplayer UI on one phone with no radio.
 * This phone hosts its running game over the in-process network and also joins it as a virtual
 * guest (a hidden joined copy in the same database), so both round screens can be opened and
 * played against each other: badges, guest limits, waiting line, refusals, claims, Same players.
 */
@Composable
fun MultiplayerDemoScreen(
    onBack: () -> Unit,
    onOpenRound: (Long) -> Unit,
    onJoinScreen: () -> Unit,
) {
    val context = LocalContext.current
    val runtime = remember { AppGraph.share(context) }
    val repository = remember { AppGraph.repository(context) }
    val share by runtime.state.collectAsStateWithLifecycle()
    val active by remember { repository.activeGame }.collectAsStateWithLifecycle(initialValue = null)
    val colors = Scan7Theme.colors
    DebugScaffold(title = "Multiplayer", subtitle = "In-process demo · radio: " + (if (runtime.radioAvailable) "Nearby (play)" else "none (foss)"), onBack = onBack) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(Scan7Spacing.screenMargin),
        ) {
            val host = share.host
            val guest = share.guest
            Text(
                "Running game: " + (active?.state?.id?.toString() ?: "none (start one first)") +
                    "\nHosting: " + (host?.let { "game ${it.gameId} · ${it.mode} · session ${it.link.sessionId} · ${it.guests.size} guest(s)" } ?: "no") +
                    "\nJoined: " + (guest?.let { "copy ${it.localGameId} · ${it.status} · me ${it.myPlayer} · scans ${it.myScans} · pending ${it.pending}" } ?: "no"),
                color = colors.text,
                style = Scan7Type.text12,
            )
            val gameId = active?.state?.id
            if (host == null) {
                PrimaryButton("Host the running game (in-process)", { gameId?.let { runtime.host(it, LinkMode.IN_PROCESS) } }, Modifier.fillMaxWidth(), enabled = gameId != null)
            } else {
                PrimaryButton("Open the host's round screen", { onOpenRound(host.gameId) }, Modifier.fillMaxWidth())
                if (guest == null) {
                    SecondaryButton("Join it as a virtual guest", { runtime.join(host.link, LinkMode.IN_PROCESS) }, Modifier.fillMaxWidth())
                }
                SecondaryButton("Stop hosting", { runtime.stopHosting() }, Modifier.fillMaxWidth())
            }
            if (guest != null) {
                guest.localGameId?.let { id -> PrimaryButton("Open the guest's round screen", { onOpenRound(id) }, Modifier.fillMaxWidth()) }
                SecondaryButton("Guest: leave", { runtime.leave() }, Modifier.fillMaxWidth())
            }
            SecondaryButton("Join screen (QR scanner)", onJoinScreen, Modifier.fillMaxWidth())
            Text(
                "Tip: on the guest's round screen pick who you are (sheet opens on its own), score your player, " +
                    "then switch to the host's screen: the badge, the stored score and Scan next skipping that player show up there.",
                color = colors.textMuted,
                style = Scan7Type.text12,
            )
        }
    }
}
