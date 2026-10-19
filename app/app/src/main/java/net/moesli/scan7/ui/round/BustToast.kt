package net.moesli.scan7.ui.round

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import net.moesli.scan7.ui.text.uiText
import net.moesli.scan7.ui.text.resolve
import net.moesli.scan7.ui.text.UiText
import androidx.compose.ui.platform.LocalResources
import android.content.res.Resources
import net.moesli.scan7.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.moesli.scan7.ui.components.scan7Clickable
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/** "<name> marked bust · Undo"; [seq] makes every showing distinct (same player busted again). */
@Immutable
data class BustToastData(val playerId: String, val name: String, val seq: Long)

/**
 * State of the round screen's bust toast ("<name> marked bust · Undo"), hoisted out of [RoundScreen] so every bust path (in-card Bust inside the screen, the player
 * sheet's Bust in the route, the scan camera's Bust) shows the same toast. [current] is the
 * request; [BustToastHost] shows it through Material 3's [SnackbarHost] (timing, TalkBack
 * announcement and dismiss action come from there). A new toast replaces the one showing.
 */
@Stable
class BustToastState {
    var current: BustToastData? by mutableStateOf(null)
        private set
    private var seq = 0L

    /** Shows the bust toast for [playerId]. */
    fun show(playerId: String, name: String) {
        current = BustToastData(playerId, name, ++seq)
    }

    fun dismiss() {
        current = null
    }

    /** The host is done with [data] (timeout, Undo, dismissed): clears it unless a newer one replaced it. */
    internal fun finished(data: BustToastData) {
        if (current == data) current = null
    }
}

@Composable
fun rememberBustToastState(): BustToastState = remember { BustToastState() }

/** Snackbar visuals of one toast (the slot draws [toastText] of [data]), texts resolved with [resources]. */
private class RoundSnackbarVisuals(val data: BustToastData, resources: Resources) : SnackbarVisuals {
    override val message: String = toastText(data).message.resolve(resources)
    override val actionLabel: String = toastText(data).action.resolve(resources)
    override val withDismissAction: Boolean = false

    // Short = 4 s, stretched by the host to the user's accessibility timeout.
    override val duration: SnackbarDuration = SnackbarDuration.Short
}

/** Texts of a toast: [message], the [action] label and its TalkBack description. */
internal data class ToastText(val message: UiText, val action: UiText, val actionDescription: UiText)

internal fun toastText(toast: BustToastData): ToastText = ToastText(
    uiText(R.string.toast_bust, toast.name),
    uiText(R.string.action_undo),
    uiText(R.string.action_undo_bust_player, toast.name),
)

/**
 * Shows [toast] as a Material 3 snackbar restyled as [ToastPanel]. Undo calls [onUndo] with the
 * player id. A new toast cancels the running snackbar, so it replaces
 * it. When this host leaves the screen with a toast still up (round ↔ camera), the next host shows
 * it again.
 */
@Composable
internal fun BustToastHost(
    toast: BustToastState,
    onUndo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val host = remember { SnackbarHostState() }
    val undo by rememberUpdatedState(onUndo)
    val data = toast.current
    val resources = LocalResources.current
    LaunchedEffect(data) {
        // A new key cancels the previous showSnackbar, which takes that snackbar down.
        if (data == null) return@LaunchedEffect
        val result = host.showSnackbar(RoundSnackbarVisuals(data, resources))
        if (result == SnackbarResult.ActionPerformed) undo(data.playerId)
        toast.finished(data)
    }
    SnackbarHost(hostState = host, modifier = modifier) { snackbar ->
        val visuals = snackbar.visuals as? RoundSnackbarVisuals ?: return@SnackbarHost
        val text = toastText(visuals.data)
        ToastPanel(text.message.resolve(), text.action.resolve(), text.actionDescription.resolve(), onAction = snackbar::performAction)
    }
}

/**
 * Toast panel ("Anna marked bust · UNDO"): `surface` panel, 48
 * high, radius 12, at the bottom above the buttons; the action in cyan caps. The [BustToastHost]
 * announces it politely to TalkBack.
 */
@Composable
internal fun ToastPanel(
    message: String,
    action: String?,
    actionDescription: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.iconButton)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            // Intrinsic height: the action's fillMaxHeight must match the text, not the snackbar
            // host's whole height (that blew the Bust toast up into an empty panel).
            .heightIn(min = Scan7Spacing.minTouch)
            .height(IntrinsicSize.Min)
            .background(c.surface, shape)
            .border(1.5.dp, c.line, shape),
    ) {
        Text(
            text = message,
            color = c.text,
            style = Scan7Type.text13,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = Scan7Spacing.l, end = if (action == null) Scan7Spacing.l else 0.dp)
                .padding(vertical = Scan7Spacing.xs),
        )
        if (action != null) Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(min = Scan7Spacing.minTouch)
                .clip(shape)
                .scan7Clickable(onAction)
                .clearAndSetSemantics {
                    contentDescription = actionDescription
                    role = Role.Button
                }
                .padding(horizontal = Scan7Spacing.l),
        ) {
            Text(action.uppercase(), color = c.accent, style = Scan7Type.tag12.copy(letterSpacing = Scan7Type.capsButton.letterSpacing))
        }
    }
}

@Preview(widthDp = 390)
@Composable
private fun BustToastPreview() {
    Scan7Theme {
        val text = toastText(BustToastData("1", "Anna", 1))
        ToastPanel(text.message.resolve(), text.action.resolve(), text.actionDescription.resolve(), {}, Modifier.padding(Scan7Spacing.screenMargin))
    }
}
