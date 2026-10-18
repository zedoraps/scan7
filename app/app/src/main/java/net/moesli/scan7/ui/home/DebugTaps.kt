package net.moesli.scan7.ui.home

/** Taps on the Home wordmark that open the hidden debug menu (like Android's "Build number"). */
const val DEBUG_TAPS = 5

/** All [DEBUG_TAPS] taps must fall within this window. */
const val DEBUG_TAP_WINDOW_MS = 3_000L

/** Taps after which the countdown toast starts ("2 more taps for debug…"). */
const val DEBUG_TAPS_BEFORE_HINT = 3

/** What a wordmark tap does. */
sealed interface DebugTapResult {
    /** Nothing to show yet. */
    data object None : DebugTapResult

    /** Show the countdown: [remaining] more taps open the debug menu. */
    data class Countdown(val remaining: Int) : DebugTapResult {
        val message: String get() = if (remaining == 1) "1 more tap for debug…" else "$remaining more taps for debug…"
    }

    /** Open the debug menu (the counter starts over). */
    data object Open : DebugTapResult
}

/**
 * Counts wordmark taps in a sliding [windowMs] window. Plain Kotlin (JVM unit tested); the caller
 * passes the time (e.g. `SystemClock.uptimeMillis()`).
 */
class DebugTapCounter(
    private val needed: Int = DEBUG_TAPS,
    private val windowMs: Long = DEBUG_TAP_WINDOW_MS,
    private val hintAfter: Int = DEBUG_TAPS_BEFORE_HINT,
) {
    private val taps = ArrayDeque<Long>()

    fun tap(now: Long): DebugTapResult {
        while (taps.isNotEmpty() && now - taps.first() > windowMs) taps.removeFirst()
        taps.addLast(now)
        return when {
            taps.size >= needed -> {
                taps.clear()
                DebugTapResult.Open
            }
            taps.size >= hintAfter -> DebugTapResult.Countdown(needed - taps.size)
            else -> DebugTapResult.None
        }
    }
}
