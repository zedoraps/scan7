package net.moesli.scan7.ui.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import net.moesli.scan7.BuildConfig
import net.moesli.scan7.R
import net.moesli.scan7.multiplayer.nearby.LinkStats
import net.moesli.scan7.multiplayer.nearby.NearbyPermissions
import net.moesli.scan7.multiplayer.nearby.TransportStatus
import net.moesli.scan7.share.RadioDiagnostics
import net.moesli.scan7.share.RadioDiagnosticsInput
import net.moesli.scan7.share.RadioFix
import net.moesli.scan7.share.RadioLog
import net.moesli.scan7.share.RadioRole
import net.moesli.scan7.share.RadioText
import net.moesli.scan7.share.ShareRuntime
import net.moesli.scan7.ui.components.PrimaryButton
import net.moesli.scan7.ui.components.SecondaryButton
import net.moesli.scan7.ui.text.resolve
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The radio's state on the Join screen / share sheet ([RadioText]): one line, the button to the
 * right settings where it applies, the Wi-Fi hint, and the collapsed "Technical details". Every
 * settings button marks [permissions] so the return from the settings tries again once
 * ([OnReturnFromSettings] in the caller).
 */
@Composable
fun RadioPanel(
    role: RadioRole,
    status: TransportStatus?,
    stats: LinkStats?,
    runtime: ShareRuntime,
    permissions: RadioPermissionRequest,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    /** Smaller text and buttons (share sheet). */
    compact: Boolean = false,
) {
    val colors = Scan7Theme.colors
    val activity = LocalActivity.current
    // Re-read the switches and Play services after every return to the app and every radio event.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    val logVersion by RadioLog.app.version.collectAsState()
    val playServices = remember(resumes) { runtime.playServices() }
    val settings = remember(resumes, logVersion, status) { runtime.radioSettings() }
    val message = RadioText.status(role, status, playServices)
    // Switches that are off but don't stop the try: hints under the main line.
    val hints = listOfNotNull(RadioText.bluetoothHint(settings), RadioText.wifiHint(settings))
    val textStyle = if (compact) Scan7Type.text12 else Scan7Type.text15
    val buttonHeight = if (compact) Scan7Spacing.minTouch else Scan7Spacing.minTouch + 8.dp
    val act = { fix: RadioFix ->
        when (fix) {
            RadioFix.ALLOW -> permissions.run { onRetry() }
            RadioFix.APP_SETTINGS -> permissions.openAppSettings()
            RadioFix.LOCATION_SETTINGS -> permissions.openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            RadioFix.BLUETOOTH_SETTINGS -> permissions.openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
            // The system's Wi-Fi panel over the app (API 29+ = minSdk).
            RadioFix.WIFI_SETTINGS -> permissions.openSettings(Settings.Panel.ACTION_WIFI)
            RadioFix.PLAY_SERVICES -> {
                permissions.sentToSettings = true
                activity?.let(runtime::fixPlayServices)
            }
            RadioFix.TRY_AGAIN -> onRetry()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s), modifier = modifier.fillMaxWidth()) {
        message?.let { m ->
            Text(m.text.resolve(), color = if (m.problem) colors.warning else colors.text, style = textStyle)
            m.fixes.forEachIndexed { i, fix ->
                val label = stringResource(fixLabel(fix))
                if (i == 0) PrimaryButton(label, { act(fix) }, Modifier.fillMaxWidth(), height = buttonHeight)
                else SecondaryButton(label, { act(fix) }, Modifier.fillMaxWidth(), height = buttonHeight)
            }
        }
        hints.forEach { h ->
            val fix = h.fixes.first()
            Text(h.text.resolve(), color = colors.textMuted, style = Scan7Type.text12)
            SecondaryButton(
                stringResource(fixLabel(fix)),
                { act(fix) },
                height = Scan7Spacing.minTouch,
                cornerRadius = Scan7Radii.iconButton,
                textStyle = Scan7Type.text12,
            )
        }
        TechnicalDetails(role, status, stats, runtime, logVersion)
    }
}

private fun fixLabel(fix: RadioFix): Int = when (fix) {
    RadioFix.ALLOW -> R.string.action_allow
    RadioFix.APP_SETTINGS -> R.string.radio_open_app
    RadioFix.LOCATION_SETTINGS -> R.string.radio_open_location
    RadioFix.BLUETOOTH_SETTINGS -> R.string.radio_open_bluetooth
    RadioFix.WIFI_SETTINGS -> R.string.radio_open_wifi
    RadioFix.PLAY_SERVICES -> R.string.radio_update_play_services
    RadioFix.TRY_AGAIN -> R.string.action_try_again
}

/**
 * Collapsed by default, also in release builds (logcat is out of reach on a friend's phone): the
 * state, counters, permissions, switches, versions and the last radio events, plus "Copy log"
 * (clipboard only; nothing is uploaded).
 */
@Composable
private fun TechnicalDetails(role: RadioRole, status: TransportStatus?, stats: LinkStats?, runtime: ShareRuntime, logVersion: Long) {
    val colors = Scan7Theme.colors
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = !open }, colors = ButtonDefaults.textButtonColors(contentColor = colors.accent)) {
        Text(stringResource(if (open) R.string.radio_details_hide else R.string.radio_details), style = Scan7Type.text12)
    }
    if (!open) return
    val sdk = Build.VERSION.SDK_INT
    // Log time stamps: technical, to the millisecond like logcat (not a user-facing date).
    val time = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT) }
    fun input(events: List<RadioLog.Event>) = RadioDiagnosticsInput(
        role = role,
        status = status,
        stats = stats,
        permissions = (NearbyPermissions.required(sdk) + NearbyPermissions.installTime).associateWith {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        },
        settings = runtime.radioSettings(),
        sdk = sdk,
        release = Build.VERSION.RELEASE,
        device = "${Build.MANUFACTURER} ${Build.MODEL}",
        playServices = runtime.playServices(),
        appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.APPLICATION_ID}",
        events = events,
        formatTime = { time.format(Date(it)) },
    )
    val lines = remember(logVersion, status, stats) { RadioDiagnostics.lines(input(RadioLog.app.last(SHOWN_EVENTS))) }
    Column(
        verticalArrangement = Arrangement.spacedBy(Scan7Spacing.xxs),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(Scan7Radii.card))
            .border(1.dp, colors.line, RoundedCornerShape(Scan7Radii.card))
            .padding(Scan7Spacing.s),
    ) {
        // Long log lines scroll sideways instead of wrapping into an unreadable column.
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            lines.forEach { Text(it, color = colors.textMuted, style = Scan7Type.text10, softWrap = false) }
        }
    }
    val copied = stringResource(R.string.radio_log_copied)
    SecondaryButton(
        stringResource(R.string.radio_copy_log),
        {
            val all = RadioDiagnostics.lines(input(RadioLog.app.last())).joinToString("\n")
            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Scan7", all))
            // Android 13+ confirms a copy itself.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
        },
        height = Scan7Spacing.minTouch,
        cornerRadius = Scan7Radii.iconButton,
        textStyle = Scan7Type.text12,
    )
}

private const val SHOWN_EVENTS = 30
