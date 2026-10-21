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
import net.moesli.scan7.scoring.Deck

private const val TAG = "CardDetector"

const val MODEL_ASSET = "model.tflite"
const val LABELS_ASSET = "labels.txt"
const val MODEL_VERSION_ASSET = "model_version.txt"

/**
 * A bundled detector model and its assets: Flip 7's at the assets root, every other game's in its
 * own folder (ml/scripts/export.py --asset-dir). Each game's labels are its [Deck]'s card labels.
 */
enum class DetectorModel(dir: String) {
    FLIP7(""),
    VENGEANCE("vengeance/"),
    ;

    val modelAsset = dir + MODEL_ASSET
    val labelsAsset = dir + LABELS_ASSET
    val versionAsset = dir + MODEL_VERSION_ASSET

    /** Whether this build bundles the model (the assets are not in git; a fresh clone may lack one). */
    fun isBundled(context: Context): Boolean =
        runCatching { context.assets.openFd(modelAsset).close() }.isSuccess

    companion object {
        fun of(deck: Deck): DetectorModel = when (deck) {
            Deck.FLIP7 -> FLIP7
            Deck.VENGEANCE -> VENGEANCE
        }
    }
}

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
         * Loads [model]'s model and labels assets. Tries the GPU accelerator first and falls back to
         * CPU (XNNPACK) if GPU compilation or a warm-up run fails.
         */
        fun create(context: Context, model: DetectorModel = DetectorModel.FLIP7): CardDetector {
            val asset = model.modelAsset
            val labels = context.assets.open(model.labelsAsset).bufferedReader().readLines().filter { it.isNotBlank() }
            val attempts = listOf("GPU" to Accelerator.GPU, "CPU" to Accelerator.CPU)
                .filter { !DetectorHolder.forceCpu || it.second == Accelerator.CPU }
            var lastError: Exception? = null
            for ((name, accelerator) in attempts) {
                var compiled: CompiledModel? = null
                try {
                    val t0 = SystemClock.elapsedRealtime()
                    compiled = CompiledModel.create(context.assets, asset, CompiledModel.Options(accelerator))
                    val detector = CardDetector(compiled, labels, name)
                    val t1 = SystemClock.elapsedRealtime()
                    // Warm-up: the first run compiles GPU kernels; failures here also trigger the fallback.
                    val warm = detector.detect(createBitmap(64, 48))
                    Log.i(TAG, "Loaded $asset on $name: compile ${t1 - t0} ms, warm-up " +
                        "${SystemClock.elapsedRealtime() - t1} ms (inference ${warm.inferenceMs} ms), ${labels.size} labels")
                    return detector
                } catch (e: Exception) {
                    Log.w(TAG, "$name accelerator unavailable, trying next", e)
                    runCatching { compiled?.close() }
                    lastError = e
                }
            }
            throw IllegalStateException("Could not load $asset on any accelerator", lastError)
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
    private var model: DetectorModel? = null

    /** The detector if it was already created (or failed to), without creating it. */
    val loaded: Result<CardDetector>? get() = detector

    /** Debug builds only: skip the GPU accelerator (set before the first [get]). */
    @Volatile var forceCpu: Boolean = false

    /**
     * Returns [model]'s detector, creating it on first use. Only one model is loaded at a time:
     * asking for another one closes the current one (each holds ~10 MB plus its GPU buffers).
     * Call only on [executor].
     */
    fun getBlocking(context: Context, model: DetectorModel = DetectorModel.FLIP7): Result<CardDetector> {
        detector?.takeIf { this.model == model }?.let { return it }
        detector?.getOrNull()?.close()
        return runCatching { CardDetector.create(context.applicationContext, model) }
            .onFailure { Log.e(TAG, "Detector init failed ($model)", it) }
            .also {
                detector = it
                this.model = model
            }
    }

    suspend fun get(context: Context, model: DetectorModel = DetectorModel.FLIP7): Result<CardDetector> =
        withContext(dispatcher) { getBlocking(context, model) }
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
