package net.moesli.scan7.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.graphics.createBitmap
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.moesli.scan7.detection.ACCEPT_SCORE
import net.moesli.scan7.detection.Detection
import net.moesli.scan7.detection.DetectionTracker
import net.moesli.scan7.detection.DetectorHolder
import net.moesli.scan7.detection.rowCandidate
import net.moesli.scan7.detection.uprightSize
import net.moesli.scan7.detection.KEEP_SCORE
import net.moesli.scan7.detection.SHUTTER_LOOKAHEAD_MS
import net.moesli.scan7.detection.SHUTTER_LOOKBACK_MS
import net.moesli.scan7.detection.SHUTTER_MAX_WAIT_MS
import net.moesli.scan7.detection.TrackedDetection
import net.moesli.scan7.detection.selectBestFrame
import net.moesli.scan7.detection.selectionScore
import net.moesli.scan7.detection.stableDetections

private const val TAG = "DetectionAnalyzer"

/**
 * Per-frame info for the live overlay. [width]/[height] are the buffer size as delivered (before
 * rotation). [detections] are this frame's raw detections ≥ [ACCEPT_SCORE] (debug overlay);
 * [tracks] the smoothed, hysteresis-filtered cards the scan camera shows.
 */
data class FrameStats(
    val fps: Float,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val detections: List<Detection> = emptyList(),
    val tracks: List<TrackedDetection> = emptyList(),
    val inferenceMs: Float = 0f,
    val preprocessMs: Float = 0f,
    val backend: String? = null,
    val error: String? = null,
) {
    val uprightWidth: Int get() = if (rotationDegrees % 180 == 0) width else height
    val uprightHeight: Int get() = if (rotationDegrees % 180 == 0) height else width
}

/** How the shutter picked its frame (stored with "Save scans for training"). */
data class FrameChoice(
    /** Number of buffered frames that were compared. */
    val bestOf: Int,
    /** Arrival of the chosen frame relative to the shutter press (negative = before). */
    val chosenOffsetMs: Long,
    /** [selectionScore] of the chosen frame. */
    val selectionScore: Float,
    val sharpness: Float,
)

/**
 * A frozen analysis frame (upright, own copy) and its [detections] after hysteresis and tracking
 * fill-in ([stableDetections]), with the camera [rotationDegrees] that was applied, the
 * [zoomRatio] at that frame and the detector [threshold].
 */
class CapturedFrame(
    val bitmap: Bitmap,
    val detections: List<Detection>,
    val rotationDegrees: Int,
    val zoomRatio: Float?,
    val backend: String?,
    val threshold: Float,
    val choice: FrameChoice,
)

/** Analysis frames kept for the best-frame shutter (≈ 1 s at 8 fps; 8 × ~5 MB at 1280×960). */
private const val RING_SIZE = 8

/**
 * Metadata of one buffered analysis frame; its bitmap (raw buffer copy, rotation not applied) is
 * slot [index] of the analyzer's [BufferRing].
 */
private class FrameSlot(val index: Int) {
    var valid = false
    var width = 0
    var height = 0
    var rotation = 0
    var timeMs = 0L
    var zoomRatio: Float? = null
    var backend: String? = null
    var detections: List<Detection> = emptyList()
    var sharpness = 0f
}

/**
 * Runs the card detector (at [KEEP_SCORE]) on each RGBA_8888 analysis frame and feeds a
 * [DetectionTracker]. Must be installed on [DetectorHolder.executor]. Each frame is copied into
 * the next slot of a ring of [RING_SIZE] reused bitmaps (row padding included); rotation and
 * letterboxing happen in a single draw inside the detector. The bitmaps are created lazily and
 * freed by [releaseBuffers] (camera unbound), so they don't stay alive off-camera.
 *
 * [capture] (shutter) picks the best buffered frame from [SHUTTER_LOOKBACK_MS] before to
 * [SHUTTER_LOOKAHEAD_MS] after the press by [selectionScore] (only the cards the row grouping keeps,
 * [rowCandidate]); the ring is only touched on the
 * detector thread, so no locking and no extra per-frame copies.
 */
class DetectionAnalyzer(context: Context) : ImageAnalysis.Analyzer {
    private val appContext = context.applicationContext
    private val fpsCounter = FpsCounter()
    private var fps = 0f
    private var lastLog = 0L
    // Detector-thread state.
    private val ring = Array(RING_SIZE) { FrameSlot(it) }
    private val bitmaps = BufferRing<Bitmap>(RING_SIZE) { it.recycle() }
    private val tracker = DetectionTracker()
    private val _stats = MutableStateFlow<FrameStats?>(null)
    private val lastFrameTime = MutableStateFlow(0L)

    /** Current camera zoom ratio (set by the preview), recorded with captures. */
    @Volatile var zoomRatio: Float? = null

    val stats: StateFlow<FrameStats?> = _stats.asStateFlow()

    override fun analyze(image: ImageProxy) {
        image.use {
            val arrived = SystemClock.elapsedRealtime()
            fpsCounter.onFrame(it.imageInfo.timestamp)?.let { newFps -> fps = newFps }
            val rotation = it.imageInfo.rotationDegrees
            val base = FrameStats(fps, it.width, it.height, rotation)
            val detector = DetectorHolder.getBlocking(appContext).getOrElse { e ->
                _stats.value = base.copy(error = e.message ?: e.javaClass.simpleName)
                return
            }
            val slot = ring[bitmaps.advance()]
            slot.valid = false
            val frame = copyToBitmap(it, slot)
            val result = detector.detect(frame, rotation, it.width, it.height, threshold = KEEP_SCORE)
            val tracks = tracker.update(result.detections)
            slot.apply {
                width = it.width
                height = it.height
                this.rotation = rotation
                timeMs = arrived
                zoomRatio = this@DetectionAnalyzer.zoomRatio
                backend = result.backend
                detections = stableDetections(result.detections, tracks)
                sharpness = result.sharpness
                valid = true
            }
            val stats = base.copy(
                detections = result.detections.filter { d -> d.score >= ACCEPT_SCORE },
                tracks = tracks,
                inferenceMs = result.inferenceMs,
                preprocessMs = result.preprocessMs,
                backend = result.backend,
            )
            _stats.value = stats
            lastFrameTime.value = arrived
            val now = SystemClock.elapsedRealtime()
            if (now - lastLog >= 1000) {
                lastLog = now
                Log.i(TAG, "%.1f fps · inference %.1f ms · pre %.1f ms · %s · %d dets · %d tracks · sharp %.0f · %dx%d rot %d".format(
                    Locale.ROOT, stats.fps, stats.inferenceMs, stats.preprocessMs, stats.backend,
                    stats.detections.size, tracks.size, result.sharpness, stats.width, stats.height, rotation,
                ))
            }
        }
    }

    /**
     * Best-frame shutter for a press at [pressedAt] ([SystemClock.elapsedRealtime]): waits until a
     * frame newer than [SHUTTER_LOOKAHEAD_MS] after the press has been analyzed (at most until
     * [SHUTTER_MAX_WAIT_MS] after the press), then picks the best buffered frame of that window by
     * [selectBestFrame] and makes an upright copy of it. Its detections are the ones of the live
     * pipeline (same hysteresis and tracking), so the detector doesn't run again. Null before
     * the first frame.
     */
    suspend fun capture(pressedAt: Long = SystemClock.elapsedRealtime()): CapturedFrame? {
        val wait = pressedAt + SHUTTER_MAX_WAIT_MS - SystemClock.elapsedRealtime()
        if (wait > 0) withTimeoutOrNull(wait) { lastFrameTime.first { it >= pressedAt + SHUTTER_LOOKAHEAD_MS } }
        return withContext(DetectorHolder.dispatcher) { choose(pressedAt) }
    }

    private fun choose(pressedAt: Long): CapturedFrame? {
        val valid = ring.filter { it.valid && bitmaps[it.index] != null }
        val window = valid.filter { it.timeMs in (pressedAt - SHUTTER_LOOKBACK_MS)..(pressedAt + SHUTTER_LOOKAHEAD_MS) }
        // Nothing in the window (stalled detector): fall back to the latest frame.
        val slots = window.ifEmpty { listOfNotNull(valid.maxByOrNull { it.timeMs }) }.sortedBy { it.timeMs }
        if (slots.isEmpty()) return null
        val candidates = slots.map {
            val (w, h) = uprightSize(it.width, it.height, it.rotation)
            rowCandidate(it.detections, w, h, it.sharpness)
        }
        val best = selectBestFrame(candidates)
        val slot = slots[best]
        val rotate = Matrix().apply { postRotate(slot.rotation.toFloat()) }
        val source = bitmaps[slot.index]!!
        // Must be an own copy: the ring bitmap is reused by the next frames and recycled by
        // releaseBuffers() while the review still shows this one. createBitmap returns the source
        // itself only for an immutable, uncropped, unrotated source: copy it in that case.
        val upright = Bitmap.createBitmap(source, 0, 0, slot.width, slot.height, rotate, true)
            .let { if (it === source) it.copy(Bitmap.Config.ARGB_8888, false) else it }
        val choice = FrameChoice(
            bestOf = slots.size,
            chosenOffsetMs = slot.timeMs - pressedAt,
            selectionScore = selectionScore(candidates[best], candidates.maxOf { it.sharpness }),
            sharpness = slot.sharpness,
        )
        Log.i(TAG, "Shutter: frame %+d ms of %d, score %.2f, %d cards (%d from tracking), ready %d ms after the press".format(
            Locale.ROOT, choice.chosenOffsetMs, choice.bestOf, choice.selectionScore, slot.detections.size,
            slot.detections.count { it.fromTracking }, SystemClock.elapsedRealtime() - pressedAt,
        ))
        return CapturedFrame(upright, slot.detections, slot.rotation, slot.zoomRatio, slot.backend, ACCEPT_SCORE, choice)
    }

    /**
     * Forgets the last frame's stats, the buffered frames and the tracks, e.g. before the preview
     * is shown again after a capture.
     */
    fun clearStats() {
        _stats.value = null
        DetectorHolder.executor.execute {
            ring.forEach { it.valid = false }
            tracker.reset()
        }
    }

    /**
     * Frees the buffered frames (≈ 40 MB) and forgets the tracks, e.g. when the camera unbinds
     * (Review / Manual keep the analyzer but need no frames). Runs on [DetectorHolder.executor]
     * after any queued frame or [capture] (same thread), so no bitmap is recycled while in use;
     * a captured frame is an own copy. The next analyzed frame allocates the ring again.
     */
    fun releaseBuffers() {
        DetectorHolder.executor.execute {
            ring.forEach { it.valid = false }
            bitmaps.release()
            tracker.reset()
        }
    }

    /** Copies the RGBA plane into the [slot]'s reused bitmap whose width covers the row stride. */
    private fun copyToBitmap(image: ImageProxy, slot: FrameSlot): Bitmap {
        val plane = image.planes[0]
        val strideWidth = plane.rowStride / plane.pixelStride
        val buffer = plane.buffer.apply { rewind() }
        if (buffer.remaining() < strideWidth * image.height * 4) {
            // Last row not padded: can't bulk-copy into a stride-wide bitmap (allocates; rare).
            return image.toBitmap().also { b -> bitmaps.put(slot.index, b) }
        }
        val bitmap = bitmaps.obtain(
            slot.index,
            fits = { b -> b.isMutable && b.width == strideWidth && b.height == image.height },
            create = { createBitmap(strideWidth, image.height) },
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }
}
