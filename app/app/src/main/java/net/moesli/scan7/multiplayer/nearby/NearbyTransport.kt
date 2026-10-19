package net.moesli.scan7.multiplayer.nearby

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.moesli.scan7.multiplayer.ClientTransport
import net.moesli.scan7.multiplayer.HostTransport
import net.moesli.scan7.multiplayer.PeerId

/** Timers for retries and delayed disconnects (the app: a Handler / coroutine on the main thread; tests: virtual time). */
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

    override fun start(listener: ClientTransport.Listener) {
        this.listener = listener
        discover()
    }

    override fun send(frame: ByteArray) {
        val ep = endpoint ?: return
        chunker.split(frame).forEach { chunk ->
            api.sendPayload(ep, chunk) { result ->
                if (result is ApiResult.Failed && result.error == ApiError.NOT_CONNECTED) dropped(ep)
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
    }

    private fun discover() {
        if (closed) return
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
                    scheduleRetry()
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

    private fun dropped(ep: String) {
        chunker.reset(ep)
        if (endpoint == ep) {
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
            connecting = endpointId
            _status.value = TransportStatus.Connecting(endpointId)
            // Field test: the host lost Wi-Fi mid-request, no result ever came, and `connecting` then
            // made the guest ignore the host it found again ("Connecting…" forever).
            connectTimer?.cancel()
            connectTimer = scheduler.schedule(connectTimeoutMs) {
                if (connecting == endpointId && endpoint == null && !closed) {
                    connecting = null
                    api.disconnectFromEndpoint(endpointId)
                    scheduleRetry()
                }
            }
            api.requestConnection(requestName(), endpointId, connectionCallback) { result ->
                if (result is ApiResult.Failed && connecting == endpointId) {
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
                connecting = null
                connectTimer?.cancel()
                scheduleRetry()
            }
        }
    }

    private val payloads = PayloadCallback { ep, bytes ->
        if (ep == endpoint) chunker.accept(ep, bytes)?.let { listener?.onFrame(it) }
    }

    private val connectionCallback = object : ConnectionCallback {
        override fun onConnectionInitiated(endpointId: String, remoteName: String) {
            if (endpointId != connecting || !JoinAuth.matches(remoteName, link.sessionId)) {
                api.rejectConnection(endpointId)
                return
            }
            api.acceptConnection(endpointId, payloads) { }
        }

        override fun onConnectionResult(endpointId: String, result: ApiResult) {
            if (endpointId != connecting) return
            connecting = null
            connectTimer?.cancel()
            if (result == ApiResult.Ok && !closed) {
                endpoint = endpointId
                attempt = 0
                _status.value = TransportStatus.Connected(endpointId)
                listener?.onConnected()
            } else if (result is ApiResult.Failed && result.error == ApiError.REJECTED) {
                // Wrong token / removed: the host won't take us; don't hammer it.
                _status.value = TransportStatus.Failed(ApiError.REJECTED, result.message)
            } else {
                scheduleRetry()
            }
        }

        override fun onDisconnected(endpointId: String) {
            if (endpointId == endpoint || endpointId == connecting) dropped(endpointId)
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
