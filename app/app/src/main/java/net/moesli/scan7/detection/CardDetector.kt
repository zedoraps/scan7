package net.moesli.scan7.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.SystemClock
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.TensorBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

private const val TAG = "CardDetector"

const val MODEL_ASSET = "model.tflite"
const val LABELS_ASSET = "labels.txt"
const val MODEL_VERSION_ASSET = "model_version.txt"

/** Model input edge in px; must match the export (ml/scripts/export.py --imgsz). */
const val MODEL_INPUT_SIZE = 800
const val DEFAULT_CONFIDENCE = ACCEPT_SCORE

/** Result of one detector run. [imageWidth]/[imageHeight] are the upright source size. */
data class DetectionResult(
    val detections: List<Detection>,
    val imageWidth: Int,
    val imageHeight: Int,
    /** Time for writing the input, running the model and reading the output. */
    val inferenceMs: Float,
    /** Time for letterbox/rotate + float conversion. */
    val preprocessMs: Float,
    val backend: String,
    /** [laplacianVariance] of the model input (content area), for picking the sharpest frame. */
    val sharpness: Float = 0f,
)

/**
 * YOLO26 end-to-end card detector on LiteRT's CompiledModel API (GPU with CPU fallback).
 *
 * Not thread-safe: call it only from [DetectorHolder]'s single detector thread (the GPU context
 * and the reused buffers belong to that thread).
 */
class CardDetector private constructor(
    private val model: CompiledModel,
    val labels: List<String>,
    /** "GPU" or "CPU": the accelerator the model was compiled for. */
    val backend: String,
) : AutoCloseable {
    private val size = MODEL_INPUT_SIZE
    private val inputBuffers: List<TensorBuffer> = model.createInputBuffers()
    private val outputBuffers: List<TensorBuffer> = model.createOutputBuffers()
    private val inputBitmap = createBitmap(size, size)
    // DENSITY_NONE: never let Android rescale the source by its density (decoded photos and
    // camera bitmaps carry different densities); the matrix alone defines the mapping.
    private val canvas = Canvas(inputBitmap).apply { density = Bitmap.DENSITY_NONE }
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    // Not named `matrix`: inside canvas.withClip { } that name resolves to Canvas.getMatrix().
    private val inputTransform = Matrix()
    private val pixels = IntArray(size * size)
    private val input = FloatArray(3 * size * size)
    private val padColor = Color.rgb(LETTERBOX_PAD, LETTERBOX_PAD, LETTERBOX_PAD)

    var confidenceThreshold: Float = DEFAULT_CONFIDENCE

    /**
     * Runs the detector on [source]. Only its top-left [width]×[height] pixels are used (camera
     * buffers can be wider than the image because of row padding). [rotationDegrees] is the
     * clockwise rotation that makes the source upright; it is applied while letterboxing, so no
     * upright copy is made. Boxes come back normalized in upright source coordinates.
     * [threshold] defaults to [confidenceThreshold]; the live camera passes the lower [KEEP_SCORE].
     */
    fun detect(
        source: Bitmap,
        rotationDegrees: Int = 0,
        width: Int = source.width,
        height: Int = source.height,
        threshold: Float = confidenceThreshold,
    ): DetectionResult {
        val t0 = SystemClock.elapsedRealtimeNanos()
        val (uprightWidth, uprightHeight) = uprightSize(width, height, rotationDegrees)
        val letterbox = Letterbox.of(uprightWidth, uprightHeight, size)
        canvas.drawColor(padColor)
        inputTransform.setValues(bufferToInputMatrix(width, height, rotationDegrees, letterbox))
        canvas.withClip(
            letterbox.padX, letterbox.padY,
            letterbox.padX + letterbox.contentWidth, letterbox.padY + letterbox.contentHeight,
        ) {
            drawBitmap(source, inputTransform, paint)
        }
        inputBitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        argbToChwFloat(pixels, size, input)
        val sharpness = laplacianVariance(pixels, size, letterbox.padX, letterbox.padY, letterbox.contentWidth, letterbox.contentHeight)
        val t1 = SystemClock.elapsedRealtimeNanos()
        inputBuffers[0].writeFloat(input)
        model.run(inputBuffers, outputBuffers)
        val output = outputBuffers[0].readFloat()
        val t2 = SystemClock.elapsedRealtimeNanos()
        val detections = parseEndToEnd(output, labels, letterbox, threshold)
        return DetectionResult(
            detections = detections,
            imageWidth = uprightWidth,
            imageHeight = uprightHeight,
            inferenceMs = (t2 - t1) / 1e6f,
            preprocessMs = (t1 - t0) / 1e6f,
            backend = backend,
            sharpness = sharpness,
        )
    }

    override fun close() {
        inputBuffers.forEach { it.close() }
        outputBuffers.forEach { it.close() }
        model.close()
        inputBitmap.recycle()
    }

    companion object {
        /**
         * Loads [MODEL_ASSET] + [LABELS_ASSET]. Tries the GPU accelerator first and falls back to
         * CPU (XNNPACK) if GPU compilation or a warm-up run fails.
         */
        fun create(context: Context): CardDetector {
            val labels = context.assets.open(LABELS_ASSET).bufferedReader().readLines().filter { it.isNotBlank() }
            val attempts = listOf("GPU" to Accelerator.GPU, "CPU" to Accelerator.CPU)
                .filter { !DetectorHolder.forceCpu || it.second == Accelerator.CPU }
            var lastError: Exception? = null
            for ((name, accelerator) in attempts) {
                var model: CompiledModel? = null
                try {
                    val t0 = SystemClock.elapsedRealtime()
                    model = CompiledModel.create(context.assets, MODEL_ASSET, CompiledModel.Options(accelerator))
                    val detector = CardDetector(model, labels, name)
                    val t1 = SystemClock.elapsedRealtime()
                    // Warm-up: the first run compiles GPU kernels; failures here also trigger the fallback.
                    val warm = detector.detect(createBitmap(64, 48))
                    Log.i(TAG, "Loaded $MODEL_ASSET on $name: compile ${t1 - t0} ms, warm-up " +
                        "${SystemClock.elapsedRealtime() - t1} ms (inference ${warm.inferenceMs} ms), ${labels.size} labels")
                    return detector
                } catch (e: Exception) {
                    Log.w(TAG, "$name accelerator unavailable, trying next", e)
                    runCatching { model?.close() }
                    lastError = e
                }
            }
            throw IllegalStateException("Could not load $MODEL_ASSET on any accelerator", lastError)
        }
    }
}

/**
 * Process-wide detector on one dedicated thread. CameraX analysis and the photo screen both run
 * their detector calls on [executor] / [dispatcher], so the model is created once and never used
 * concurrently.
 */
object DetectorHolder {
    val executor: ExecutorService = Executors.newSingleThreadExecutor { r -> Thread(r, "card-detector") }
    val dispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()

    @Volatile private var detector: Result<CardDetector>? = null

    /** The detector if it was already created (or failed to), without creating it. */
    val loaded: Result<CardDetector>? get() = detector

    /** Debug builds only: skip the GPU accelerator (set before the first [get]). */
    @Volatile var forceCpu: Boolean = false

    /** Returns the detector, creating it on first use. Call only on [executor]. */
    fun getBlocking(context: Context): Result<CardDetector> =
        detector ?: runCatching { CardDetector.create(context.applicationContext) }
            .onFailure { Log.e(TAG, "Detector init failed", it) }
            .also { detector = it }

    suspend fun get(context: Context): Result<CardDetector> = withContext(dispatcher) { getBlocking(context) }
}

/** The bundled model: version line (from [MODEL_VERSION_ASSET], `null` if missing) and file size. */
data class ModelDetails(val version: String?, val bytes: Long)

/** [ModelDetails] of the bundled model, `null` if there is no model asset. */
fun readModelDetails(context: Context): ModelDetails? = runCatching {
    val version = runCatching {
        context.assets.open(MODEL_VERSION_ASSET).bufferedReader().readLine()?.trim()
    }.getOrNull()
    ModelDetails(version, context.assets.openFd(MODEL_ASSET).use { it.length })
}.getOrNull()

/** Footer text: model version string (from [MODEL_VERSION_ASSET]) and file size. */
fun readModelInfo(context: Context): String = readModelDetails(context)?.let { d ->
    val mb = "%.1f MB".format(java.util.Locale.ROOT, d.bytes / 1e6)
    "$MODEL_ASSET ${d.version ?: ""} ($mb)".replace("  ", " ")
} ?: "none"
