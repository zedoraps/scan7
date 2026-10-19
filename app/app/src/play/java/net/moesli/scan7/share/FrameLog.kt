package net.moesli.scan7.share

import android.util.Log
import net.moesli.scan7.multiplayer.ClientTransport
import net.moesli.scan7.multiplayer.HostTransport
import net.moesli.scan7.multiplayer.PeerId
import java.util.zip.GZIPInputStream

/**
 * Field-test trail of every game message (`adb logcat -s Scan7Frames`): the decoded JSON,
 * shortened. Wraps the radio transports; the plain-Kotlin protocol stays untouched.
 */
private const val TAG = "Scan7Frames"

private fun describe(frame: ByteArray): String = runCatching {
    val text = when (frame.firstOrNull()?.toInt()) {
        0 -> frame.copyOfRange(1, frame.size)
        1 -> GZIPInputStream(frame.inputStream(1, frame.size - 1)).use { it.readBytes() }
        else -> frame
    }.decodeToString()
    if (text.length > 300) text.take(300) + "… (${text.length})" else text
}.getOrElse { "${frame.size} bytes" }

internal class LoggingHostTransport(private val inner: HostTransport) : HostTransport {
    override fun start(listener: HostTransport.Listener) = inner.start(object : HostTransport.Listener {
        override fun onPeerConnected(peer: PeerId) {
            Log.i(TAG, "peer connected ${peer.value}")
            listener.onPeerConnected(peer)
        }

        override fun onFrame(peer: PeerId, frame: ByteArray) {
            Log.i(TAG, "<- ${peer.value} ${describe(frame)}")
            listener.onFrame(peer, frame)
        }

        override fun onPeerDisconnected(peer: PeerId) {
            Log.i(TAG, "peer disconnected ${peer.value}")
            listener.onPeerDisconnected(peer)
        }
    })

    override fun send(peer: PeerId, frame: ByteArray) {
        Log.i(TAG, "-> ${peer.value} ${describe(frame)}")
        inner.send(peer, frame)
    }

    override fun disconnect(peer: PeerId) = inner.disconnect(peer)

    override fun stop() = inner.stop()
}

internal class LoggingClientTransport(private val inner: ClientTransport) : ClientTransport {
    override fun start(listener: ClientTransport.Listener) = inner.start(object : ClientTransport.Listener {
        override fun onConnected() {
            Log.i(TAG, "connected")
            listener.onConnected()
        }

        override fun onFrame(frame: ByteArray) {
            Log.i(TAG, "<- ${describe(frame)}")
            listener.onFrame(frame)
        }

        override fun onDisconnected() {
            Log.i(TAG, "disconnected")
            listener.onDisconnected()
        }
    })

    override fun send(frame: ByteArray) {
        Log.i(TAG, "-> ${describe(frame)}")
        inner.send(frame)
    }

    override fun disconnect() = inner.disconnect()
}
