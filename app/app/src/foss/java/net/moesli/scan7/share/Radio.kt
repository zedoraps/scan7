package net.moesli.scan7.share

import android.content.Context
import net.moesli.scan7.multiplayer.ClientTransport
import net.moesli.scan7.multiplayer.HostTransport
import net.moesli.scan7.multiplayer.nearby.JoinLink
import net.moesli.scan7.multiplayer.nearby.Scheduler

/**
 * No radio: the `foss` flavor and the flavorless default build ship without Google Play services,
 * so phones can't connect (multiplayer only runs over the debug in-process network there).
 */
object Radio : RadioSupport {
    override val available: Boolean = false

    override fun missingPermissions(context: Context): List<String> = emptyList()

    override fun host(context: Context, link: JoinLink, scheduler: Scheduler): RadioLink<HostTransport>? = null

    override fun join(context: Context, link: JoinLink, scheduler: Scheduler): RadioLink<ClientTransport>? = null

    override fun sessionActive(context: Context, active: Boolean) = Unit
}
