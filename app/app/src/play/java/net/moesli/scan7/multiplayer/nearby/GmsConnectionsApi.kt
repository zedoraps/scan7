package net.moesli.scan7.multiplayer.nearby

import android.content.Context
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Status
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.google.android.gms.tasks.Task

/**
 * [ConnectionsApi] on Google Play services Nearby Connections (`play` flavor only), strategy
 * `P2P_STAR`. A thin 1:1 forwarder: all logic lives in the plain-Kotlin transports, which are
 * unit-tested against fakes. Nearby calls its callbacks and task listeners on the main thread.
 */
class GmsConnectionsApi(private val client: ConnectionsClient) : ConnectionsApi {
    constructor(context: Context) : this(Nearby.getConnectionsClient(context))

    /** 32 KB: the documented BYTES size everyone agrees on (the reference allows ~1 MB). */
    override val maxPayloadBytes: Int = 32 * 1024

    override fun startAdvertising(localName: String, serviceId: String, callback: ConnectionCallback, done: (ApiResult) -> Unit) {
        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_STAR).build()
        client.startAdvertising(localName, serviceId, lifecycle(callback), options).report(done)
    }

    override fun stopAdvertising() = client.stopAdvertising()

    override fun startDiscovery(serviceId: String, callback: DiscoveryCallback, done: (ApiResult) -> Unit) {
        val options = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_STAR).build()
        val discovery = object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) =
                callback.onEndpointFound(endpointId, info.endpointName, info.serviceId)

            override fun onEndpointLost(endpointId: String) = callback.onEndpointLost(endpointId)
        }
        client.startDiscovery(serviceId, discovery, options).report(done)
    }

    override fun stopDiscovery() = client.stopDiscovery()

    override fun requestConnection(localName: String, endpointId: String, callback: ConnectionCallback, done: (ApiResult) -> Unit) {
        client.requestConnection(localName, endpointId, lifecycle(callback)).report(done)
    }

    override fun acceptConnection(endpointId: String, payloads: PayloadCallback, done: (ApiResult) -> Unit) {
        val payloadCallback = object : com.google.android.gms.nearby.connection.PayloadCallback() {
            override fun onPayloadReceived(endpointId: String, payload: Payload) {
                if (payload.type == Payload.Type.BYTES) payload.asBytes()?.let { payloads.onBytes(endpointId, it) }
            }

            override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
        }
        client.acceptConnection(endpointId, payloadCallback).report(done)
    }

    override fun rejectConnection(endpointId: String) {
        client.rejectConnection(endpointId)
    }

    override fun sendPayload(endpointId: String, bytes: ByteArray, done: (ApiResult) -> Unit) {
        client.sendPayload(endpointId, Payload.fromBytes(bytes)).report(done)
    }

    override fun disconnectFromEndpoint(endpointId: String) = client.disconnectFromEndpoint(endpointId)

    override fun stopAllEndpoints() = client.stopAllEndpoints()

    private fun lifecycle(callback: ConnectionCallback) = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) =
            callback.onConnectionInitiated(endpointId, info.endpointName)

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) =
            callback.onConnectionResult(endpointId, resolution.status.toResult())

        override fun onDisconnected(endpointId: String) = callback.onDisconnected(endpointId)
    }

    private fun Task<Void>.report(done: (ApiResult) -> Unit) {
        addOnSuccessListener { done(ApiResult.Ok) }
        addOnFailureListener { e ->
            val code = (e as? ApiException)?.statusCode ?: -1
            done(failure(code, e.message))
        }
    }

    private fun Status.toResult(): ApiResult = if (isSuccess) ApiResult.Ok else failure(statusCode, statusMessage)

    private companion object {
        fun failure(code: Int, message: String?): ApiResult.Failed {
            val name = ConnectionsStatusCodes.getStatusCodeString(code)
            val error = when {
                code == ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> ApiError.REJECTED
                code == ConnectionsStatusCodes.STATUS_NOT_CONNECTED_TO_ENDPOINT -> ApiError.NOT_CONNECTED
                name.startsWith("MISSING_PERMISSION") -> ApiError.MISSING_PERMISSION
                name.startsWith("STATUS_ALREADY") -> ApiError.ALREADY_ACTIVE
                name.contains("RADIO") || name.contains("BLUETOOTH") || name.contains("WIFI") -> ApiError.RADIO
                else -> ApiError.OTHER
            }
            return ApiResult.Failed(error, code, message ?: name)
        }
    }
}
