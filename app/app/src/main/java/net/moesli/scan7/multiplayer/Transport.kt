package net.moesli.scan7.multiplayer

/** A connected device as the transport names it (a Nearby endpoint id, a socket, …). Not stable across reconnects. */
@JvmInline
value class PeerId(val value: String)

/**
 * Hub side of a star transport (Nearby `P2P_STAR` host, or a LAN server). Implementations deliver
 * every peer's frames **in order** and call the listener from one thread (the session is not
 * thread-safe; the Android implementation posts to a single-threaded dispatcher).
 */
interface HostTransport {
    fun start(listener: Listener)

    fun send(peer: PeerId, frame: ByteArray)

    /** Closes the link to [peer]; [Listener.onPeerDisconnected] follows. */
    fun disconnect(peer: PeerId)

    interface Listener {
        fun onPeerConnected(peer: PeerId)

        fun onFrame(peer: PeerId, frame: ByteArray)

        fun onPeerDisconnected(peer: PeerId)
    }
}

/** Spoke side: one link to the host. A reconnect is a new [ClientTransport] (see [ClientSession.attach]). */
interface ClientTransport {
    fun start(listener: Listener)

    fun send(frame: ByteArray)

    fun disconnect()

    interface Listener {
        fun onConnected()

        fun onFrame(frame: ByteArray)

        fun onDisconnected()
    }
}
