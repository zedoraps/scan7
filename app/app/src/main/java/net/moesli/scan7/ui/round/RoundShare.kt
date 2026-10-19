package net.moesli.scan7.ui.round

import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import net.moesli.scan7.ui.components.scan7Clickable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import net.moesli.scan7.R
import net.moesli.scan7.multiplayer.ClientStatus
import net.moesli.scan7.multiplayer.DeviceInfo
import net.moesli.scan7.multiplayer.DeviceLabel
import net.moesli.scan7.share.GuestInfo
import net.moesli.scan7.share.HostInfo
import net.moesli.scan7.share.ShareText
import net.moesli.scan7.game.Player
import net.moesli.scan7.ui.components.ShareMark
import net.moesli.scan7.ui.text.uiText
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/**
 * The host's Round screen while the game is shareable: the header share icon (with the number of
 * connected phones) and a badge on every player another phone scans.
 */
fun RoundUiState.hosted(host: HostInfo?, players: Map<Long, Player>): RoundUiState {
    if (host == null) return copy(share = RoundShareUi(guest = false, phones = 0))
    val marks = host.scannedBy.mapValues { (_, d) -> otherMark(d.label(players)) }
    return copy(
        share = RoundShareUi(guest = false, phones = host.guests.count { it.connected }),
        players = this.players.map { p -> p.copy(player = p.player.copy(share = marks[p.id.toLong()])) },
    )
}

/**
 * A guest's Round screen (ids are local): its own players highlighted, the other phones' players
 * badged, no round ending, the waiting line and the connection banner.
 */
fun RoundUiState.joined(guest: GuestInfo): RoundUiState {
    val labelOf = { d: DeviceInfo -> guest.labels[d.deviceId] }
    val marks = guest.scannedBy.mapValues { (_, d) -> otherMark(labelOf(d)) } +
        guest.myScans.associateWith { ShareMark(mine = true, initial = "", description = uiText(R.string.round_you_scan)) }
    val waiting = if (allDone) uiText(R.string.round_waiting_host, ShareText.phone(guest.host)) else uiText(R.string.round_mine_done)
    val banner = when {
        guest.joinRejection != null -> ShareText.joinRejection(guest.joinRejection)
        guest.status == ClientStatus.DISCONNECTED -> uiText(R.string.banner_lost)
        guest.status == ClientStatus.CONNECTING -> uiText(R.string.banner_connecting)
        else -> null
    }
    return copy(
        share = RoundShareUi(guest = true, waiting = waiting, banner = banner),
        players = players.map { p -> p.copy(player = p.player.copy(share = marks[p.id.toLong()])) },
    )
}

private fun otherMark(label: DeviceLabel?): ShareMark {
    val initial = when (label) {
        is DeviceLabel.OfPlayer -> label.name.trim().take(1).uppercase()
        is DeviceLabel.Helper -> label.name.trim().take(1).uppercase()
        else -> ""
    }
    return ShareMark(mine = false, initial = initial, description = uiText(R.string.claim_scanned_by, ShareText.phone(label)))
}

/** Connection status of a joined game, e.g. "Connection lost · reconnecting…"; [rejoin]: offer Join again. */
internal data class ConnectionStatus(val message: String, val rejoin: Boolean)

private class ConnectionSnackbarVisuals(val status: ConnectionStatus, override val actionLabel: String?) : SnackbarVisuals {
    override val message: String = status.message
    override val withDismissAction: Boolean = false

    // Stays while the state lasts; a new state (or none) replaces it.
    override val duration: SnackbarDuration = SnackbarDuration.Indefinite
}

/**
 * A guest's connection state as a Material 3 snackbar (the Bust toast's [ToastPanel] look) at the
 * bottom above the buttons, instead of a banner over the cards. Stays up while [status] lasts; the
 * "not connected" state carries the Join again action ([onRejoin]).
 */
@Composable
internal fun ConnectionSnackbarHost(status: ConnectionStatus?, onRejoin: () -> Unit, modifier: Modifier = Modifier) {
    val host = remember { SnackbarHostState() }
    val rejoin by rememberUpdatedState(onRejoin)
    val joinAgain = stringResource(R.string.action_join_again)
    LaunchedEffect(status) {
        // A new key cancels the running showSnackbar, which takes that snackbar down.
        if (status == null) return@LaunchedEffect
        val result = host.showSnackbar(ConnectionSnackbarVisuals(status, joinAgain.takeIf { status.rejoin }))
        if (result == SnackbarResult.ActionPerformed) rejoin()
    }
    SnackbarHost(hostState = host, modifier = modifier) { snackbar ->
        val visuals = snackbar.visuals as? ConnectionSnackbarVisuals ?: return@SnackbarHost
        ToastPanel(visuals.message, visuals.actionLabel, visuals.actionLabel.orEmpty(), onAction = snackbar::performAction)
    }
}

/** A guest's bottom line while it has nothing to scan ("Waiting for Philip to close the round"). */
@Composable
internal fun WaitingLine(text: String, modifier: Modifier = Modifier) {
    val colors = Scan7Theme.colors
    Box(contentAlignment = Alignment.Center, modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(text, color = colors.textMuted, style = Scan7Type.text15, textAlign = TextAlign.Center, maxLines = 2)
    }
}
