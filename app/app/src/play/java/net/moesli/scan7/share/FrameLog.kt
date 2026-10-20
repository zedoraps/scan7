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

private fun decode(frame: ByteArray): String? = runCatching {
    when (frame.firstOrNull()?.toInt()) {
        0 -> frame.copyOfRange(1, frame.size)
        1 -> GZIPInputStream(frame.inputStream(1, frame.size - 1)).use { it.readBytes() }
        else -> frame
    }.decodeToString()
}.getOrNull()

private fun describe(frame: ByteArray): String {
    val text = decode(frame) ?: return "${frame.size} bytes"
    return if (text.length > 300) text.take(300) + "… (${text.length})" else text
}

/** Technical details on screen: the message type only (no JSON, no names). */
private fun note(direction: String, frame: ByteArray) = RadioLog.app.add("frame", "$direction ${RadioLog.frameType(decode(frame), frame.size)}")

internal class LoggingHostTransport(private val inner: HostTransport) : HostTransport {
    override fun start(listener: HostTransport.Listener) = inner.start(object : HostTransport.Listener {
        override fun onPeerConnected(peer: PeerId) {
            Log.i(TAG, "peer connected ${peer.value}")
            RadioLog.app.add("frame", "peer connected ${peer.value}")
            listener.onPeerConnected(peer)
        }

        override fun onFrame(peer: PeerId, frame: ByteArray) {
            Log.i(TAG, "<- ${peer.value} ${describe(frame)}")
            note("<- ${peer.value}", frame)
            listener.onFrame(peer, frame)
        }

        override fun onPeerDisconnected(peer: PeerId) {
            Log.i(TAG, "peer disconnected ${peer.value}")
            RadioLog.app.add("frame", "peer disconnected ${peer.value}")
            listener.onPeerDisconnected(peer)
        }
    })

    override fun send(peer: PeerId, frame: ByteArray) {
        Log.i(TAG, "-> ${peer.value} ${describe(frame)}")
        note("-> ${peer.value}", frame)
        inner.send(peer, frame)
    }

    override fun disconnect(peer: PeerId) = inner.disconnect(peer)

    override fun stop() = inner.stop()
}

internal class LoggingClientTransport(private val inner: ClientTransport) : ClientTransport {
    override fun start(listener: ClientTransport.Listener) = inner.start(object : ClientTransport.Listener {
        override fun onConnected() {
            Log.i(TAG, "connected")
            RadioLog.app.add("frame", "connected")
            listener.onConnected()
        }

        override fun onFrame(frame: ByteArray) {
            Log.i(TAG, "<- ${describe(frame)}")
            note("<-", frame)
            listener.onFrame(frame)
        }

        override fun onDisconnected() {
            Log.i(TAG, "disconnected")
            RadioLog.app.add("frame", "disconnected")
            listener.onDisconnected()
        }
    })

    override fun send(frame: ByteArray) {
        Log.i(TAG, "-> ${describe(frame)}")
        note("->", frame)
        inner.send(frame)
    }

    override fun disconnect() = inner.disconnect()
}
