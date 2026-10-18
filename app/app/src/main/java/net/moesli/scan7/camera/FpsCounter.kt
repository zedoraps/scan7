package net.moesli.scan7.camera

/** Counts frames over fixed windows of [windowNanos] and reports frames per second. */
class FpsCounter(private val windowNanos: Long = 1_000_000_000L) {
    private var windowStart = -1L
    private var frames = 0

    /** Records a frame at [timestampNanos]; returns the new FPS when a window completes, else null. */
    fun onFrame(timestampNanos: Long): Float? {
        if (windowStart < 0) {
            windowStart = timestampNanos
            return null
        }
        frames++
        val elapsed = timestampNanos - windowStart
        if (elapsed < windowNanos) return null
        val fps = frames * 1_000_000_000f / elapsed
        windowStart = timestampNanos
        frames = 0
        return fps
    }
}
