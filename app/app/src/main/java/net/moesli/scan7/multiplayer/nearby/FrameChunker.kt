package net.moesli.scan7.multiplayer.nearby

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Splits protocol frames into Nearby BYTES payloads of at most `maxPayloadBytes` and joins them
 * again. Chunk = [MAGIC] + message id (int) + index (short) + count (short) + data. Nearby delivers
 * BYTES payloads of one endpoint in order, so a reassembly buffer per endpoint is enough; anything
 * out of order, foreign or too big is dropped (the session then resyncs).
 */
class FrameChunker(maxPayloadBytes: Int, private val maxFrameBytes: Int = MAX_FRAME_BYTES) {
    private val dataPerChunk = maxPayloadBytes - HEADER
    private var nextId = 1
    private val partial = mutableMapOf<String, Partial>()

    init {
        require(dataPerChunk > 0) { "Payload limit too small" }
    }

    fun split(frame: ByteArray): List<ByteArray> {
        require(frame.size <= maxFrameBytes) { "Frame of ${frame.size} bytes is over $maxFrameBytes" }
        val id = nextId++
        val count = maxOf(1, (frame.size + dataPerChunk - 1) / dataPerChunk)
        require(count <= Short.MAX_VALUE) { "Too many chunks" }
        return (0 until count).map { i ->
            val from = i * dataPerChunk
            val to = minOf(frame.size, from + dataPerChunk)
            ByteBuffer.allocate(HEADER + (to - from))
                .put(MAGIC).putInt(id).putShort(i.toShort()).putShort(count.toShort())
                .put(frame, from, to - from)
                .array()
        }
    }

    /** Feeds one payload from [endpoint]; returns the complete frame when this was its last chunk. */
    fun accept(endpoint: String, chunk: ByteArray): ByteArray? {
        if (chunk.size < HEADER || chunk[0] != MAGIC) return null
        val buf = ByteBuffer.wrap(chunk)
        buf.get()
        val id = buf.int
        val index = buf.short.toInt()
        val count = buf.short.toInt()
        if (count < 1 || index !in 0 until count || count.toLong() * dataPerChunk > maxFrameBytes + dataPerChunk) {
            partial.remove(endpoint)
            return null
        }
        val p = if (index == 0) Partial(id, count).also { partial[endpoint] = it } else partial[endpoint]
        if (p == null || p.id != id || p.count != count || p.next != index) {
            partial.remove(endpoint) // a lost or foreign chunk: give up on this frame
            return null
        }
        p.data.write(chunk, HEADER, chunk.size - HEADER)
        p.next++
        if (p.data.size() > maxFrameBytes) {
            partial.remove(endpoint)
            return null
        }
        if (p.next < count) return null
        partial.remove(endpoint)
        return p.data.toByteArray()
    }

    /** Endpoint gone: forget its half-received frame. */
    fun reset(endpoint: String) {
        partial.remove(endpoint)
    }

    private class Partial(val id: Int, val count: Int) {
        var next = 0
        val data = ByteArrayOutputStream()
    }

    companion object {
        const val MAGIC: Byte = 0x37
        const val HEADER = 9

        /** Largest frame accepted (protocol frames are designed for ≤ 32 KB; this is the safety cap). */
        const val MAX_FRAME_BYTES = 1024 * 1024
    }
}
