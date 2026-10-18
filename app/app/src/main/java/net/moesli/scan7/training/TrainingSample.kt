package net.moesli.scan7.training

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import net.moesli.scan7.detection.Detection

/*
 * Plain-Kotlin part of "Save scans for training": the JSON sidecar and file names. No Android
 * dependencies (manual JSON, no org.json) so it is JVM unit tested (TrainingSampleTest).
 */

/** What the user confirmed for the scan; `null` in a sample saved from the review before confirming. */
data class TrainingOutcome(
    /** "scored" or "bust". */
    val kind: String,
    /** "scan" (OK / Confirm bust on the review) or "manual_after_scan" (saved via Fix / manual). */
    val source: String,
    /** Confirmed scoring cards (detector labels). */
    val cards: List<String>,
    /** Action cards / backs the user kept as ignored (detector labels). */
    val ignored: List<String>,
    val points: Int,
)

/** Everything stored next to the JPEG. Boxes are normalized 0–1 in the upright image. */
data class TrainingSample(
    val timestampMillis: Long,
    val appVersion: String,
    val modelVersion: String?,
    val zoomRatio: Float?,
    /** Clockwise rotation that was applied to make the camera buffer upright; `null` for a photo file. */
    val rotationDegrees: Int?,
    val imageWidth: Int,
    val imageHeight: Int,
    val backend: String?,
    val threshold: Float?,
    /** "camera", or "photo" / "synthetic" for debug captures. */
    val origin: String,
    val detections: List<Detection>,
    val outcome: TrainingOutcome?,
) {
    /** File name without extension: `scan7_<yyyyMMdd_HHmmss_SSS>` in [zone]. */
    fun fileStem(zone: TimeZone = TimeZone.getDefault()): String = trainingFileStem(timestampMillis, zone)

    fun toJson(zone: TimeZone = TimeZone.getDefault()): String {
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT).apply { timeZone = zone }
        return buildString {
            append("{\n")
            field("timestamp", str(iso.format(Date(timestampMillis))))
            field("timestamp_millis", timestampMillis.toString())
            field("app_version", str(appVersion))
            field("model_version", modelVersion?.let(::str) ?: "null")
            field("zoom_ratio", num(zoomRatio))
            field("rotation_degrees", rotationDegrees?.toString() ?: "null")
            field("image", "{\"width\": $imageWidth, \"height\": $imageHeight}")
            field("detector", "{\"backend\": ${backend?.let(::str) ?: "null"}, \"threshold\": ${num(threshold)}}")
            field("origin", str(origin))
            field(
                "detections",
                detections.joinToString(",\n    ", "[\n    ", "\n  ]") { d ->
                    "{\"label\": ${str(d.label)}, \"score\": ${num(d.score)}, \"box\": " +
                        "{\"left\": ${num(d.box.left)}, \"top\": ${num(d.box.top)}, " +
                        "\"right\": ${num(d.box.right)}, \"bottom\": ${num(d.box.bottom)}}}"
                }.let { if (detections.isEmpty()) "[]" else it },
            )
            val o = outcome
            val outcomeJson = if (o == null) {
                "null"
            } else {
                "{\"kind\": ${str(o.kind)}, \"source\": ${str(o.source)}, \"points\": ${o.points}, " +
                    "\"cards\": ${strList(o.cards)}, \"ignored\": ${strList(o.ignored)}}"
            }
            field("outcome", outcomeJson, last = true)
            append("}\n")
        }
    }

    private fun StringBuilder.field(name: String, value: String, last: Boolean = false) {
        append("  ").append(str(name)).append(": ").append(value)
        append(if (last) "\n" else ",\n")
    }
}

fun trainingFileStem(timestampMillis: Long, zone: TimeZone = TimeZone.getDefault()): String =
    "scan7_" + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ROOT).apply { timeZone = zone }.format(Date(timestampMillis))

private fun strList(values: List<String>): String = values.joinToString(", ", "[", "]") { str(it) }

/** Finite floats as plain decimals (Float.toString), anything else as null. */
private fun num(value: Float?): String = if (value == null || value.isNaN() || value.isInfinite()) "null" else value.toString()

/** JSON string literal with escaping. */
internal fun str(value: String): String = buildString {
    append('"')
    for (c in value) {
        when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c == '\n' -> append("\\n")
            c == '\r' -> append("\\r")
            c == '\t' -> append("\\t")
            c < ' ' -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
    }
    append('"')
}
