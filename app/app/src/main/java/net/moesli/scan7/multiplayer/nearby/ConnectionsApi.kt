package net.moesli.scan7.multiplayer.nearby

/**
 * The slice of Google Nearby Connections' `ConnectionsClient` Scan7 uses, as an interface we own
 * (docs/MULTI_DEVICE.md §6). The Play implementation (`GmsConnectionsApi`, `play` flavor only)
 * forwards 1:1 and maps status codes to [ApiError]; tests use a fake. Every callback and every
 * `done` must arrive on one thread (Nearby: the main thread), which is also where the transports
 * are driven from.
 */
interface ConnectionsApi {
    /** Largest BYTES payload to send; bigger frames are chunked by [FrameChunker]. */
    val maxPayloadBytes: Int

    fun startAdvertising(localName: String, serviceId: String, callback: ConnectionCallback, done: (ApiResult) -> Unit)

    fun stopAdvertising()

    fun startDiscovery(serviceId: String, callback: DiscoveryCallback, done: (ApiResult) -> Unit)

    fun stopDiscovery()

    fun requestConnection(localName: String, endpointId: String, callback: ConnectionCallback, done: (ApiResult) -> Unit)

    fun acceptConnection(endpointId: String, payloads: PayloadCallback, done: (ApiResult) -> Unit)

    fun rejectConnection(endpointId: String)

    fun sendPayload(endpointId: String, bytes: ByteArray, done: (ApiResult) -> Unit)

    /** Nearby doesn't call [ConnectionCallback.onDisconnected] for a disconnect we start ourselves. */
    fun disconnectFromEndpoint(endpointId: String)

    fun stopAllEndpoints()
}

/** `ConnectionLifecycleCallback`. */
interface ConnectionCallback {
    /** Both sides get this; each must accept (or reject) before the link exists. [remoteName] = the other side's local name. */
    fun onConnectionInitiated(endpointId: String, remoteName: String)

    fun onConnectionResult(endpointId: String, result: ApiResult)

    fun onDisconnected(endpointId: String)
}

/** `EndpointDiscoveryCallback`. */
interface DiscoveryCallback {
    fun onEndpointFound(endpointId: String, name: String, serviceId: String)

    fun onEndpointLost(endpointId: String)
}

/** `PayloadCallback`, BYTES payloads only (others are ignored by the implementation). */
fun interface PayloadCallback {
    fun onBytes(endpointId: String, bytes: ByteArray)
}

sealed interface ApiResult {
    data object Ok : ApiResult

    data class Failed(val error: ApiError, val code: Int = 0, val message: String? = null) : ApiResult
}

enum class ApiError {
    /** A runtime permission is missing (or location is off where it's needed). */
    MISSING_PERMISSION,

    /** Already advertising / discovering / connected. */
    ALREADY_ACTIVE,

    /** The other side rejected the connection. */
    REJECTED,

    NOT_CONNECTED,

    /** Bluetooth / Wi-Fi off or radio failure. */
    RADIO,

    OTHER,
}
