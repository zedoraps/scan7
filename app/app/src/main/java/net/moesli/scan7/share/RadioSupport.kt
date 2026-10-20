package net.moesli.scan7.share

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.moesli.scan7.multiplayer.ClientTransport
import net.moesli.scan7.multiplayer.HostTransport
import net.moesli.scan7.multiplayer.nearby.JoinLink
import net.moesli.scan7.multiplayer.nearby.LinkStats
import net.moesli.scan7.multiplayer.nearby.Scheduler
import net.moesli.scan7.multiplayer.nearby.TransportStatus

/** A transport plus what the share UI shows about its radio link. */
class RadioLink<T>(
    val transport: T,
    val status: StateFlow<TransportStatus>,
    val stats: StateFlow<LinkStats> = MutableStateFlow(LinkStats()),
)

/** The phone's switches the radio depends on; `null` = can't tell (treated as on). */
data class RadioSettings(val locationOn: Boolean?, val bluetoothOn: Boolean?, val wifiOn: Boolean?)

/** Google Play services as `GoogleApiAvailability` sees it ([result] 0 = SUCCESS). */
data class PlayServicesInfo(val version: String?, val result: Int, val resultName: String, val resolvable: Boolean) {
    val ok: Boolean get() = result == 0
}

/**
 * Phone-to-phone radio for multiplayer (docs/dev/MULTI_DEVICE.md §6). The `play` flavor implements it
 * with Google Nearby Connections (`Radio` in src/play); the `foss` flavor and the flavorless
 * default build have none (`Radio` in src/foss: [available] false, Share / Join need the debug
 * in-process network). Calls come from the session thread; [sessionActive] starts / stops the
 * foreground service that keeps the link alive while the screen is off.
 */
interface RadioSupport {
    val available: Boolean

    /** Runtime permissions still missing on this phone (requested on Share / Join). */
    fun missingPermissions(context: Context): List<String>

    fun host(context: Context, link: JoinLink, scheduler: Scheduler): RadioLink<HostTransport>?

    fun join(context: Context, link: JoinLink, scheduler: Scheduler): RadioLink<ClientTransport>?

    fun sessionActive(context: Context, active: Boolean)

    /** Location / Bluetooth / Wi-Fi on or off (the transports check them before each try); `null` without a radio. */
    fun settings(context: Context): RadioSettings? = null

    /** `null` without Play services in the build (foss). */
    fun playServices(context: Context): PlayServicesInfo? = null

    /** Play's own "update / enable Google Play services" flow. */
    fun fixPlayServices(activity: Activity) = Unit
}
