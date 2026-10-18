package net.moesli.scan7.multiplayer

import kotlin.random.Random

/**
 * In-memory star network for JVM tests and an in-process demo (no radios). Frames are queued and
 * only move when the test calls [deliverNext] / [deliverAll]: per link and direction they stay in
 * order (like Nearby), but different links interleave in a [random] order, which simulates
 * phones acting at the same time.
 */
class FakeNetwork(private val random: Random = Random(0)) {
    private var nextLink = 1
    private val links = mutableMapOf<PeerId, Link>()
    private val hostListeners = mutableListOf<HostTransport.Listener>()

    /** Pending deliveries per (link, direction); each queue is FIFO. */
    private val queues = linkedMapOf<Pair<PeerId, Boolean>, ArrayDeque<() -> Unit>>()

    /** Frames delivered so far (for size checks in tests). */
    val delivered = mutableListOf<ByteArray>()

    /** The host's transport (one per network). */
    val host: HostTransport = object : HostTransport {
        override fun start(listener: HostTransport.Listener) {
            hostListeners += listener
        }

        override fun send(peer: PeerId, frame: ByteArray) {
            val link = links[peer] ?: return
            enqueue(peer, toHost = false) { if (link.open) link.client.receive(frame) }
        }

        /** Graceful: frames already sent to [peer] arrive first (e.g. a [HelloRejected]). */
        override fun disconnect(peer: PeerId) {
            if (links[peer]?.open == true) enqueue(peer, toHost = false) { drop(peer) }
        }
    }

    /** A new phone connecting; its link opens once the connect events are delivered. */
    fun connect(): FakeClientTransport = FakeClientTransport(this)

    /** Peers with an open link. */
    val openPeers: Set<PeerId> get() = links.filterValues { it.open }.keys

    /** Breaks [peer]'s link at once (radio gone): frames still queued on it are lost, both sides hear "disconnected". */
    fun drop(peer: PeerId) {
        val link = links[peer] ?: return
        if (!link.open) return
        link.open = false
        queues.remove(peer to true)
        queues.remove(peer to false)
        link.client.listener?.onDisconnected()
        hostListeners.forEach { it.onPeerDisconnected(peer) }
    }

    /** The host process dies: every link breaks and the old session stops listening (start a new one on [host]). */
    fun killHost() {
        links.keys.toList().forEach(::drop)
        hostListeners.clear()
    }

    /** Loses the next frame queued from the host to [peer] (to provoke a seq gap). */
    fun loseNextToClient(peer: PeerId) {
        queues[peer to false]?.removeFirstOrNull()
    }

    /** Delivers one queued frame from a random link; `false` when nothing is queued. */
    fun deliverNext(): Boolean {
        val busy = queues.filterValues { it.isNotEmpty() }.keys.toList()
        if (busy.isEmpty()) return false
        val key = busy[random.nextInt(busy.size)]
        queues.getValue(key).removeFirst().invoke()
        return true
    }

    /** Delivers until the network is quiet (also what the deliveries sent in reply). */
    fun deliverAll(limit: Int = 100_000) {
        var n = 0
        while (deliverNext()) check(++n < limit) { "Network did not settle" }
    }

    internal fun open(client: FakeClientTransport): PeerId {
        val peer = PeerId("peer${nextLink++}")
        links[peer] = Link(client)
        client.peer = peer
        hostListeners.forEach { it.onPeerConnected(peer) }
        client.listener?.onConnected()
        return peer
    }

    internal fun sendToHost(peer: PeerId, frame: ByteArray) {
        val link = links[peer] ?: return
        if (!link.open) return
        enqueue(peer, toHost = true) {
            if (link.open) {
                delivered += frame
                hostListeners.forEach { it.onFrame(peer, frame) }
            }
        }
    }

    internal fun closeFromClient(peer: PeerId) {
        if (links[peer]?.open == true) enqueue(peer, toHost = true) { drop(peer) }
    }

    internal fun recordDelivered(frame: ByteArray) {
        delivered += frame
    }

    private fun enqueue(peer: PeerId, toHost: Boolean, delivery: () -> Unit) {
        queues.getOrPut(peer to toHost) { ArrayDeque() }.addLast(delivery)
    }

    private class Link(val client: FakeClientTransport) {
        var open = true
    }
}

/** One phone's link in a [FakeNetwork]. Connects when started; after a drop, use a new one from [FakeNetwork.connect]. */
class FakeClientTransport internal constructor(private val network: FakeNetwork) : ClientTransport {
    internal var listener: ClientTransport.Listener? = null

    /** The host's id for this link, once started. */
    var peer: PeerId? = null
        internal set

    override fun start(listener: ClientTransport.Listener) {
        this.listener = listener
        network.open(this)
    }

    override fun send(frame: ByteArray) {
        peer?.let { network.sendToHost(it, frame) }
    }

    /** Graceful: frames already sent (e.g. [Leave]) reach the host first. Use [FakeNetwork.drop] for a radio loss. */
    override fun disconnect() {
        peer?.let(network::closeFromClient)
    }

    internal fun receive(frame: ByteArray) {
        network.recordDelivered(frame)
        listener?.onFrame(frame)
    }
}
