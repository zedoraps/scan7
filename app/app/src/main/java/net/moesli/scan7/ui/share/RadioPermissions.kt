package net.moesli.scan7.ui.share

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import net.moesli.scan7.R
import net.moesli.scan7.multiplayer.nearby.NearbyPermissions
import net.moesli.scan7.ui.components.Scan7ConfirmDialog

/**
 * The "Nearby devices" (API 31+) / location (API 29–30) permissions for multiplayer, asked only
 * when the user taps Share or Join (docs/dev/MULTI_DEVICE.md §6.3): a one-line rationale first, then
 * the system dialog; denied → "Open settings". [run] calls `then` at once when everything is granted,
 * `onCancel` when the user backs out (rationale cancelled, or denied) so the caller can close its UI.
 */
@Stable
class RadioPermissionRequest internal constructor(private val context: Context) {
    internal var pending: (() -> Unit)? by mutableStateOf(null)
    internal var cancelled: (() -> Unit)? by mutableStateOf(null)
    internal var rationale by mutableStateOf(false)
    internal var denied by mutableStateOf(false)
    internal var launch: (Array<String>) -> Unit = {}

    /**
     * Set when a button sent the user to the system settings (app, Location, Bluetooth, Wi-Fi):
     * [OnReturnFromSettings] then tries the radio again once. Not keyed on the radio's status: that
     * retried on every status change and, with Nearby refusing a granted permission, made the Join
     * screen flicker between "Connecting" and "permissions" forever (field report 2026-10-19).
     */
    internal var sentToSettings = false

    /** Opens a settings screen ([action], e.g. `Settings.ACTION_LOCATION_SOURCE_SETTINGS`); the return tries again. */
    fun openSettings(action: String) {
        sentToSettings = true
        val intent = if (action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS) {
            Intent(action, Uri.fromParts("package", context.packageName, null))
        } else {
            Intent(action)
        }
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun openAppSettings() = openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)

    fun missing(): List<String> = NearbyPermissions.missing(Build.VERSION.SDK_INT) {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** [missing] plus the optional ones not granted yet (the notification), for the system dialog. */
    internal fun toRequest(): List<String> = missing() + NearbyPermissions.optional(Build.VERSION.SDK_INT).filterNot {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun run(onCancel: () -> Unit = {}, then: () -> Unit) {
        if (missing().isEmpty()) return then()
        pending = then
        cancelled = onCancel
        rationale = true
    }

    internal fun cancel() {
        pending = null
        cancelled?.invoke()
        cancelled = null
    }
}

@Composable
fun rememberRadioPermissionRequest(): RadioPermissionRequest {
    val context = LocalContext.current
    val request = remember { RadioPermissionRequest(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        val then = request.pending
        request.pending = null
        if (request.missing().isEmpty()) {
            request.cancelled = null
            then?.invoke()
        } else {
            request.denied = true
        }
    }
    request.launch = { launcher.launch(it) }
    if (request.rationale) {
        val sdk = Build.VERSION.SDK_INT
        Scan7ConfirmDialog(
            title = stringResource(
                if (NearbyPermissions.rationale(sdk) == NearbyPermissions.Rationale.LOCATION) R.string.permission_rationale_location else R.string.permission_rationale_nearby,
            ),
            confirmLabel = stringResource(R.string.permission_continue),
            onConfirm = {
                request.rationale = false
                request.launch(request.toRequest().toTypedArray())
            },
            onCancel = {
                request.rationale = false
                request.cancel()
            },
        )
    }
    if (request.denied) {
        Scan7ConfirmDialog(
            title = stringResource(R.string.permission_denied),
            confirmLabel = stringResource(R.string.permission_settings),
            onConfirm = {
                request.denied = false
                request.cancel()
                request.openAppSettings()
            },
            onCancel = {
                request.denied = false
                request.cancel()
            },
        )
    }
    return request
}

/** Back from a settings screen [request] opened: [onReturn] once (a fresh radio try). */
@Composable
fun OnReturnFromSettings(request: RadioPermissionRequest, onReturn: () -> Unit) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (request.sentToSettings) {
            request.sentToSettings = false
            onReturn()
        }
    }
}
