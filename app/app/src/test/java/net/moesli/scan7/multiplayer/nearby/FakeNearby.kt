package net.moesli.scan7.multiplayer.nearby

/**
 * An in-memory stand-in for Nearby Connections: phones ([api]) advertise, discover, connect (both
 * sides must accept), exchange BYTES payloads (enforcing [maxPayloadBytes]) and drop links. Every
 * callback is queued and delivered by [deliverAll], like Nearby posting to the main thread.
 */
class FakeNearbyMedium(
    val maxPayloadBytes: Int = 1024,
    /** Runs each queued callback by itself on this executor (app-level tests); `null` = [deliverAll] only. */
    private val autoPost: ((() -> Unit) -> Unit)? = null,
) {
    private val queue = ArrayDeque<() -> Unit>()
    private val phones = mutableListOf<FakeConnectionsApi>()
    private var nextId = 1

    fun api(): FakeConnectionsApi = FakeConnectionsApi(this, "ep${nextId++}").also { phones += it }

    fun post(block: () -> Unit) {
        queue.addLast(block)
        autoPost?.invoke { queue.removeFirstOrNull()?.invoke() }
    }

    /** The phone's app process dies: its advertising and links vanish (the others hear a disconnect), it is never found again. */
    fun kill(phone: FakeConnectionsApi) {
        phone.stopAdvertising()
        phone.stopDiscovery()
        phones -= phone
        phones.forEach { other -> if (other.links.remove(phone.id) != null) post { other.connectionCallbacks[phone.id]?.onDisconnected(phone.id) } }
        phone.links.clear()
    }

    fun deliverAll(limit: Int = 100_000) {
        var n = 0
        while (queue.isNotEmpty()) {
            check(++n < limit) { "Medium did not settle" }
            queue.removeFirst().invoke()
        }
    }

    /** Radio loss between [a] and [b]: both get onDisconnected. */
    fun breakLink(a: FakeConnectionsApi, b: FakeConnectionsApi) {
        if (a.links.remove(b.id) != null) post { a.connectionCallbacks[b.id]?.onDisconnected(b.id) }
        if (b.links.remove(a.id) != null) post { b.connectionCallbacks[a.id]?.onDisconnected(a.id) }
    }

    internal fun advertisers(serviceId: String) = phones.filter { it.advertising?.serviceId == serviceId }

    internal fun discoverers(serviceId: String) = phones.filter { it.discovering?.serviceId == serviceId }

    internal fun byId(id: String) = phones.firstOrNull { it.id == id }
}

class FakeConnectionsApi internal constructor(private val medium: FakeNearbyMedium, val id: String) : ConnectionsApi {
    internal class Advertising(val name: String, val serviceId: String, val callback: ConnectionCallback)

    internal class Discovering(val serviceId: String, val callback: DiscoveryCallback)

    internal var advertising: Advertising? = null
    internal var discovering: Discovering? = null

    /** Established links: remote id → its payload callback on this side. */
    internal val links = mutableMapOf<String, PayloadCallback>()
    internal val connectionCallbacks = mutableMapOf<String, ConnectionCallback>()
    private val accepted = mutableMapOf<String, PayloadCallback>()

    /** Every call by name, for assertions. */
    val calls = mutableListOf<String>()

    /** Nearby refuses to advertise / discover (a runtime permission or the location setting is off). */
    var permissionDenied = false

    /** Payloads sent, with their sizes (chunking checks). */
    val sent = mutableListOf<ByteArray>()

    override val maxPayloadBytes: Int get() = medium.maxPayloadBytes

    val connectedTo: Set<String> get() = links.keys.toSet()

    override fun startAdvertising(localName: String, serviceId: String, callback: ConnectionCallback, done: (ApiResult) -> Unit) {
        calls += "startAdvertising"
        if (permissionDenied) return medium.post { done(ApiResult.Failed(ApiError.MISSING_PERMISSION)) }
        if (advertising != null) return medium.post { done(ApiResult.Failed(ApiError.ALREADY_ACTIVE)) }
        val adv = Advertising(localName, serviceId, callback)
        advertising = adv
        medium.post { done(ApiResult.Ok) }
        medium.discoverers(serviceId).forEach { d -> medium.post { d.discovering?.callback?.onEndpointFound(id, localName, serviceId) } }
    }

    override fun stopAdvertising() {
        calls += "stopAdvertising"
        val adv = advertising ?: return
        advertising = null
        medium.discoverers(adv.serviceId).forEach { d -> medium.post { d.discovering?.callback?.onEndpointLost(id) } }
    }

    override fun startDiscovery(serviceId: String, callback: DiscoveryCallback, done: (ApiResult) -> Unit) {
        calls += "startDiscovery"
        if (permissionDenied) return medium.post { done(ApiResult.Failed(ApiError.MISSING_PERMISSION)) }
        if (discovering != null) return medium.post { done(ApiResult.Failed(ApiError.ALREADY_ACTIVE)) }
        discovering = Discovering(serviceId, callback)
        medium.post { done(ApiResult.Ok) }
        medium.advertisers(serviceId).forEach { a ->
            val name = a.advertising!!.name
            medium.post { discovering?.callback?.onEndpointFound(a.id, name, serviceId) }
        }
    }

    override fun stopDiscovery() {
        calls += "stopDiscovery"
        discovering = null
    }

    override fun requestConnection(localName: String, endpointId: String, callback: ConnectionCallback, done: (ApiResult) -> Unit) {
        calls += "requestConnection"
        val host = medium.byId(endpointId)
        val adv = host?.advertising
        if (host == null || adv == null) return medium.post { done(ApiResult.Failed(ApiError.OTHER, message = "Endpoint unknown")) }
        connectionCallbacks[endpointId] = callback
        host.connectionCallbacks[id] = adv.callback
        medium.post { done(ApiResult.Ok) }
        medium.post { callback.onConnectionInitiated(endpointId, adv.name) }
        medium.post { adv.callback.onConnectionInitiated(id, localName) }
    }

    override fun acceptConnection(endpointId: String, payloads: PayloadCallback, done: (ApiResult) -> Unit) {
        calls += "acceptConnection"
        val other = medium.byId(endpointId) ?: return medium.post { done(ApiResult.Failed(ApiError.NOT_CONNECTED)) }
        accepted[endpointId] = payloads
        medium.post { done(ApiResult.Ok) }
        val theirs = other.accepted[id] ?: return
        links[endpointId] = payloads
        other.links[id] = theirs
        medium.post { connectionCallbacks[endpointId]?.onConnectionResult(endpointId, ApiResult.Ok) }
        medium.post { other.connectionCallbacks[id]?.onConnectionResult(id, ApiResult.Ok) }
        accepted.remove(endpointId)
        other.accepted.remove(id)
    }

    override fun rejectConnection(endpointId: String) {
        calls += "rejectConnection"
        val other = medium.byId(endpointId) ?: return
        accepted.remove(endpointId)
        other.accepted.remove(id)
        val rejected = ApiResult.Failed(ApiError.REJECTED, 8004)
        medium.post { connectionCallbacks[endpointId]?.onConnectionResult(endpointId, rejected) }
        medium.post { other.connectionCallbacks[id]?.onConnectionResult(id, rejected) }
    }

    override fun sendPayload(endpointId: String, bytes: ByteArray, done: (ApiResult) -> Unit) {
        check(bytes.size <= maxPayloadBytes) { "Payload of ${bytes.size} bytes over the limit $maxPayloadBytes" }
        val other = medium.byId(endpointId)
        val theirs = other?.links?.get(id)
        if (endpointId !in links || theirs == null) return medium.post { done(ApiResult.Failed(ApiError.NOT_CONNECTED, 8005)) }
        sent += bytes
        val copy = bytes.copyOf()
        medium.post { theirs.onBytes(id, copy) }
        medium.post { done(ApiResult.Ok) }
    }

    override fun disconnectFromEndpoint(endpointId: String) {
        calls += "disconnectFromEndpoint"
        links.remove(endpointId)
        val other = medium.byId(endpointId) ?: return
        if (other.links.remove(id) != null) medium.post { other.connectionCallbacks[id]?.onDisconnected(id) }
    }

    override fun stopAllEndpoints() {
        calls += "stopAllEndpoints"
        links.keys.toList().forEach { disconnectFromEndpoint(it) }
    }
}

/** Virtual time: [advanceBy] runs what became due, in order. */
class FakeScheduler : Scheduler {
    var now = 0L
        private set
    private val tasks = mutableListOf<Triple<Long, Int, () -> Unit>>()
    private var order = 0

    override fun schedule(delayMs: Long, block: () -> Unit): Cancellable {
        val task = Triple(now + delayMs, order++, block)
        tasks += task
        return Cancellable { tasks.remove(task) }
    }

    val pending: Int get() = tasks.size

    fun advanceBy(ms: Long, afterEach: () -> Unit = {}) {
        val end = now + ms
        while (true) {
            val next = tasks.filter { it.first <= end }.minWithOrNull(compareBy({ it.first }, { it.second })) ?: break
            tasks.remove(next)
            now = next.first
            next.third()
            afterEach()
        }
        now = end
    }
}
