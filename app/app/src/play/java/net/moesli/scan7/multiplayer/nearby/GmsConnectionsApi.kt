package net.moesli.scan7.multiplayer.nearby

import android.content.Context
import android.util.Log
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
import net.moesli.scan7.share.RadioLog

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
        log("startAdvertising $localName on $serviceId")
        client.startAdvertising(localName, serviceId, lifecycle(callback), options).report("startAdvertising", done)
    }

    override fun stopAdvertising() {
        log("stopAdvertising")
        client.stopAdvertising()
    }

    override fun startDiscovery(serviceId: String, callback: DiscoveryCallback, done: (ApiResult) -> Unit) {
        val options = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_STAR).build()
        val discovery = object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                log("found $endpointId ${info.endpointName} on ${info.serviceId}")
                callback.onEndpointFound(endpointId, info.endpointName, info.serviceId)
            }

            override fun onEndpointLost(endpointId: String) {
                log("lost $endpointId")
                callback.onEndpointLost(endpointId)
            }
        }
        log("startDiscovery on $serviceId")
        client.startDiscovery(serviceId, discovery, options).report("startDiscovery", done)
    }

    override fun stopDiscovery() {
        log("stopDiscovery")
        client.stopDiscovery()
    }

    override fun requestConnection(localName: String, endpointId: String, callback: ConnectionCallback, done: (ApiResult) -> Unit) {
        log("requestConnection $endpointId")
        client.requestConnection(localName, endpointId, lifecycle(callback)).report("requestConnection", done)
    }

    override fun acceptConnection(endpointId: String, payloads: PayloadCallback, done: (ApiResult) -> Unit) {
        val payloadCallback = object : com.google.android.gms.nearby.connection.PayloadCallback() {
            override fun onPayloadReceived(endpointId: String, payload: Payload) {
                if (payload.type == Payload.Type.BYTES) payload.asBytes()?.let { payloads.onBytes(endpointId, it) }
            }

            override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
        }
        log("acceptConnection $endpointId")
        client.acceptConnection(endpointId, payloadCallback).report("acceptConnection", done)
    }

    override fun rejectConnection(endpointId: String) {
        log("rejectConnection $endpointId")
        client.rejectConnection(endpointId)
    }

    override fun sendPayload(endpointId: String, bytes: ByteArray, done: (ApiResult) -> Unit) {
        client.sendPayload(endpointId, Payload.fromBytes(bytes)).report("sendPayload", done)
    }

    override fun disconnectFromEndpoint(endpointId: String) {
        log("disconnectFromEndpoint $endpointId")
        client.disconnectFromEndpoint(endpointId)
    }

    override fun stopAllEndpoints() {
        log("stopAllEndpoints")
        client.stopAllEndpoints()
    }

    private fun lifecycle(callback: ConnectionCallback) = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            log("connectionInitiated $endpointId incoming=${info.isIncomingConnection}")
            callback.onConnectionInitiated(endpointId, info.endpointName)
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            log("connectionResult $endpointId ${ConnectionsStatusCodes.getStatusCodeString(resolution.status.statusCode)}")
            callback.onConnectionResult(endpointId, resolution.status.toResult())
        }

        override fun onDisconnected(endpointId: String) {
            log("disconnected $endpointId")
            callback.onDisconnected(endpointId)
        }
    }

    private fun Task<Void>.report(what: String, done: (ApiResult) -> Unit) {
        addOnSuccessListener {
            // Sends are too many for the trail; every other call's outcome goes in.
            if (what != "sendPayload") log("$what ok")
            done(ApiResult.Ok)
        }
        addOnFailureListener { e ->
            val code = (e as? ApiException)?.statusCode ?: -1
            val line = "$what failed: $code ${ConnectionsStatusCodes.getStatusCodeString(code)} ${e.message}"
            Log.w(TAG, line)
            RadioLog.app.add("nearby", line)
            done(failure(code, e.message))
        }
    }

    private fun Status.toResult(): ApiResult = if (isSuccess) ApiResult.Ok else failure(statusCode, statusMessage)

    private companion object {
        /** Field-test trail of every radio call and callback: `adb logcat -s Scan7Nearby`. */
        const val TAG = "Scan7Nearby"

        /** Also into the on-screen technical details ([RadioLog]). */
        fun log(message: String) {
            Log.i(TAG, message)
            RadioLog.app.add("nearby", message)
        }

        /** The code and Play's name go along: [NearbyCodes] tells permission, setting and radio apart. */
        fun failure(code: Int, message: String?): ApiResult.Failed {
            val name = ConnectionsStatusCodes.getStatusCodeString(code)
            return ApiResult.Failed(NearbyCodes.error(code, name), code, message ?: name)
        }
    }
}
