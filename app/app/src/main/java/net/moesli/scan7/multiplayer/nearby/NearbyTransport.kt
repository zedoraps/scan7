package net.moesli.scan7.multiplayer.nearby

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.moesli.scan7.multiplayer.ClientTransport
import net.moesli.scan7.multiplayer.HostTransport
import net.moesli.scan7.multiplayer.PeerId

/**
 * Timers for retries and delayed disconnects, run on the transports' thread (the app: a coroutine
 * on the share runtime's session thread, where a 0 ms task also posts Nearby's callbacks; tests:
 * virtual time).
 */
fun interface Scheduler {
    fun schedule(delayMs: Long, block: () -> Unit): Cancellable
}

fun interface Cancellable {
    fun cancel()
}

/** What the share sheet / join screen shows about the radio link. */
sealed interface TransportStatus {
    data object Idle : TransportStatus

    data object Advertising : TransportStatus

    data object Discovering : TransportStatus

    data class Connecting(val endpointId: String) : TransportStatus

    data class Connected(val endpointId: String) : TransportStatus

    /** Lost the host; [retryInMs] until the next attempt. */
    data class Reconnecting(val retryInMs: Long) : TransportStatus

    /** Permissions missing: [permissions] (empty when Nearby itself reported it, e.g. location off). */
    data class MissingPermissions(val permissions: List<String>) : TransportStatus

    data class Failed(val error: ApiError, val message: String? = null) : TransportStatus

    data object Stopped : TransportStatus
}

/** Nearby service id: one per app id, so debug (`.b`, …) and release builds never see each other. */
fun nearbyServiceId(applicationId: String) = "$applicationId.mp"

/**
 * Host side over Nearby Connections, `P2P_STAR` hub (docs/dev/MULTI_DEVICE.md §6.1). Advertises
 * `s7|<sessionId>`, accepts only connection requests that prove the QR token ([JoinAuth]), and
 * chunks frames above the BYTES limit ([FrameChunker]). [missingPermissions] is checked before
 * advertising starts.
 */
class NearbyHostTransport(
    private val api: ConnectionsApi,
    private val serviceId: String,
    private val sessionId: String,
    private val token: String,
    private val scheduler: Scheduler,
    private val missingPermissions: () -> List<String> = { emptyList() },
    /** Time for queued payloads (e.g. `hello_rejected`) to leave before a disconnect. */
    private val flushDelayMs: Long = FLUSH_DELAY_MS,
) : HostTransport {
    private val _status = MutableStateFlow<TransportStatus>(TransportStatus.Idle)
    val status: StateFlow<TransportStatus> = _status.asStateFlow()

    private var listener: HostTransport.Listener? = null
    private val chunker = FrameChunker(api.maxPayloadBytes)
    private val pending = mutableSetOf<String>()
    private val connected = mutableSetOf<String>()

    /** Connection requests turned away for a wrong / missing token (debug screen). */
    var rejectedRequests = 0
        private set

    val endpoints: Set<String> get() = connected.toSet()

    override fun start(listener: HostTransport.Listener) {
        this.listener = listener
        val missing = missingPermissions()
        if (missing.isNotEmpty()) {
            _status.value = TransportStatus.MissingPermissions(missing)
            return
        }
        api.startAdvertising(JoinAuth.advertisedName(sessionId), serviceId, connectionCallback) { result ->
            _status.value = when (result) {
                ApiResult.Ok -> TransportStatus.Advertising
                is ApiResult.Failed -> result.toStatus()
            }
        }
    }

    override fun send(peer: PeerId, frame: ByteArray) {
        val endpoint = peer.value
        if (endpoint !in connected) return
        chunker.split(frame).forEach { chunk ->
            api.sendPayload(endpoint, chunk) { result ->
                if (result is ApiResult.Failed && result.error == ApiError.NOT_CONNECTED) lost(endpoint)
            }
        }
    }

    /** Graceful: waits [flushDelayMs] so frames sent just before (a rejection) still arrive. */
    override fun disconnect(peer: PeerId) {
        val endpoint = peer.value
        if (endpoint !in connected) return
        scheduler.schedule(flushDelayMs) {
            if (endpoint in connected) {
                api.disconnectFromEndpoint(endpoint)
                lost(endpoint) // Nearby doesn't call onDisconnected for our own disconnect
            }
        }
    }

    /** Stop sharing: no more advertising, every link closed. */
    override fun stop() {
        api.stopAdvertising()
        api.stopAllEndpoints()
        connected.toList().forEach(::lost)
        pending.clear()
        _status.value = TransportStatus.Stopped
    }

    private fun lost(endpoint: String) {
        chunker.reset(endpoint)
        if (connected.remove(endpoint)) listener?.onPeerDisconnected(PeerId(endpoint))
    }

    private val payloads = PayloadCallback { endpoint, bytes ->
        if (endpoint in connected) chunker.accept(endpoint, bytes)?.let { listener?.onFrame(PeerId(endpoint), it) }
    }

    private val connectionCallback = object : ConnectionCallback {
        override fun onConnectionInitiated(endpointId: String, remoteName: String) {
            if (!JoinAuth.verify(remoteName, sessionId, token)) {
                rejectedRequests++
                api.rejectConnection(endpointId)
                return
            }
            pending += endpointId
            api.acceptConnection(endpointId, payloads) { result ->
                if (result is ApiResult.Failed) pending -= endpointId
            }
        }

        override fun onConnectionResult(endpointId: String, result: ApiResult) {
            if (!pending.remove(endpointId)) return
            if (result == ApiResult.Ok && connected.add(endpointId)) listener?.onPeerConnected(PeerId(endpointId))
        }

        override fun onDisconnected(endpointId: String) {
            pending -= endpointId
            lost(endpointId)
        }
    }

    companion object {
        const val FLUSH_DELAY_MS = 300L
    }
}

/**
 * Guest side over Nearby Connections, `P2P_STAR` spoke. Discovers the host of [link]'s session
 * (ignores other Scan7 sessions), requests the connection with the token proof, and keeps the link:
 * after a drop it reports `onDisconnected`, rediscovers with backoff and reports `onConnected`
 * again (the [net.moesli.scan7.multiplayer.ClientSession] then sends a fresh `hello`). [disconnect]
 * ends it for good.
 *
 * Every connection attempt has its own callbacks ([Attempt]). On this side the host has **one
 * endpoint id for all links**, so a late callback of an earlier link (its `onDisconnected` once the
 * host closed it after a rejoin, a failed send) names the current link's id too. Taken for the loss
 * of the current link, it made the guest link again, the host then closed the link before, and so
 * on (field test: a new link on every scan). Only the current attempt may end the link.
 */
class NearbyClientTransport(
    private val api: ConnectionsApi,
    private val serviceId: String,
    private val link: JoinLink,
    private val scheduler: Scheduler,
    private val missingPermissions: () -> List<String> = { emptyList() },
    private val backoffMs: List<Long> = DEFAULT_BACKOFF_MS,
    private val requestName: () -> String = { JoinAuth.requestName(link) },
    private val flushDelayMs: Long = NearbyHostTransport.FLUSH_DELAY_MS,
    /** A request the host never answers (it went out of range mid-handshake) is given up after this. */
    private val connectTimeoutMs: Long = CONNECT_TIMEOUT_MS,
    /** Field-test trail: why the link dropped, which late callbacks were ignored. */
    private val log: (String) -> Unit = {},
) : ClientTransport {
    private val _status = MutableStateFlow<TransportStatus>(TransportStatus.Idle)
    val status: StateFlow<TransportStatus> = _status.asStateFlow()

    private var listener: ClientTransport.Listener? = null
    private val chunker = FrameChunker(api.maxPayloadBytes)
    private var endpoint: String? = null
    private var connecting: String? = null
    private var discovering = false
    private var closed = false
    private var attempt = 0
    private var retry: Cancellable? = null
    private var connectTimer: Cancellable? = null

    /** The attempt that owns [connecting] / [endpoint] (`null` between attempts); callbacks of any other are ignored. */
    private var current: Attempt? = null
    private var attempts = 0

    override fun start(listener: ClientTransport.Listener) {
        this.listener = listener
        discover()
    }

    override fun send(frame: ByteArray) {
        val ep = endpoint ?: return
        val owner = current
        chunker.split(frame).forEach { chunk ->
            api.sendPayload(ep, chunk) { result ->
                if (result is ApiResult.Failed && result.error == ApiError.NOT_CONNECTED && owner != null && owner === current) {
                    // Nearby says the link is gone: close our side too, so no half-open link lingers.
                    log("send to $ep failed: not connected (attempt ${owner.number})")
                    api.disconnectFromEndpoint(ep)
                    dropped(ep)
                }
            }
        }
    }

    /** Leave: after [flushDelayMs] (so a `leave` frame gets out), close the link and stop retrying. */
    override fun disconnect() {
        if (closed) return
        closed = true
        retry?.cancel()
        connectTimer?.cancel()
        stopDiscovery()
        val ep = endpoint ?: connecting
        _status.value = TransportStatus.Stopped
        if (ep != null) scheduler.schedule(flushDelayMs) { api.disconnectFromEndpoint(ep) }
        endpoint = null
        connecting = null
        current = null
    }

    private fun discover() {
        // Never while linked or mid-handshake: a second request would open a second link to the host.
        if (closed || endpoint != null || connecting != null) return
        val missing = missingPermissions()
        if (missing.isNotEmpty()) {
            _status.value = TransportStatus.MissingPermissions(missing)
            return
        }
        _status.value = TransportStatus.Discovering
        discovering = true
        api.startDiscovery(serviceId, discoveryCallback) { result ->
            when {
                result == ApiResult.Ok -> Unit
                result is ApiResult.Failed && result.error == ApiError.ALREADY_ACTIVE -> Unit
                result is ApiResult.Failed && result.error == ApiError.MISSING_PERMISSION -> {
                    discovering = false
                    _status.value = TransportStatus.MissingPermissions(emptyList())
                }
                result is ApiResult.Failed -> {
                    discovering = false
                    // A late failure must not knock over a link found in the meantime.
                    if (endpoint == null && connecting == null) scheduleRetry()
                }
            }
        }
    }

    private fun stopDiscovery() {
        if (discovering) api.stopDiscovery()
        discovering = false
    }

    private fun scheduleRetry() {
        if (closed) return
        val delay = backoffMs[minOf(attempt, backoffMs.lastIndex)]
        attempt++
        _status.value = TransportStatus.Reconnecting(delay)
        retry?.cancel()
        retry = scheduler.schedule(delay) { discover() }
    }

    /** The current link (or handshake) to [ep] is gone: tell the session and look for the host again. */
    private fun dropped(ep: String) {
        chunker.reset(ep)
        val wasLinked = endpoint == ep
        if (!wasLinked && connecting != ep) return // not ours (any more): nothing to retry
        current = null
        if (wasLinked) {
            endpoint = null
            listener?.onDisconnected()
        }
        if (connecting == ep) {
            connecting = null
            connectTimer?.cancel()
        }
        scheduleRetry()
    }

    private val discoveryCallback = object : DiscoveryCallback {
        override fun onEndpointFound(endpointId: String, name: String, serviceId: String) {
            if (closed || endpoint != null || connecting != null) return
            if (serviceId != this@NearbyClientTransport.serviceId || !JoinAuth.matches(name, link.sessionId)) return
            stopDiscovery()
            val a = Attempt(++attempts)
            current = a
            connecting = endpointId
            _status.value = TransportStatus.Connecting(endpointId)
            // Field test: the host lost Wi-Fi mid-request, no result ever came, and `connecting` then
            // made the guest ignore the host it found again ("Connecting…" forever).
            connectTimer?.cancel()
            connectTimer = scheduler.schedule(connectTimeoutMs) {
                if (current === a && connecting == endpointId && endpoint == null && !closed) {
                    log("request to $endpointId timed out (attempt ${a.number})")
                    current = null
                    connecting = null
                    api.disconnectFromEndpoint(endpointId)
                    scheduleRetry()
                }
            }
            api.requestConnection(requestName(), endpointId, a) { result ->
                if (result is ApiResult.Failed && current === a && connecting == endpointId) {
                    current = null
                    connecting = null
                    connectTimer?.cancel()
                    if (result.error == ApiError.MISSING_PERMISSION) {
                        _status.value = TransportStatus.MissingPermissions(emptyList())
                    } else {
                        scheduleRetry()
                    }
                }
            }
        }

        override fun onEndpointLost(endpointId: String) {
            // The host vanished before we got connected: look again (a connected link reports onDisconnected instead).
            if (connecting == endpointId && endpoint == null) {
                current = null
                connecting = null
                connectTimer?.cancel()
                scheduleRetry()
            }
        }
    }

    /** One connection attempt: its lifecycle and payload callbacks count only while it is [current]. */
    private inner class Attempt(val number: Int) : ConnectionCallback {
        val payloads = PayloadCallback { ep, bytes ->
            if (current === this && ep == endpoint) chunker.accept(ep, bytes)?.let { listener?.onFrame(it) }
        }

        override fun onConnectionInitiated(endpointId: String, remoteName: String) {
            if (current !== this) {
                log("ignored a late connectionInitiated of attempt $number ($endpointId)")
                return
            }
            if (endpointId != connecting || !JoinAuth.matches(remoteName, link.sessionId)) {
                api.rejectConnection(endpointId)
                return
            }
            api.acceptConnection(endpointId, payloads) { }
        }

        override fun onConnectionResult(endpointId: String, result: ApiResult) {
            if (current !== this || endpointId != connecting) {
                log("ignored a late connectionResult of attempt $number ($endpointId)")
                return
            }
            connecting = null
            connectTimer?.cancel()
            if (result == ApiResult.Ok && !closed) {
                endpoint = endpointId
                attempt = 0
                retry?.cancel()
                _status.value = TransportStatus.Connected(endpointId)
                listener?.onConnected()
            } else if (result is ApiResult.Failed && result.error == ApiError.REJECTED) {
                current = null
                // Wrong token / removed: the host won't take us; don't hammer it.
                _status.value = TransportStatus.Failed(ApiError.REJECTED, result.message)
            } else {
                current = null
                scheduleRetry()
            }
        }

        override fun onDisconnected(endpointId: String) {
            if (current !== this) {
                // An earlier link (e.g. the one the host closed after we rejoined), not the current one.
                log("ignored a late disconnect of attempt $number ($endpointId)")
                return
            }
            log("link to $endpointId lost (attempt $number)")
            dropped(endpointId)
        }
    }

    companion object {
        val DEFAULT_BACKOFF_MS = listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L)

        /** Bluetooth handshakes took ~11 s in the field test: well above that. */
        const val CONNECT_TIMEOUT_MS = 30_000L
    }
}

private fun ApiResult.Failed.toStatus(): TransportStatus =
    if (error == ApiError.MISSING_PERMISSION) TransportStatus.MissingPermissions(emptyList()) else TransportStatus.Failed(error, message)
