package net.moesli.scan7.detection

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Gray used for letterbox padding, as in Ultralytics (114/255). */
const val LETTERBOX_PAD = 114

/**
 * Centered letterbox of a [srcWidth]×[srcHeight] upright image into a [size]×[size] model input,
 * keeping the aspect ratio. Same rounding as Ultralytics' LetterBox (and ml/scripts/export.py).
 */
data class Letterbox(
    val srcWidth: Int,
    val srcHeight: Int,
    val size: Int,
    val scale: Float,
    val padX: Int,
    val padY: Int,
    val contentWidth: Int,
    val contentHeight: Int,
) {
    /** Maps a box in model-input pixels to 0–1 source-image coordinates, clamped to the image. */
    fun inputToNormalized(x1: Float, y1: Float, x2: Float, y2: Float): Box = Box(
        left = ((x1 - padX) / scale / srcWidth).coerceIn(0f, 1f),
        top = ((y1 - padY) / scale / srcHeight).coerceIn(0f, 1f),
        right = ((x2 - padX) / scale / srcWidth).coerceIn(0f, 1f),
        bottom = ((y2 - padY) / scale / srcHeight).coerceIn(0f, 1f),
    )

    /** Inverse of [inputToNormalized] (no clamping): normalized source box → model-input pixels. */
    fun normalizedToInput(box: Box): Box = Box(
        left = box.left * srcWidth * scale + padX,
        top = box.top * srcHeight * scale + padY,
        right = box.right * srcWidth * scale + padX,
        bottom = box.bottom * srcHeight * scale + padY,
    )

    companion object {
        fun of(srcWidth: Int, srcHeight: Int, size: Int): Letterbox {
            require(srcWidth > 0 && srcHeight > 0 && size > 0) { "bad size $srcWidth×$srcHeight → $size" }
            val scale = min(size.toFloat() / srcWidth, size.toFloat() / srcHeight)
            val w = (srcWidth * scale).roundToInt().coerceIn(1, size)
            val h = (srcHeight * scale).roundToInt().coerceIn(1, size)
            val padX = ((size - w) / 2f - 0.1f).roundToInt()
            val padY = ((size - h) / 2f - 0.1f).roundToInt()
            return Letterbox(srcWidth, srcHeight, size, scale, padX, padY, w, h)
        }
    }
}

/** Upright size of a buffer of [width]×[height] that must be rotated by [rotationDegrees] clockwise. */
fun uprightSize(width: Int, height: Int, rotationDegrees: Int): Pair<Int, Int> =
    if (normalizeRotation(rotationDegrees) % 180 == 0) width to height else height to width

private fun normalizeRotation(degrees: Int): Int {
    val r = ((degrees % 360) + 360) % 360
    require(r % 90 == 0) { "rotation must be a multiple of 90, was $degrees" }
    return r
}

/**
 * Affine transform (3×3 row-major, as `android.graphics.Matrix.setValues` expects) mapping pixel
 * coordinates of a [width]×[height] camera buffer that needs [rotationDegrees] clockwise rotation
 * to be upright, into model-input pixels of [letterbox] (which is built on the upright size).
 */
fun bufferToInputMatrix(width: Int, height: Int, rotationDegrees: Int, letterbox: Letterbox): FloatArray {
    // Rotation buffer → upright: x' = a*x + b*y + c, y' = d*x + e*y + f.
    val (a, b, c, d, e, f) = when (normalizeRotation(rotationDegrees)) {
        0 -> Affine(1f, 0f, 0f, 0f, 1f, 0f)
        90 -> Affine(0f, -1f, height.toFloat(), 1f, 0f, 0f)
        180 -> Affine(-1f, 0f, width.toFloat(), 0f, -1f, height.toFloat())
        else -> Affine(0f, 1f, 0f, -1f, 0f, width.toFloat())
    }
    val s = letterbox.scale
    return floatArrayOf(
        s * a, s * b, s * c + letterbox.padX,
        s * d, s * e, s * f + letterbox.padY,
        0f, 0f, 1f,
    )
}

private data class Affine(val a: Float, val b: Float, val c: Float, val d: Float, val e: Float, val f: Float)

/** Applies a 3×3 row-major affine [m] to (x, y). */
fun FloatArray.mapPoint(x: Float, y: Float): Pair<Float, Float> =
    (this[0] * x + this[1] * y + this[2]) to (this[3] * x + this[4] * y + this[5])

/**
 * Converts ARGB pixels (row-major [size]×[size], as `Bitmap.getPixels` returns) into a planar
 * NCHW float tensor: RGB in 0–1. [out] must hold 3·size² floats.
 */
fun argbToChwFloat(pixels: IntArray, size: Int, out: FloatArray) {
    val plane = size * size
    require(pixels.size >= plane && out.size >= 3 * plane)
    for (i in 0 until plane) {
        val p = pixels[i]
        out[i] = ((p shr 16) and 0xFF) / 255f
        out[plane + i] = ((p shr 8) and 0xFF) / 255f
        out[2 * plane + i] = (p and 0xFF) / 255f
    }
}

/** Values per detection row of the YOLO26 end-to-end head: x1, y1, x2, y2, score, class. */
const val E2E_ROW = 6

/**
 * Parses the end-to-end (NMS-free) YOLO output `[1, N, 6]` = x1, y1, x2, y2 (model-input pixels),
 * score, class id. Keeps rows with score ≥ [threshold] and a known class, maps boxes back through
 * [letterbox] to normalized upright image coordinates, drops boxes that collapse to nothing after
 * clamping, and returns them by descending score.
 */
fun parseEndToEnd(output: FloatArray, labels: List<String>, letterbox: Letterbox, threshold: Float): List<Detection> {
    val result = ArrayList<Detection>()
    var i = 0
    while (i + E2E_ROW <= output.size) {
        val score = output[i + 4]
        if (score >= threshold) {
            val cls = output[i + 5].roundToInt()
            val label = labels.getOrNull(cls)
            if (label != null) {
                val box = letterbox.inputToNormalized(output[i], output[i + 1], output[i + 2], output[i + 3])
                if (box.width > 0f && box.height > 0f) result.add(Detection(label, score, box))
            }
        }
        i += E2E_ROW
    }
    result.sortByDescending { it.score }
    return result
}

/**
 * How an image of [imageWidth]×[imageHeight] is shown centered in a [viewWidth]×[viewHeight] view:
 * [crop] = fill and crop (camera preview FILL_CENTER / ContentScale.Crop), else fit (letterboxed).
 */
data class DisplayMapping(val scale: Float, val offsetX: Float, val offsetY: Float, val imageWidth: Int, val imageHeight: Int) {
    /** Normalized image box → view pixels. */
    fun toView(box: Box): Box = Box(
        offsetX + box.left * imageWidth * scale,
        offsetY + box.top * imageHeight * scale,
        offsetX + box.right * imageWidth * scale,
        offsetY + box.bottom * imageHeight * scale,
    )

    /** View pixel → normalized image coordinates (may be outside 0–1 for letterbox bars). */
    fun toImage(x: Float, y: Float): Pair<Float, Float> =
        (x - offsetX) / scale / imageWidth to (y - offsetY) / scale / imageHeight

    companion object {
        fun of(imageWidth: Int, imageHeight: Int, viewWidth: Float, viewHeight: Float, crop: Boolean): DisplayMapping {
            val sx = viewWidth / imageWidth
            val sy = viewHeight / imageHeight
            val scale = if (crop) max(sx, sy) else min(sx, sy)
            return DisplayMapping(
                scale = scale,
                offsetX = (viewWidth - imageWidth * scale) / 2f,
                offsetY = (viewHeight - imageHeight * scale) / 2f,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
            )
        }
    }
}
