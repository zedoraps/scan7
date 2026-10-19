package net.moesli.scan7.training

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import net.moesli.scan7.detection.Detection

/*
 * Plain-Kotlin part of "Save scans for training": the JSON sidecar and file names. No Android
 * dependencies (manual JSON, no org.json) so it is JVM unit tested (TrainingSampleTest).
 */

/** [TrainingSample.origin] of a real shutter photo. */
const val ORIGIN_CAMERA = "camera"

/**
 * Only real shutter photos are training data. Captures from the debug hooks (`debug_game_photo`,
 * `debug_scan_photo`: "photo"; the scan-flow demo: "synthetic") are never saved, so emulator
 * end-to-end runs don't fill the shared "Scan7 Training" folder with copies of one photo.
 */
fun isTrainingOrigin(origin: String): Boolean = origin == ORIGIN_CAMERA

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
    /** Detections the user left out on the review (e.g. a neighbour's card; detector labels). */
    val excluded: List<String> = emptyList(),
)

/** How the best-frame shutter chose the frame: `"capture": {"best_of", "chosen_offset_ms", "selection_score", …}`. */
data class TrainingCapture(
    /** Buffered frames compared. */
    val bestOf: Int,
    /** Chosen frame's arrival relative to the shutter press in ms (negative = before the press). */
    val chosenOffsetMs: Long,
    val selectionScore: Float,
    val sharpness: Float,
    /** Live detector threshold (hysteresis: tracks stay alive down to this score). */
    val keepThreshold: Float,
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
    /** Best-frame shutter info; null for debug captures (photo file / synthetic). */
    val capture: TrainingCapture? = null,
    /** Indices into [detections] the user left out on the review (`"excluded": true` per detection). */
    val excludedDetections: Set<Int> = emptySet(),
) {
    /** File name without extension: `scan7_<yyyyMMdd_HHmmss_SSS>` in [zone]. */
    fun fileStem(zone: ZoneId = ZoneId.systemDefault()): String = trainingFileStem(timestampMillis, zone)

    fun toJson(zone: ZoneId = ZoneId.systemDefault()): String {
        val timestamp = IsoTimestamp.format(Instant.ofEpochMilli(timestampMillis).atZone(zone))
        return buildString {
            append("{\n")
            field("timestamp", str(timestamp))
            field("timestamp_millis", timestampMillis.toString())
            field("app_version", str(appVersion))
            field("model_version", modelVersion?.let(::str) ?: "null")
            field("zoom_ratio", num(zoomRatio))
            field("rotation_degrees", rotationDegrees?.toString() ?: "null")
            field("image", "{\"width\": $imageWidth, \"height\": $imageHeight}")
            field("detector", "{\"backend\": ${backend?.let(::str) ?: "null"}, \"threshold\": ${num(threshold)}}")
            field("origin", str(origin))
            val c = capture
            field(
                "capture",
                if (c == null) {
                    "null"
                } else {
                    "{\"best_of\": ${c.bestOf}, \"chosen_offset_ms\": ${c.chosenOffsetMs}, " +
                        "\"selection_score\": ${num(c.selectionScore)}, \"sharpness\": ${num(c.sharpness)}, " +
                        "\"keep_threshold\": ${num(c.keepThreshold)}}"
                },
            )
            field("detections_from_tracking", detections.count { it.fromTracking }.toString())
            field(
                "detections",
                detections.withIndex().joinToString(",\n    ", "[\n    ", "\n  ]") { (i, d) ->
                    "{\"label\": ${str(d.label)}, \"score\": ${num(d.score)}, \"box\": " +
                        "{\"left\": ${num(d.box.left)}, \"top\": ${num(d.box.top)}, " +
                        "\"right\": ${num(d.box.right)}, \"bottom\": ${num(d.box.bottom)}}, " +
                        "\"from_tracking\": ${d.fromTracking}, \"excluded\": ${i in excludedDetections}}"
                }.let { if (detections.isEmpty()) "[]" else it },
            )
            val o = outcome
            val outcomeJson = if (o == null) {
                "null"
            } else {
                "{\"kind\": ${str(o.kind)}, \"source\": ${str(o.source)}, \"points\": ${o.points}, " +
                    "\"cards\": ${strList(o.cards)}, \"ignored\": ${strList(o.ignored)}, \"excluded\": ${strList(o.excluded)}}"
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

fun trainingFileStem(timestampMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    "scan7_" + StemTimestamp.format(Instant.ofEpochMilli(timestampMillis).atZone(zone))

// java.time formatters are immutable and thread-safe (SimpleDateFormat is neither). Explicit
// pattern instead of ISO_OFFSET_DATE_TIME, which drops ".000" millis; keep the format the importer reads.
private val IsoTimestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT)
private val StemTimestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS", Locale.ROOT)

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
