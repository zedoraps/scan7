package net.moesli.scan7.multiplayer.nearby

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

    /** Still looking, but nothing found for [NearbyClientTransport.NOT_FOUND_MS] (discovery keeps running). */
    data object NotFound : TransportStatus

    data class Connecting(val endpointId: String) : TransportStatus

    data class Connected(val endpointId: String) : TransportStatus

    /** Lost the host, or the last try failed; [retryInMs] until the next attempt. */
    data class Reconnecting(val retryInMs: Long) : TransportStatus

    /** Runtime permissions our check finds missing ([code] = Nearby's, when Nearby reported it). No retry until the user acts. */
    data class MissingPermissions(val permissions: List<String>, val code: Int = 0) : TransportStatus

    /** A phone setting is off (our check before starting, or Nearby's 8025). No retry until the user acts. */
    data class NeedsSetting(val setting: RadioSetting, val code: Int = 0) : TransportStatus

    /**
     * Nearby says [permission] is missing although our check finds every permission granted (field
     * report 2026-10-19 on Samsung phones). Retrying by itself only made the screen flicker: the user
     * taps Try again. [permission] `null` = Nearby didn't say which.
     */
    data class NearbyRefused(val permission: String?, val code: Int) : TransportStatus

    data class Failed(val error: ApiError, val message: String? = null, val code: Int = 0) : TransportStatus

    data object Stopped : TransportStatus
}

/** Counters for the "Technical details" of the share sheet / join screen. */
data class LinkStats(
    /** startAdvertising / startDiscovery calls (each try). */
    val starts: Int = 0,
    /** Connection requests sent (guest). */
    val requests: Int = 0,
    /** Failed tries since the last good link. */
    val failuresInARow: Int = 0,
    /** Nearby's last failure: code and call. */
    val lastCode: Int? = null,
    val lastCall: String? = null,
    /** The permission Nearby last named as missing. */
    val nearbyMissing: String? = null,
)

/** Nearby service id: one per app id, so debug (`.b`, …) and release builds never see each other. */
fun nearbyServiceId(applicationId: String) = "$applicationId.mp"

/**
 * How a failed call ends the try: a permission or setting the user has to fix (no retry), or `null`
 * (retry). Nearby naming a permission our check finds granted is [TransportStatus.NearbyRefused].
 */
private fun ApiResult.Failed.blocked(missingNow: List<String>): TransportStatus? = when (error) {
    ApiError.SETTING_OFF -> TransportStatus.NeedsSetting(NearbyCodes.describe(code).setting ?: RadioSetting.LOCATION, code)
    ApiError.MISSING_PERMISSION ->
        if (missingNow.isNotEmpty()) TransportStatus.MissingPermissions(missingNow, code)
        else TransportStatus.NearbyRefused(NearbyCodes.describe(code).permission, code)
    else -> null
}

private fun ApiResult.Failed.describe() = "$code ${NearbyCodes.describe(code).name}${message?.let { " ($it)" }.orEmpty()}"

/**
 * Host side over Nearby Connections, `P2P_STAR` hub (docs/dev/MULTI_DEVICE.md §6.1). Advertises
 * `s7|<sessionId>`, accepts only connection requests that prove the QR token ([JoinAuth]), and
 * chunks frames above the BYTES limit ([FrameChunker]). [missingPermissions] and [blockingSetting]
 * are checked before advertising starts; a radio failure is retried ([backoffMs], at most
 * [NearbyClientTransport.MAX_FAILURES] in a row), 8008 once after stopping everything.
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
    /** A setting that is off and needed to advertise (Bluetooth), checked before each try. */
    private val blockingSetting: () -> RadioSetting? = { null },
    private val backoffMs: List<Long> = NearbyClientTransport.DEFAULT_BACKOFF_MS,
    private val log: (String) -> Unit = {},
) : HostTransport {
    private val _status = MutableStateFlow<TransportStatus>(TransportStatus.Idle)
    val status: StateFlow<TransportStatus> = _status.asStateFlow()
    private val _stats = MutableStateFlow(LinkStats())
    val stats: StateFlow<LinkStats> = _stats.asStateFlow()

    private var listener: HostTransport.Listener? = null
    private val chunker = FrameChunker(api.maxPayloadBytes)
    private val pending = mutableSetOf<String>()
    private val connected = mutableSetOf<String>()
    private var stopped = false
    private var retry: Cancellable? = null
    private var busyRetried = false

    /** Connection requests turned away for a wrong / missing token (debug screen). */
    var rejectedRequests = 0
        private set

    val endpoints: Set<String> get() = connected.toSet()

    override fun start(listener: HostTransport.Listener) {
        this.listener = listener
        advertise()
    }

    private fun advertise() {
        if (stopped) return
        val missing = missingPermissions()
        if (missing.isNotEmpty()) {
            log("host: permissions missing ${missing.joinToString { it.substringAfterLast('.') }}")
            _status.value = TransportStatus.MissingPermissions(missing)
            return
        }
        blockingSetting()?.let {
            log("host: $it is off")
            _status.value = TransportStatus.NeedsSetting(it)
            return
        }
        _stats.update { it.copy(starts = it.starts + 1) }
        api.startAdvertising(JoinAuth.advertisedName(sessionId), serviceId, connectionCallback) { result ->
            if (stopped) return@startAdvertising
            when {
                result == ApiResult.Ok -> {
                    _stats.update { it.copy(failuresInARow = 0) }
                    _status.value = TransportStatus.Advertising
                }
                // Our own earlier advertising still runs: that's what we want.
                result is ApiResult.Failed && result.error == ApiError.ALREADY_ACTIVE -> _status.value = TransportStatus.Advertising
                result is ApiResult.Failed -> failed(result)
            }
        }
    }

    private fun failed(result: ApiResult.Failed) {
        val n = _stats.value.failuresInARow + 1
        _stats.update {
            it.copy(
                failuresInARow = n, lastCode = result.code, lastCall = "startAdvertising",
                nearbyMissing = NearbyCodes.describe(result.code).permission ?: it.nearbyMissing,
            )
        }
        log("host: startAdvertising failed: ${result.describe()} (try $n)")
        result.blocked(missingPermissions())?.let {
            _status.value = it
            return
        }
        when {
            // 8008: Nearby still holds a discovery (or an older advertising) of this app; clear it once.
            result.error == ApiError.BUSY && !busyRetried -> {
                busyRetried = true
                log("host: clearing Nearby (stopDiscovery, stopAllEndpoints), then once more")
                api.stopDiscovery()
                api.stopAllEndpoints()
                schedule(BUSY_RETRY_MS)
            }
            result.error == ApiError.BUSY || n >= NearbyClientTransport.MAX_FAILURES ->
                _status.value = TransportStatus.Failed(result.error, result.message, result.code)
            else -> schedule(backoffMs[minOf(n - 1, backoffMs.lastIndex)])
        }
    }

    private fun schedule(delay: Long) {
        _status.value = TransportStatus.Reconnecting(delay)
        retry?.cancel()
        retry = scheduler.schedule(delay) { advertise() }
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
        stopped = true
        retry?.cancel()
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
                log("host: rejected $endpointId (no valid proof)")
                api.rejectConnection(endpointId)
                return
            }
            log("host: accepting $endpointId")
            pending += endpointId
            api.acceptConnection(endpointId, payloads) { result ->
                if (result is ApiResult.Failed) {
                    log("host: acceptConnection $endpointId failed: ${result.describe()}")
                    pending -= endpointId
                }
            }
        }

        override fun onConnectionResult(endpointId: String, result: ApiResult) {
            if (!pending.remove(endpointId)) return
            if (result is ApiResult.Failed) log("host: link to $endpointId failed: ${result.describe()}")
            if (result == ApiResult.Ok && connected.add(endpointId)) {
                log("host: linked $endpointId")
                listener?.onPeerConnected(PeerId(endpointId))
            }
        }

        override fun onDisconnected(endpointId: String) {
            log("host: $endpointId disconnected")
            pending -= endpointId
            lost(endpointId)
        }
    }

    companion object {
        const val FLUSH_DELAY_MS = 300L

        /** After clearing Nearby for 8008: a moment for Play services to wind down. */
        const val BUSY_RETRY_MS = 500L
    }
}

/**
 * Guest side over Nearby Connections, `P2P_STAR` spoke. Discovers the host of [link]'s session
 * (ignores other Scan7 sessions), requests the connection with the token proof, and keeps the link:
 * after a drop it reports `onDisconnected`, rediscovers with backoff and reports `onConnected`
 * again (the [net.moesli.scan7.multiplayer.ClientSession] then sends a fresh `hello`). [disconnect]
 * ends it for good.
 *
 * Failures (docs/dev/MULTI_DEVICE.md §6.1): a missing permission or a switched-off setting stops
 * the tries until the user acts ([TransportStatus.MissingPermissions], [TransportStatus.NeedsSetting],
 * [TransportStatus.NearbyRefused]); 8008 clears Nearby once and tries again; any other failed try
 * (radio error, unanswered request, host lost mid-handshake) backs off, and after [MAX_FAILURES] in
 * a row it stops at [TransportStatus.Failed] with a Try again button instead of "Connecting…" forever.
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
    /** A setting that is off and needed to discover (Location, Bluetooth), checked before each try. */
    private val blockingSetting: () -> RadioSetting? = { null },
    private val notFoundMs: Long = NOT_FOUND_MS,
) : ClientTransport {
    private val _status = MutableStateFlow<TransportStatus>(TransportStatus.Idle)
    val status: StateFlow<TransportStatus> = _status.asStateFlow()
    private val _stats = MutableStateFlow(LinkStats())
    val stats: StateFlow<LinkStats> = _stats.asStateFlow()

    private var listener: ClientTransport.Listener? = null
    private val chunker = FrameChunker(api.maxPayloadBytes)
    private var endpoint: String? = null
    private var connecting: String? = null
    private var discovering = false
    private var closed = false
    private var attempt = 0
    private var retry: Cancellable? = null
    private var connectTimer: Cancellable? = null
    private var notFoundTimer: Cancellable? = null
    private var busyRetried = false

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
        notFoundTimer?.cancel()
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
            log("permissions missing: ${missing.joinToString { it.substringAfterLast('.') }}")
            _status.value = TransportStatus.MissingPermissions(missing)
            return
        }
        blockingSetting()?.let {
            log("$it is off")
            _status.value = TransportStatus.NeedsSetting(it)
            return
        }
        _status.value = TransportStatus.Discovering
        _stats.update { it.copy(starts = it.starts + 1) }
        discovering = true
        notFoundTimer?.cancel()
        notFoundTimer = scheduler.schedule(notFoundMs) {
            if (discovering && _status.value == TransportStatus.Discovering) {
                log("host not found for ${notFoundMs / 1000} s, still looking")
                _status.value = TransportStatus.NotFound
            }
        }
        api.startDiscovery(serviceId, discoveryCallback) { result ->
            when {
                result == ApiResult.Ok -> Unit
                result is ApiResult.Failed && result.error == ApiError.ALREADY_ACTIVE -> Unit
                result is ApiResult.Failed -> {
                    discovering = false
                    notFoundTimer?.cancel()
                    // A late failure must not knock over a link found in the meantime.
                    if (endpoint == null && connecting == null && !closed) failed("startDiscovery", result)
                }
            }
        }
    }

    private fun stopDiscovery() {
        if (discovering) api.stopDiscovery()
        discovering = false
        notFoundTimer?.cancel()
    }

    /** A failed try: stop where the user must act, clear Nearby once for 8008, else back off (at most [MAX_FAILURES] in a row). */
    private fun failed(call: String, result: ApiResult.Failed) {
        val n = _stats.value.failuresInARow + 1
        _stats.update {
            it.copy(
                failuresInARow = n, lastCode = result.code, lastCall = call,
                nearbyMissing = NearbyCodes.describe(result.code).permission ?: it.nearbyMissing,
            )
        }
        log("$call failed: ${result.describe()} (try $n)")
        result.blocked(missingPermissions())?.let {
            _status.value = it
            return
        }
        when {
            // 8008: Nearby still holds this app's advertising (it hosted before); clear it once.
            result.error == ApiError.BUSY && !busyRetried -> {
                busyRetried = true
                log("clearing Nearby (stopAdvertising, stopAllEndpoints), then once more")
                api.stopAdvertising()
                api.stopAllEndpoints()
                retryIn(NearbyHostTransport.BUSY_RETRY_MS)
            }
            result.error == ApiError.BUSY || n >= MAX_FAILURES -> _status.value = TransportStatus.Failed(result.error, result.message, result.code)
            else -> scheduleRetry()
        }
    }

    /** A try that ended without a Nearby code (timeout, host lost mid-handshake): counts like a failure. */
    private fun gaveUp(call: String) = failed(call, ApiResult.Failed(ApiError.OTHER, 0, call))

    private fun scheduleRetry() {
        if (closed) return
        val delay = backoffMs[minOf(attempt, backoffMs.lastIndex)]
        attempt++
        retryIn(delay)
    }

    private fun retryIn(delay: Long) {
        if (closed) return
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
            if (serviceId != this@NearbyClientTransport.serviceId || !JoinAuth.matches(name, link.sessionId)) {
                log("found $endpointId, another game")
                return
            }
            stopDiscovery()
            val a = Attempt(++attempts)
            current = a
            connecting = endpointId
            log("found the host ($endpointId), requesting (attempt ${a.number})")
            _stats.update { it.copy(requests = it.requests + 1) }
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
                    gaveUp("requestConnection timeout")
                }
            }
            api.requestConnection(requestName(), endpointId, a) { result ->
                if (result is ApiResult.Failed && current === a && connecting == endpointId) {
                    current = null
                    connecting = null
                    connectTimer?.cancel()
                    failed("requestConnection", result)
                }
            }
        }

        override fun onEndpointLost(endpointId: String) {
            // The host vanished before we got connected: look again (a connected link reports onDisconnected instead).
            if (connecting == endpointId && endpoint == null) {
                log("host $endpointId lost before the link")
                current = null
                connecting = null
                connectTimer?.cancel()
                gaveUp("endpoint lost")
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
            api.acceptConnection(endpointId, payloads) { result ->
                if (result is ApiResult.Failed) log("acceptConnection failed: ${result.describe()}")
            }
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
                busyRetried = false
                _stats.update { it.copy(failuresInARow = 0) }
                retry?.cancel()
                log("linked to $endpointId (attempt $number)")
                _status.value = TransportStatus.Connected(endpointId)
                listener?.onConnected()
            } else if (result is ApiResult.Failed && result.error == ApiError.REJECTED) {
                current = null
                log("the host rejected the link (attempt $number)")
                // Wrong token / removed: the host won't take us; don't hammer it.
                _status.value = TransportStatus.Failed(ApiError.REJECTED, result.message, result.code)
            } else {
                current = null
                if (result is ApiResult.Failed) failed("connectionResult", result) else scheduleRetry()
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

        /** Failed tries in a row before the guest (or host) stops and offers Try again. */
        const val MAX_FAILURES = 6

        /** "Not found yet": the host phone isn't seen after this long. */
        const val NOT_FOUND_MS = 20_000L
    }
}
