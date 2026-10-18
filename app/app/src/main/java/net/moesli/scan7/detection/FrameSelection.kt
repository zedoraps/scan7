package net.moesli.scan7.detection

/*
 * Best-frame shutter, plain Kotlin (JVM tests in FrameSelectionTest): which buffered analysis frame
 * the review shows, and the cheap sharpness measure computed per frame on the detector thread.
 */

/** Frames this old (ms before the shutter press) or older are not considered. */
const val SHUTTER_LOOKBACK_MS = 1000L

/** Frames up to this long after the press are considered too (they skip the shake of the tap). */
const val SHUTTER_LOOKAHEAD_MS = 150L

/** Longest wait after the press for a frame past [SHUTTER_LOOKAHEAD_MS] (slow detector / frame drops). */
const val SHUTTER_MAX_WAIT_MS = 250L

/** Weight of each detection ≥ [CONFIDENT_SCORE] in [selectionScore]. */
const val CONFIDENT_BONUS = 0.5f
const val CONFIDENT_SCORE = 0.5f

/** Weight of the relative sharpness (1 = sharpest candidate) in [selectionScore]: about one card. */
const val SHARPNESS_WEIGHT = 1.0f

/** One buffered frame as the selection sees it: its [detections] after hysteresis and its [sharpness]. */
data class FrameCandidate(val detections: List<Detection>, val sharpness: Float)

/**
 * Selection score of [frame] among frames whose best sharpness is [maxSharpness]:
 *
 *   score = Σ s_i + [CONFIDENT_BONUS] · #{s_i ≥ [CONFIDENT_SCORE]} + [SHARPNESS_WEIGHT] · sharpness / maxSharpness
 *
 * over the frame's own detections s_i (cards filled in from tracking don't count: every frame
 * would get them). More and more confident cards win; sharpness breaks ties and outweighs about
 * one weak card, so a blurred frame of the tap loses against a steady one with the same cards.
 */
fun selectionScore(frame: FrameCandidate, maxSharpness: Float): Float {
    val own = frame.detections.filterNot { it.fromTracking }
    val sharp = if (maxSharpness > 0f) (frame.sharpness / maxSharpness).coerceIn(0f, 1f) else 0f
    return own.sumOf { it.score.toDouble() }.toFloat() +
        CONFIDENT_BONUS * own.count { it.score >= CONFIDENT_SCORE } +
        SHARPNESS_WEIGHT * sharp
}

/** Index of the best of [frames] by [selectionScore] (the later one on ties), or -1 if empty. */
fun selectBestFrame(frames: List<FrameCandidate>): Int {
    if (frames.isEmpty()) return -1
    val maxSharp = frames.maxOf { it.sharpness }
    var best = 0
    var bestScore = Float.NEGATIVE_INFINITY
    frames.forEachIndexed { i, f ->
        val s = selectionScore(f, maxSharp)
        if (s >= bestScore) {
            best = i
            bestScore = s
        }
    }
    return best
}

/**
 * Sharpness: variance of the 4-neighbour Laplacian of the luma, sampled every [step] pixels in the
 * [width]×[height] region at ([left], [top]) of row-major ARGB [pixels] with row length [stride].
 * Called on the detector's 640 px input (already a filtered downscale), so it costs < 1 ms.
 * Blur (motion or focus) lowers it; only comparable between frames of the same scene.
 */
fun laplacianVariance(pixels: IntArray, stride: Int, left: Int, top: Int, width: Int, height: Int, step: Int = 2): Float {
    fun luma(x: Int, y: Int): Int {
        val p = pixels[y * stride + x]
        return (((p shr 16) and 0xFF) * 77 + ((p shr 8) and 0xFF) * 150 + (p and 0xFF) * 29) shr 8
    }
    var n = 0
    var sum = 0.0
    var sumSq = 0.0
    var y = top + step
    while (y < top + height - step) {
        var x = left + step
        while (x < left + width - step) {
            val l = 4 * luma(x, y) - luma(x - step, y) - luma(x + step, y) - luma(x, y - step) - luma(x, y + step)
            sum += l
            sumSq += l.toDouble() * l
            n++
            x += step
        }
        y += step
    }
    if (n == 0) return 0f
    val mean = sum / n
    return (sumSq / n - mean * mean).toFloat()
}
