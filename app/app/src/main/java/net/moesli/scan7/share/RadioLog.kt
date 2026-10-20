package net.moesli.scan7.share

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The last [capacity] radio events in memory, for the "Technical details" on the Join screen and
 * the share sheet (also in release builds, where logcat is out of reach): every Nearby call and
 * callback (`GmsConnectionsApi`), the transports' decisions, and game messages by **type only** (no
 * JSON, no player names). Never written to disk or sent anywhere; "Copy log" puts it on the
 * clipboard. Thread-safe (Nearby's main thread and the session thread both write).
 */
class RadioLog(private val capacity: Int = CAPACITY, private val clock: () -> Long = System::currentTimeMillis) {
    data class Event(val atMs: Long, val tag: String, val message: String)

    private val buffer = ArrayDeque<Event>(capacity)
    private val _version = MutableStateFlow(0L)

    /** Bumped on every event, so the details panel redraws. */
    val version: StateFlow<Long> = _version.asStateFlow()

    fun add(tag: String, message: String) {
        synchronized(buffer) {
            if (buffer.size == capacity) buffer.removeFirst()
            buffer.addLast(Event(clock(), tag, message.take(MAX_MESSAGE)))
        }
        _version.value++
    }

    /** Newest last. */
    fun last(n: Int = capacity): List<Event> = synchronized(buffer) { buffer.toList().takeLast(n) }

    fun clear() {
        synchronized(buffer) { buffer.clear() }
        _version.value++
    }

    companion object {
        const val CAPACITY = 200
        private const val MAX_MESSAGE = 300

        /** The app's one log (process-wide like the Nearby client). */
        val app = RadioLog()

        /** "Frame type only": `{"t":"hello",…}` → `hello`; anything else → its size. */
        fun frameType(json: String?, size: Int): String =
            json?.let { Regex(""""(?:t|type)"\s*:\s*"([A-Za-z_]+)"""").find(it)?.groupValues?.get(1) } ?: "$size bytes"
    }
}
