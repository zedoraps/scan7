package net.moesli.scan7.share

import net.moesli.scan7.R
import net.moesli.scan7.multiplayer.nearby.ApiError
import net.moesli.scan7.multiplayer.nearby.LinkStats
import net.moesli.scan7.multiplayer.nearby.NearbyCodes
import net.moesli.scan7.multiplayer.nearby.NearbyPermissions
import net.moesli.scan7.multiplayer.nearby.RadioSetting
import net.moesli.scan7.multiplayer.nearby.TransportStatus
import net.moesli.scan7.ui.text.UiText
import net.moesli.scan7.ui.text.uiText

/** Which side of the radio this phone is on. */
enum class RadioRole { HOST, GUEST }

/** What the button under a radio message does. */
enum class RadioFix {
    /** Ask for the permissions again (system dialog, or the app settings when denied for good). */
    ALLOW,
    APP_SETTINGS,
    LOCATION_SETTINGS,
    BLUETOOTH_SETTINGS,
    WIFI_SETTINGS,
    PLAY_SERVICES,
    TRY_AGAIN,
}

/**
 * One line about the radio on the Join screen / share sheet, with its buttons ([fixes], first =
 * primary). [problem]: shown in the warning colour (the user has to do something).
 */
data class RadioMessage(val text: UiText, val fixes: List<RadioFix> = emptyList(), val problem: Boolean = false)

/**
 * Text for the radio states (docs/dev/MULTI_DEVICE.md §6.3), plain Kotlin and JVM-tested. Every
 * state the user can fix names the fix and offers the right settings; Try again starts one fresh
 * try (nothing retries in a loop behind a permission or setting message any more).
 */
object RadioText {
    /** The main line; `null` = nothing to say (the host is advertising, the guest is linked). */
    fun status(role: RadioRole, status: TransportStatus?, playServices: PlayServicesInfo?): RadioMessage? {
        // Play services missing / too old: every Nearby call fails, whatever the transport says.
        if (playServices != null && !playServices.ok) {
            return RadioMessage(
                uiText(R.string.radio_play_services, playServices.resultName),
                if (playServices.resolvable) listOf(RadioFix.PLAY_SERVICES, RadioFix.TRY_AGAIN) else listOf(RadioFix.TRY_AGAIN),
                problem = true,
            )
        }
        return when (status) {
            null, TransportStatus.Idle -> if (role == RadioRole.GUEST) RadioMessage(uiText(R.string.join_connecting)) else null
            TransportStatus.Advertising, is TransportStatus.Connected, TransportStatus.Stopped -> null
            TransportStatus.Discovering -> RadioMessage(uiText(R.string.radio_looking))
            TransportStatus.NotFound -> RadioMessage(uiText(R.string.radio_not_found), listOf(RadioFix.TRY_AGAIN))
            is TransportStatus.Connecting -> RadioMessage(uiText(R.string.radio_found))
            is TransportStatus.Reconnecting -> RadioMessage(uiText(if (role == RadioRole.HOST) R.string.radio_host_trying_again else R.string.radio_trying_again))
            is TransportStatus.MissingPermissions -> RadioMessage(
                uiText(R.string.radio_permissions_named, permissionNames(status.permissions)),
                listOf(RadioFix.ALLOW),
                problem = true,
            )
            is TransportStatus.NeedsSetting -> when (status.setting) {
                RadioSetting.LOCATION -> RadioMessage(uiText(R.string.radio_location_off), listOf(RadioFix.LOCATION_SETTINGS), problem = true)
                RadioSetting.BLUETOOTH -> RadioMessage(uiText(R.string.radio_bluetooth_off), listOf(RadioFix.BLUETOOTH_SETTINGS), problem = true)
            }
            is TransportStatus.NearbyRefused -> RadioMessage(
                uiText(R.string.radio_nearby_refused, status.permission?.substringAfterLast('.') ?: "?", codeText(status.code)),
                listOf(RadioFix.APP_SETTINGS, RadioFix.TRY_AGAIN),
                problem = true,
            )
            is TransportStatus.Failed -> when (status.error) {
                ApiError.REJECTED -> RadioMessage(uiText(R.string.radio_rejected), listOf(RadioFix.TRY_AGAIN), problem = true)
                else -> RadioMessage(
                    uiText(R.string.radio_failed_code, codeText(status.code)),
                    listOf(RadioFix.TRY_AGAIN),
                    problem = true,
                )
            }
        }
    }

    /** Wi-Fi off: a hint only (Bluetooth works without it, just slower). */
    fun wifiHint(settings: RadioSettings?): RadioMessage? =
        if (settings?.wifiOn == false) RadioMessage(uiText(R.string.radio_wifi_off), listOf(RadioFix.WIFI_SETTINGS)) else null

    /** "Nearby devices and Location" (the groups Android shows in the app settings). */
    fun permissionNames(permissions: List<String>): UiText {
        val location = permissions.any { it == NearbyPermissions.ACCESS_FINE_LOCATION || it == NearbyPermissions.ACCESS_COARSE_LOCATION }
        val nearby = permissions.any { !(it == NearbyPermissions.ACCESS_FINE_LOCATION || it == NearbyPermissions.ACCESS_COARSE_LOCATION) }
        return when {
            location && nearby -> uiText(R.string.radio_permission_both)
            location -> uiText(R.string.radio_permission_location)
            else -> uiText(R.string.radio_permission_nearby)
        }
    }

    /** "8036 MISSING_PERMISSION_ACCESS_FINE_LOCATION" (technical; untranslated). */
    fun codeText(code: Int): String = if (code == 0) "-" else "$code ${NearbyCodes.name(code)}"
}

/** Everything the "Technical details" list shows; built by the screen, formatted by [RadioDiagnostics.lines]. */
data class RadioDiagnosticsInput(
    val role: RadioRole,
    val status: TransportStatus?,
    val stats: LinkStats?,
    /** Each required permission → granted. */
    val permissions: Map<String, Boolean>,
    val settings: RadioSettings?,
    val sdk: Int,
    val release: String,
    val device: String,
    val playServices: PlayServicesInfo?,
    val appVersion: String,
    val events: List<RadioLog.Event>,
    val formatTime: (Long) -> String,
)

/**
 * The technical lines (deliberately English and terse: they get copied into bug reports and read
 * by the developer, like logcat). No player names: the log carries frame types only.
 */
object RadioDiagnostics {
    fun lines(d: RadioDiagnosticsInput): List<String> = buildList {
        add("role: ${d.role.name.lowercase()} · state: ${describe(d.status)}")
        d.stats?.let { s ->
            add("tries: ${s.starts} · requests: ${s.requests} · failures in a row: ${s.failuresInARow}")
            s.lastCode?.let { add("last error: ${RadioText.codeText(it)} (${s.lastCall})") }
            s.nearbyMissing?.let { add("Nearby says missing: ${it.substringAfterLast('.')}") }
        }
        d.permissions.forEach { (p, granted) -> add("${if (granted) "✓" else "✗"} ${p.substringAfterLast('.')}") }
        d.settings?.let { add("location: ${onOff(it.locationOn)} · bluetooth: ${onOff(it.bluetoothOn)} · wi-fi: ${onOff(it.wifiOn)}") }
        add("Android ${d.release} (API ${d.sdk}) · ${d.device}")
        add(
            d.playServices?.let { "Play services: ${it.version ?: "?"} · ${it.resultName}" } ?: "Play services: not in this build",
        )
        add("Scan7 ${d.appVersion}")
        if (d.events.isNotEmpty()) add("--- last ${d.events.size} events")
        d.events.forEach { add("${d.formatTime(it.atMs)} ${it.tag}: ${it.message}") }
    }

    fun describe(status: TransportStatus?): String = when (status) {
        null -> "none"
        is TransportStatus.MissingPermissions -> "missing permissions ${status.permissions.joinToString { it.substringAfterLast('.') }}" + code(status.code)
        is TransportStatus.NeedsSetting -> "${status.setting.name.lowercase()} off" + code(status.code)
        is TransportStatus.NearbyRefused -> "Nearby refuses (${status.permission?.substringAfterLast('.') ?: "?"})" + code(status.code)
        is TransportStatus.Failed -> "failed ${status.error.name.lowercase()}" + code(status.code)
        is TransportStatus.Reconnecting -> "retry in ${status.retryInMs / 1000} s"
        is TransportStatus.Connecting -> "connecting ${status.endpointId}"
        is TransportStatus.Connected -> "connected ${status.endpointId}"
        else -> status.toString().lowercase()
    }

    private fun code(c: Int) = if (c == 0) "" else " · ${RadioText.codeText(c)}"

    private fun onOff(v: Boolean?) = when (v) {
        true -> "on"
        false -> "OFF"
        null -> "?"
    }
}
