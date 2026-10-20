package net.moesli.scan7.training

import java.time.ZoneId
import java.time.ZoneOffset
import net.moesli.scan7.detection.Box
import net.moesli.scan7.detection.Detection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingSampleTest {
    private val utc = ZoneOffset.UTC

    // 2026-10-18 13:24:05.123 UTC
    private val t = 1792329845123L

    private val sample = TrainingSample(
        timestampMillis = t,
        appVersion = "1.0 (1)",
        modelVersion = "synth_v1/ep15",
        zoomRatio = 1.0f,
        rotationDegrees = 90,
        imageWidth = 960,
        imageHeight = 1280,
        backend = "GPU",
        threshold = 0.35f,
        origin = "camera",
        detections = listOf(Detection("7", 0.91f, Box(0.1f, 0.2f, 0.3f, 0.5f)), Detection("freeze", 0.5f, Box(0f, 0f, 0.25f, 0.25f), fromTracking = true)),
        outcome = TrainingOutcome("scored", "manual_after_scan", listOf("7", "x2"), listOf("freeze"), 14),
        capture = TrainingCapture(bestOf = 6, chosenOffsetMs = -120, selectionScore = 3.25f, sharpness = 410.5f, keepThreshold = 0.2f),
    )

    @Test
    fun fileStem() {
        assertEquals("scan7_20261018_132405_123", sample.fileStem(utc))
    }

    @Test
    fun timestampKeepsZeroMillisAndOffset() {
        val zurich = ZoneId.of("Europe/Zurich") // CEST (+02:00) on 2026-10-18
        val whole = sample.copy(timestampMillis = t - 123)
        assertTrue(whole.toJson(zurich), whole.toJson(zurich).contains("\"timestamp\": \"2026-10-18T15:24:05.000+02:00\""))
        assertEquals("scan7_20261018_152405_000", whole.fileStem(zurich))
    }

    @Test
    fun jsonHasAllFields() {
        val json = sample.toJson(utc)
        listOf(
            "\"timestamp\": \"2026-10-18T13:24:05.123Z\"",
            "\"timestamp_millis\": $t",
            "\"app_version\": \"1.0 (1)\"",
            "\"model_version\": \"synth_v1/ep15\"",
            "\"zoom_ratio\": 1.0",
            "\"rotation_degrees\": 90",
            "\"image\": {\"width\": 960, \"height\": 1280}",
            "\"detector\": {\"backend\": \"GPU\", \"threshold\": 0.35}",
            "\"origin\": \"camera\"",
            "{\"label\": \"7\", \"score\": 0.91, \"box\": {\"left\": 0.1, \"top\": 0.2, \"right\": 0.3, \"bottom\": 0.5}, \"from_tracking\": false, \"excluded\": false, \"auto_excluded\": false}",
            "\"label\": \"freeze\", \"score\": 0.5, \"box\": {\"left\": 0.0, \"top\": 0.0, \"right\": 0.25, \"bottom\": 0.25}, \"from_tracking\": true, \"excluded\": false, \"auto_excluded\": false}",
            "\"capture\": {\"best_of\": 6, \"chosen_offset_ms\": -120, \"selection_score\": 3.25, \"sharpness\": 410.5, \"keep_threshold\": 0.2}",
            "\"detections_from_tracking\": 1",
            "\"outcome\": {\"kind\": \"scored\", \"source\": \"manual_after_scan\", \"points\": 14, \"cards\": [\"7\", \"x2\"], \"ignored\": [\"freeze\"], \"excluded\": []}",
        ).forEach { assertTrue("missing $it in\n$json", json.contains(it)) }
        assertBalanced(json)
    }

    @Test
    fun excludedDetectionsAndOutcome() {
        val json = sample.copy(
            outcome = TrainingOutcome("scored", "scan", listOf("x2"), emptyList(), 0, excluded = listOf("7")),
            excludedDetections = setOf(0),
        ).toJson(utc)
        assertTrue(json, json.contains("\"bottom\": 0.5}, \"from_tracking\": false, \"excluded\": true, \"auto_excluded\": false}"))
        assertTrue(json, json.contains("\"from_tracking\": true, \"excluded\": false, \"auto_excluded\": false}"))
        assertTrue(json, json.contains("\"ignored\": [], \"excluded\": [\"7\"]}"))
        assertBalanced(json)
    }

    @Test
    fun autoExcludedDetections() {
        // The app left the freeze out, the user brought it back: auto_excluded stays, excluded is the final choice.
        val json = sample.copy(autoExcludedDetections = setOf(1), excludedDetections = emptySet()).toJson(utc)
        assertTrue(json, json.contains("\"from_tracking\": true, \"excluded\": false, \"auto_excluded\": true}"))
        assertTrue(json, json.contains("\"from_tracking\": false, \"excluded\": false, \"auto_excluded\": false}"))
        assertBalanced(json)
    }

    @Test
    fun unconfirmedAndEmpty() {
        val json = sample.copy(outcome = null, detections = emptyList(), zoomRatio = null, rotationDegrees = null, modelVersion = null, capture = null).toJson(utc)
        assertTrue(json.contains("\"outcome\": null"))
        assertTrue(json.contains("\"capture\": null"))
        assertTrue(json.contains("\"detections\": []"))
        assertTrue(json.contains("\"zoom_ratio\": null"))
        assertTrue(json.contains("\"model_version\": null"))
        assertBalanced(json)
    }

    @Test
    fun stringsAreEscaped() {
        assertEquals("\"a\\\"b\\\\c\\nd\\u0001\"", str("a\"b\\c\nd\u0001"))
    }

    private fun assertBalanced(json: String) {
        assertEquals(json.count { it == '{' }, json.count { it == '}' })
        assertEquals(json.count { it == '[' }, json.count { it == ']' })
        assertTrue(json.trim().startsWith("{") && json.trim().endsWith("}"))
    }

    @Test
    fun onlyCameraCapturesAreTrainingData() {
        assertTrue(isTrainingOrigin(ORIGIN_CAMERA))
        assertFalse(isTrainingOrigin("photo"))
        assertFalse(isTrainingOrigin("synthetic"))
    }
}
