package net.moesli.scan7.share

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import net.moesli.scan7.multiplayer.ClientTransport
import net.moesli.scan7.multiplayer.HostTransport
import net.moesli.scan7.multiplayer.nearby.GmsConnectionsApi
import net.moesli.scan7.multiplayer.nearby.JoinLink
import net.moesli.scan7.multiplayer.nearby.NearbyClientTransport
import net.moesli.scan7.multiplayer.nearby.NearbyHostTransport
import net.moesli.scan7.multiplayer.nearby.NearbyPermissions
import net.moesli.scan7.multiplayer.nearby.Scheduler
import net.moesli.scan7.multiplayer.nearby.SerialConnectionsApi
import net.moesli.scan7.multiplayer.nearby.nearbyServiceId

/**
 * Google Nearby Connections (`play` flavor). One [GmsConnectionsApi] per process; the transports
 * are the unit-tested plain-Kotlin adapters. Not run on a device yet (M4 field test).
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

    private fun log(message: String) = Log.i("Scan7Nearby", message)

    override fun missingPermissions(context: Context): List<String> = NearbyPermissions.missing(Build.VERSION.SDK_INT) {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun host(context: Context, link: JoinLink, scheduler: Scheduler): RadioLink<HostTransport> {
        Log.i("Scan7Nearby", "host session ${link.sessionId}")
        val t = NearbyHostTransport(
            api(context, scheduler), nearbyServiceId(context.packageName), link.sessionId, link.token, scheduler,
            missingPermissions = { missingPermissions(context) },
        )
        return RadioLink(LoggingHostTransport(t), t.status)
    }

    override fun join(context: Context, link: JoinLink, scheduler: Scheduler): RadioLink<ClientTransport> {
        Log.i("Scan7Nearby", "join session ${link.sessionId}")
        val t = NearbyClientTransport(
            api(context, scheduler), nearbyServiceId(context.packageName), link, scheduler,
            missingPermissions = { missingPermissions(context) },
            log = ::log,
        )
        return RadioLink(LoggingClientTransport(t), t.status)
    }

    override fun sessionActive(context: Context, active: Boolean) {
        val intent = Intent(context, ShareService::class.java)
        if (active) ContextCompat.startForegroundService(context, intent) else context.stopService(intent)
    }
}
