package net.moesli.scan7.detection

/** Axis-aligned box. Plain Kotlin (no android.graphics) so the detection math is JVM-testable. */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * One detected card. [box] is normalized to 0–1 in upright (original) image coordinates.
 * [fromTracking]: not detected in this frame but carried over from a stable track ([DetectionTracker]).
 */
data class Detection(val label: String, val score: Float, val box: Box, val fromTracking: Boolean = false)
