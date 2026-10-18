package net.moesli.scan7.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionTrackerTest {
    private val cardBox = Box(0.10f, 0.20f, 0.30f, 0.60f)
    private val otherBox = Box(0.50f, 0.20f, 0.70f, 0.60f)

    private fun det(label: String, score: Float, box: Box = cardBox) = Detection(label, score, box)

    /** Feeds [frames] and returns the visible tracks after each frame. */
    private fun run(tracker: DetectionTracker, frames: List<List<Detection>>) = frames.map { tracker.update(it) }

    @Test
    fun thresholdsAreOrdered() {
        assertTrue(KEEP_SCORE < ACCEPT_SCORE)
        assertEquals(0.20f, KEEP_SCORE)
        assertEquals(0.35f, ACCEPT_SCORE)
    }

    @Test
    fun needsTwoOfThreeFramesToAppear() {
        val out = run(DetectionTracker(), listOf(listOf(det("7", 0.9f)), listOf(det("7", 0.9f))))
        assertTrue(out[0].isEmpty())
        assertEquals(listOf("7"), out[1].map { it.detection.label })
        assertTrue(out[1].single().detection.fromTracking)
    }

    @Test
    fun oneFrameGhostNeverShows() {
        val frames = listOf(listOf(det("7", 0.95f))) + List(6) { emptyList<Detection>() }
        assertTrue(run(DetectionTracker(), frames).all { it.isEmpty() })
    }

    @Test
    fun weakDetectionsAloneNeverShow() {
        val frames = List(10) { listOf(det("7", 0.30f)) }
        assertTrue(run(DetectionTracker(), frames).all { it.isEmpty() })
    }

    @Test
    fun flickeringCardStaysVisible() {
        // Detected in every other frame (the NMS-free head drops it now and then).
        val frames = List(20) { i -> if (i % 2 == 0) listOf(det("7", 0.8f)) else emptyList() }
        val out = run(DetectionTracker(), frames)
        // Seen in frames 0 and 2: 2 of the last 3 → visible from frame 2 on, without a gap.
        assertTrue(out.take(2).all { it.isEmpty() })
        out.drop(2).forEach { assertEquals(listOf("7"), it.map { t -> t.detection.label }) }
    }

    @Test
    fun scoreOscillatingAroundThresholdStaysVisibleContinuously() {
        // Hysteresis: 0.45 (≥ accept) and 0.25 (only ≥ keep) alternating.
        val frames = List(30) { i -> listOf(det("9", if (i % 2 == 0) 0.45f else 0.25f)) }
        val out = run(DetectionTracker(), frames)
        assertTrue(out[0].isEmpty() && out[1].isEmpty())
        out.drop(2).forEach { tracks ->
            assertEquals(1, tracks.size)
            assertEquals(0, tracks.single().missed)
        }
    }

    @Test
    fun visibleTrackSurvivesWeakFramesButNotAStartFromWeak() {
        val tracker = DetectionTracker()
        run(tracker, listOf(listOf(det("5", 0.6f)), listOf(det("5", 0.6f))))
        // Now only weak matches: the track stays.
        val out = run(tracker, List(10) { listOf(det("5", 0.22f)) })
        assertTrue(out.all { it.size == 1 })
        // Below the keep threshold counts as missed.
        val gone = run(tracker, List(MAX_MISSES + 1) { listOf(det("5", 0.15f)) })
        assertEquals(1, gone[MAX_MISSES - 1].size)
        assertTrue(gone[MAX_MISSES].isEmpty())
    }

    @Test
    fun staysForFiveMissedFramesFadingOut() {
        val tracker = DetectionTracker()
        run(tracker, List(3) { listOf(det("7", 0.9f)) })
        val out = run(tracker, List(MAX_MISSES + 1) { emptyList() })
        for (m in 1..MAX_MISSES) {
            val t = out[m - 1].single()
            assertEquals(m, t.missed)
            assertTrue(t.alpha < 1f && t.alpha > 0f)
        }
        assertTrue(out[MAX_MISSES].isEmpty())
    }

    @Test
    fun labelWinsByScoreWeightedVote() {
        // 6 is seen 5×, 9 3× with higher single scores but a lower sum: 6 wins, although the
        // latest frame says 9.
        val labels = listOf("6", "6", "9", "6", "6", "9", "6", "9")
        val scores = mapOf("6" to 0.7f, "9" to 0.8f)
        val out = run(DetectionTracker(), labels.map { listOf(det(it, scores.getValue(it))) })
        assertEquals("6", out.last().single().detection.label)
    }

    @Test
    fun labelFollowsAChangeOnceItWinsTheVote() {
        val frames = List(4) { listOf(det("plus2", 0.6f)) } + List(8) { listOf(det("plus4", 0.6f)) }
        val out = run(DetectionTracker(), frames)
        assertEquals("plus2", out[4].single().detection.label)
        assertEquals("plus4", out.last().single().detection.label)
    }

    @Test
    fun boxIsSmoothed() {
        val tracker = DetectionTracker()
        run(tracker, List(2) { listOf(det("7", 0.9f)) })
        val moved = Box(0.12f, 0.20f, 0.32f, 0.60f)
        val t = tracker.update(listOf(det("7", 0.9f, moved))).single()
        assertEquals(0.11f, t.detection.box.left, 1e-4f)
        assertEquals(0.31f, t.detection.box.right, 1e-4f)
    }

    @Test
    fun twoCardsGetTwoTracks() {
        val frames = List(3) { listOf(det("7", 0.9f), det("8", 0.8f, otherBox)) }
        val out = run(DetectionTracker(), frames).last()
        assertEquals(setOf("7", "8"), out.map { it.detection.label }.toSet())
    }

    @Test
    fun resetForgetsTracks() {
        val tracker = DetectionTracker()
        run(tracker, List(3) { listOf(det("7", 0.9f)) })
        tracker.reset()
        assertTrue(tracker.visible().isEmpty())
        assertTrue(tracker.update(listOf(det("7", 0.9f))).isEmpty())
    }

    // ---- stableDetections (shutter frame) ----

    @Test
    fun shutterFrameKeepsWeakDetectionOfAStableCard() {
        val tracker = DetectionTracker()
        run(tracker, List(3) { listOf(det("7", 0.8f)) })
        val frame = listOf(det("7", 0.25f))
        val stable = stableDetections(frame, tracker.update(frame))
        assertEquals(listOf(det("7", 0.25f)), stable)
        assertFalse(stable.single().fromTracking)
    }

    @Test
    fun shutterFrameDropsWeakDetectionWithoutTrack() {
        val frame = listOf(det("7", 0.25f), det("8", 0.5f, otherBox))
        assertEquals(listOf("8"), stableDetections(frame, DetectionTracker().update(frame)).map { it.label })
    }

    @Test
    fun shutterFrameMissingAStableCardGetsItFromTracking() {
        val tracker = DetectionTracker()
        run(tracker, List(4) { listOf(det("7", 0.8f), det("12", 0.9f, otherBox)) })
        val frame = listOf(det("12", 0.9f, otherBox))
        val stable = stableDetections(frame, tracker.update(frame))
        assertEquals(listOf("12", "7"), stable.map { it.label })
        val filled = stable.single { it.label == "7" }
        assertTrue(filled.fromTracking)
        assertEquals(cardBox.left, filled.box.left, 1e-4f)
    }

    @Test
    fun noFillInForALongGoneTrackOrACoveredOne() {
        val tracker = DetectionTracker()
        run(tracker, List(4) { listOf(det("7", 0.8f)) })
        repeat(FILL_MAX_MISSES) { tracker.update(emptyList()) }
        // Missed FILL_MAX_MISSES + 1 frames now: still visible (fading) but not filled in.
        assertTrue(stableDetections(emptyList(), tracker.update(emptyList())).isEmpty())
        // A shifted detection of the same card covers it (no duplicate → no false bust).
        val tracker2 = DetectionTracker()
        run(tracker2, List(4) { listOf(det("7", 0.8f)) })
        val shifted = listOf(det("7", 0.8f, Box(0.19f, 0.20f, 0.39f, 0.60f)))
        assertEquals(1, stableDetections(shifted, tracker2.update(shifted)).size)
    }
}
