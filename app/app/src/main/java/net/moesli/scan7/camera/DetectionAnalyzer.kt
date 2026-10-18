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
import kotlinx.coroutines.withContext
import net.moesli.scan7.detection.Detection
import net.moesli.scan7.detection.DetectionResult
import net.moesli.scan7.detection.DetectorHolder

private const val TAG = "DetectionAnalyzer"

/** Per-frame info for the live overlay. [width]/[height] are the buffer size as delivered (before rotation). */
data class FrameStats(
    val fps: Float,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val detections: List<Detection> = emptyList(),
    val inferenceMs: Float = 0f,
    val preprocessMs: Float = 0f,
    val backend: String? = null,
    val error: String? = null,
) {
    val uprightWidth: Int get() = if (rotationDegrees % 180 == 0) width else height
    val uprightHeight: Int get() = if (rotationDegrees % 180 == 0) height else width
}

/** A frozen analysis frame (upright, own copy) and the detector result on it. */
class CapturedFrame(val bitmap: Bitmap, val result: Result<DetectionResult>)

/**
 * Runs the card detector on each RGBA_8888 analysis frame. Must be installed on
 * [DetectorHolder.executor]. The frame is copied into one reused bitmap (row padding included);
 * rotation and letterboxing happen in a single draw inside the detector.
 *
 * The latest frame stays available for [capture] (shutter button): it runs on the same detector
 * thread, so it reads the frame between two [analyze] calls without extra per-frame copies.
 */
class DetectionAnalyzer(context: Context) : ImageAnalysis.Analyzer {
    private val appContext = context.applicationContext
    private val fpsCounter = FpsCounter()
    private var fps = 0f
    private var frameBitmap: Bitmap? = null
    private var lastLog = 0L
    // Latest frame, only touched on the detector thread.
    private var lastFrame: Bitmap? = null
    private var lastWidth = 0
    private var lastHeight = 0
    private var lastRotation = 0
    private val _stats = MutableStateFlow<FrameStats?>(null)
    val stats: StateFlow<FrameStats?> = _stats.asStateFlow()

    override fun analyze(image: ImageProxy) {
        image.use {
            fpsCounter.onFrame(it.imageInfo.timestamp)?.let { newFps -> fps = newFps }
            val rotation = it.imageInfo.rotationDegrees
            val base = FrameStats(fps, it.width, it.height, rotation)
            val detector = DetectorHolder.getBlocking(appContext).getOrElse { e ->
                _stats.value = base.copy(error = e.message ?: e.javaClass.simpleName)
                return
            }
            val frame = copyToBitmap(it)
            lastFrame = frame
            lastWidth = it.width
            lastHeight = it.height
            lastRotation = rotation
            val result = detector.detect(frame, rotation, it.width, it.height)
            val stats = base.copy(
                detections = result.detections,
                inferenceMs = result.inferenceMs,
                preprocessMs = result.preprocessMs,
                backend = result.backend,
            )
            _stats.value = stats
            val now = SystemClock.elapsedRealtime()
            if (now - lastLog >= 1000) {
                lastLog = now
                Log.i(TAG, "%.1f fps · inference %.1f ms · pre %.1f ms · %s · %d dets · %dx%d rot %d".format(
                    Locale.ROOT, stats.fps, stats.inferenceMs, stats.preprocessMs, stats.backend,
                    stats.detections.size, stats.width, stats.height, rotation,
                ))
            }
        }
    }

    /**
     * Freezes the latest analysis frame: an upright copy (row padding cropped, rotation applied)
     * plus one detector run on it, both on the detector thread. Null before the first frame.
     */
    suspend fun capture(): CapturedFrame? = withContext(DetectorHolder.dispatcher) {
        val frame = lastFrame ?: return@withContext null
        val rotate = Matrix().apply { postRotate(lastRotation.toFloat()) }
        val upright = Bitmap.createBitmap(frame, 0, 0, lastWidth, lastHeight, rotate, true)
        val result = DetectorHolder.getBlocking(appContext).mapCatching { detector -> detector.detect(upright) }
        CapturedFrame(upright, result)
    }

    /** Forgets the last frame's stats, e.g. before the preview is shown again after a capture. */
    fun clearStats() {
        _stats.value = null
    }

    /** Copies the RGBA plane into a reused bitmap whose width covers the row stride. */
    private fun copyToBitmap(image: ImageProxy): Bitmap {
        val plane = image.planes[0]
        val strideWidth = plane.rowStride / plane.pixelStride
        val buffer = plane.buffer.apply { rewind() }
        if (buffer.remaining() < strideWidth * image.height * 4) {
            // Last row not padded: can't bulk-copy into a stride-wide bitmap (allocates; rare).
            return image.toBitmap()
        }
        val bitmap = frameBitmap?.takeIf { b -> b.width == strideWidth && b.height == image.height }
            ?: createBitmap(strideWidth, image.height).also { b -> frameBitmap = b }
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }
}
