package net.moesli.scan7.multiplayer.nearby

/**
 * Hands every callback and every `done` of [inner] to [post], in the order they arrive. Nearby calls
 * them on the main thread, but the transports and sessions live on the share runtime's session
 * thread (docs/dev/MULTI_DEVICE.md §4.8), where the app also sends, retries and joins: without this
 * the link state was changed from two threads at once. Calls into Nearby go straight through.
 */
class SerialConnectionsApi(private val inner: ConnectionsApi, private val post: (() -> Unit) -> Unit) : ConnectionsApi {
    override val maxPayloadBytes: Int get() = inner.maxPayloadBytes

    override fun startAdvertising(localName: String, serviceId: String, callback: ConnectionCallback, done: (ApiResult) -> Unit) =
        inner.startAdvertising(localName, serviceId, serial(callback), serial(done))

    override fun stopAdvertising() = inner.stopAdvertising()

    override fun startDiscovery(serviceId: String, callback: DiscoveryCallback, done: (ApiResult) -> Unit) =
        inner.startDiscovery(serviceId, serial(callback), serial(done))

    override fun stopDiscovery() = inner.stopDiscovery()

    override fun requestConnection(localName: String, endpointId: String, callback: ConnectionCallback, done: (ApiResult) -> Unit) =
        inner.requestConnection(localName, endpointId, serial(callback), serial(done))

    override fun acceptConnection(endpointId: String, payloads: PayloadCallback, done: (ApiResult) -> Unit) =
        inner.acceptConnection(endpointId, { ep, bytes -> post { payloads.onBytes(ep, bytes) } }, serial(done))

    override fun rejectConnection(endpointId: String) = inner.rejectConnection(endpointId)

    override fun sendPayload(endpointId: String, bytes: ByteArray, done: (ApiResult) -> Unit) = inner.sendPayload(endpointId, bytes, serial(done))

    override fun disconnectFromEndpoint(endpointId: String) = inner.disconnectFromEndpoint(endpointId)

    override fun stopAllEndpoints() = inner.stopAllEndpoints()

    private fun serial(done: (ApiResult) -> Unit): (ApiResult) -> Unit = { result -> post { done(result) } }

    private fun serial(callback: DiscoveryCallback) = object : DiscoveryCallback {
        override fun onEndpointFound(endpointId: String, name: String, serviceId: String) = post { callback.onEndpointFound(endpointId, name, serviceId) }

        override fun onEndpointLost(endpointId: String) = post { callback.onEndpointLost(endpointId) }
    }

    private fun serial(callback: ConnectionCallback) = object : ConnectionCallback {
        override fun onConnectionInitiated(endpointId: String, remoteName: String) = post { callback.onConnectionInitiated(endpointId, remoteName) }

        override fun onConnectionResult(endpointId: String, result: ApiResult) = post { callback.onConnectionResult(endpointId, result) }

        override fun onDisconnected(endpointId: String) = post { callback.onDisconnected(endpointId) }
    }
}
