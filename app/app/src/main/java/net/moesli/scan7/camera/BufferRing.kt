package net.moesli.scan7.camera

/**
 * Fixed ring of [size] reusable buffers (plain Kotlin, no Android types, so it is unit-testable):
 * each slot's buffer is created lazily on first use, reused while it still [fits], replaced (the
 * old one handed to [free]) when it doesn't, and [release] frees all of them. After a release the
 * slots fill again lazily. Not thread-safe: use it from one thread only (the detector thread).
 */
internal class BufferRing<B : Any>(val size: Int, private val free: (B) -> Unit) {
    private val buffers = arrayOfNulls<Any>(size)
    private var next = 0

    init {
        require(size > 0) { "size must be > 0" }
    }

    /** Number of slots that currently hold a buffer. */
    val allocated: Int get() = buffers.count { it != null }

    /** Index of the slot to fill next; advances the ring. */
    fun advance(): Int = next.also { next = (next + 1) % size }

    @Suppress("UNCHECKED_CAST")
    operator fun get(index: Int): B? = buffers[index] as B?

    /** The buffer of slot [index] if it [fits], else a new one from [create] (the old one is freed). */
    fun obtain(index: Int, fits: (B) -> Boolean, create: () -> B): B {
        get(index)?.let { if (fits(it)) return it }
        return create().also { put(index, it) }
    }

    /** Stores [buffer] in slot [index], freeing the buffer it replaces (if a different one). */
    fun put(index: Int, buffer: B) {
        val old = get(index)
        buffers[index] = buffer
        if (old != null && old !== buffer) free(old)
    }

    /** Frees every buffer and starts the ring over at slot 0. */
    fun release() {
        for (i in buffers.indices) {
            @Suppress("UNCHECKED_CAST")
            (buffers[i] as B?)?.let(free)
            buffers[i] = null
        }
        next = 0
    }
}
