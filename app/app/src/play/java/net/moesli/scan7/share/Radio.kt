package net.moesli.scan7.share

import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.common.GoogleApiAvailability
import net.moesli.scan7.multiplayer.ClientTransport
import net.moesli.scan7.multiplayer.HostTransport
import net.moesli.scan7.multiplayer.nearby.GmsConnectionsApi
import net.moesli.scan7.multiplayer.nearby.JoinLink
import net.moesli.scan7.multiplayer.nearby.NearbyClientTransport
import net.moesli.scan7.multiplayer.nearby.NearbyHostTransport
import net.moesli.scan7.multiplayer.nearby.NearbyPermissions
import net.moesli.scan7.multiplayer.nearby.RadioSetting
import net.moesli.scan7.multiplayer.nearby.Scheduler
import net.moesli.scan7.multiplayer.nearby.SerialConnectionsApi
import net.moesli.scan7.multiplayer.nearby.nearbyServiceId

/**
 * Google Nearby Connections (`play` flavor). One [GmsConnectionsApi] per process; the transports
 * are the unit-tested plain-Kotlin adapters. Before each try they check the permissions and the
 * switches ([settings]: Location for discovery, Bluetooth for both), so a switched-off setting
 * shows as such instead of "Connecting…" (docs/dev/MULTI_DEVICE.md §6.3).
 */
object Radio : RadioSupport {
    override val available: Boolean = true

    @Volatile
    private var api: GmsConnectionsApi? = null

    private fun gms(context: Context) = api ?: synchronized(this) { api ?: GmsConnectionsApi(context.applicationContext).also { api = it } }

    /**
     * Nearby's callbacks (main thread) posted to the session thread the transports run on, in order:
     * the [scheduler] runs a 0 ms task there. Directly on the main thread they raced the session
     * thread's sends, timers and joins on the same link state.
     */
    private fun api(context: Context, scheduler: Scheduler) = SerialConnectionsApi(gms(context)) { block -> scheduler.schedule(0, block) }

    /** logcat (`Scan7Nearby`) and the on-screen technical details. */
    private fun log(message: String) {
        Log.i("Scan7Nearby", message)
        RadioLog.app.add("link", message)
    }

    override fun missingPermissions(context: Context): List<String> = NearbyPermissions.missing(Build.VERSION.SDK_INT) {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun blocking(context: Context, discovering: Boolean): RadioSetting? {
        val s = settings(context)
        return NearbyPermissions.blockingSetting(discovering, locationOn = s.locationOn != false, bluetoothOn = s.bluetoothOn != false)
    }

    override fun host(context: Context, link: JoinLink, scheduler: Scheduler): RadioLink<HostTransport> {
        log("host session ${link.sessionId}")
        val t = NearbyHostTransport(
            api(context, scheduler), nearbyServiceId(context.packageName), link.sessionId, link.token, scheduler,
            missingPermissions = { missingPermissions(context) },
            blockingSetting = { blocking(context, discovering = false) },
            log = ::log,
        )
        return RadioLink(LoggingHostTransport(t), t.status, t.stats)
    }

    override fun join(context: Context, link: JoinLink, scheduler: Scheduler): RadioLink<ClientTransport> {
        log("join session ${link.sessionId}")
        val t = NearbyClientTransport(
            api(context, scheduler), nearbyServiceId(context.packageName), link, scheduler,
            missingPermissions = { missingPermissions(context) },
            log = ::log,
            blockingSetting = { blocking(context, discovering = true) },
        )
        return RadioLink(LoggingClientTransport(t), t.status, t.stats)
    }

    override fun sessionActive(context: Context, active: Boolean) {
        val intent = Intent(context, ShareService::class.java)
        if (active) ContextCompat.startForegroundService(context, intent) else context.stopService(intent)
    }

    override fun settings(context: Context): RadioSettings {
        val location = context.getSystemService(LocationManager::class.java)?.let(LocationManagerCompat::isLocationEnabled)
        // isEnabled needs no permission; a phone without Bluetooth (or a SecurityException on an odd build) = unknown.
        val bluetooth = runCatching { context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled }.getOrNull()
        val wifi = runCatching { context.applicationContext.getSystemService(WifiManager::class.java)?.isWifiEnabled }.getOrNull()
        return RadioSettings(location, bluetooth, wifi)
    }

    override fun playServices(context: Context): PlayServicesInfo {
        val availability = GoogleApiAvailability.getInstance()
        val result = availability.isGooglePlayServicesAvailable(context)
        val version = runCatching {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(GoogleApiAvailability.GOOGLE_PLAY_SERVICES_PACKAGE, 0)
            "${info.versionName} (${androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info)})"
        }.getOrNull()
        return PlayServicesInfo(version, result, availability.getErrorString(result), availability.isUserResolvableError(result))
    }

    override fun fixPlayServices(activity: Activity) {
        GoogleApiAvailability.getInstance().makeGooglePlayServicesAvailable(activity)
    }
}
