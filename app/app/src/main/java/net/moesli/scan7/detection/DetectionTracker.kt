package net.moesli.scan7.detection

/*
 * Temporal smoothing of the live detections (brief milestone 6), plain Kotlin (JVM tests in
 * DetectionTrackerTest). All thresholds of the live camera and the shutter live here.
 *
 * Hysteresis: the live detector runs with the low [KEEP_SCORE]. A new card needs [ACCEPT_SCORE]
 * in [CONFIRM_HITS] of the last [CONFIRM_WINDOW] frames to appear; once visible, any match
 * ≥ [KEEP_SCORE] keeps it alive, so a card whose score hovers around [ACCEPT_SCORE] (or that the
 * NMS-free head drops now and then) doesn't blink.
 */

/** Score a detection needs to start a track or to count on its own (also the photo/debug threshold). */
const val ACCEPT_SCORE = 0.35f

/** Raw threshold of the live detector: a match this strong keeps an existing track alive. */
const val KEEP_SCORE = 0.20f

/** A detection continues a track when its box overlaps the track's box by at least this IoU. */
const val TRACK_MATCH_IOU = 0.4f

/** A track becomes visible after [CONFIRM_HITS] detections ≥ [ACCEPT_SCORE] in the last [CONFIRM_WINDOW] frames. */
const val CONFIRM_HITS = 2
const val CONFIRM_WINDOW = 3

/** A visible track survives this many frames without a match (fading out), then it is dropped. */
const val MAX_MISSES = 5

/** The track's label is the score-weighted majority over its last [LABEL_WINDOW] frames. */
const val LABEL_WINDOW = 8

/** Box smoothing: box = [BOX_EMA] · detection + (1 − [BOX_EMA]) · box. */
const val BOX_EMA = 0.5f

/** Shutter fill-in: a track missing in the chosen frame is added only if it was matched at most this many frames ago… */
const val FILL_MAX_MISSES = 2

/** …and no kept detection of that frame overlaps it by this IoU or more (the card is already there). */
const val FILL_COVER_IOU = 0.3f

/**
 * A visible track: [detection] carries the voted label, the mean score of that label and the
 * smoothed box. [missed] = frames since the last match (0 = seen in this frame).
 */
data class TrackedDetection(val id: Long, val detection: Detection, val missed: Int) {
    /** 1 when seen in this frame, fading linearly to ~0.17 at [MAX_MISSES]. */
    val alpha: Float get() = 1f - missed / (MAX_MISSES + 1f)
}

/** Frame-by-frame tracker. Not thread-safe: call it from one thread (the detector thread). */
class DetectionTracker {
    private class Track(val id: Long, var box: Box) {
        /** Last [CONFIRM_WINDOW] frames: matched by a detection ≥ [ACCEPT_SCORE]. */
        val strong = ArrayDeque<Boolean>()
        /** Last [LABEL_WINDOW] frames: the matched detection or null. */
        val votes = ArrayDeque<Detection?>()
        var misses = 0
        var confirmed = false

        fun push(detection: Detection?) {
            strong.addLast(detection != null && detection.score >= ACCEPT_SCORE)
            if (strong.size > CONFIRM_WINDOW) strong.removeFirst()
            votes.addLast(detection)
            if (votes.size > LABEL_WINDOW) votes.removeFirst()
            if (detection == null) {
                misses++
            } else {
                misses = 0
                box = lerp(box, detection.box, BOX_EMA)
                if (strong.count { it } >= CONFIRM_HITS) confirmed = true
            }
        }

        /** An unconfirmed track whose last two frames had no strong hit can't reach 2 of 3 any more. */
        val dead: Boolean
            get() = if (confirmed) misses > MAX_MISSES else strong.size >= 2 && strong.takeLast(2).none { it }

        val visible: Boolean get() = confirmed && misses <= MAX_MISSES

        fun output(): TrackedDetection {
            val weights = HashMap<String, Float>()
            val counts = HashMap<String, Int>()
            for (d in votes) {
                if (d == null) continue
                weights[d.label] = (weights[d.label] ?: 0f) + d.score
                counts[d.label] = (counts[d.label] ?: 0) + 1
            }
            val label = weights.maxBy { it.value }.key
            val score = weights.getValue(label) / counts.getValue(label)
            return TrackedDetection(id, Detection(label, score, box, fromTracking = true), misses)
        }
    }

    private val tracks = ArrayList<Track>()
    private var nextId = 1L

    /**
     * Feeds one frame's raw [detections] (any score ≥ [KEEP_SCORE]) and returns the visible tracks
     * by descending score, near-identical tracks merged as in [suppressDuplicates].
     */
    fun update(detections: List<Detection>): List<TrackedDetection> {
        val candidates = detections.filter { it.score >= KEEP_SCORE }
        // Greedy matching by IoU, label-agnostic (a flickering label must not split the track).
        val pairs = ArrayList<Triple<Int, Int, Float>>()
        for ((ti, t) in tracks.withIndex()) {
            for ((di, d) in candidates.withIndex()) {
                val o = iou(t.box, d.box)
                if (o >= TRACK_MATCH_IOU) pairs.add(Triple(ti, di, o))
            }
        }
        pairs.sortByDescending { it.third }
        val trackMatch = arrayOfNulls<Detection>(tracks.size)
        val used = BooleanArray(candidates.size)
        for ((ti, di, _) in pairs) {
            if (trackMatch[ti] != null || used[di]) continue
            trackMatch[ti] = candidates[di]
            used[di] = true
        }
        tracks.forEachIndexed { i, t -> t.push(trackMatch[i]) }
        tracks.removeAll { it.dead }
        for ((di, d) in candidates.withIndex()) {
            if (!used[di] && d.score >= ACCEPT_SCORE) tracks.add(Track(nextId++, d.box).apply { push(d) })
        }
        return visible()
    }

    /** The visible tracks of the last [update]. */
    fun visible(): List<TrackedDetection> {
        val out = tracks.filter { it.visible }.map { it.output() }.sortedByDescending { it.detection.score }
        val kept = suppressDuplicates(out.map { it.detection }).toSet()
        return out.filter { it.detection in kept }
    }

    fun reset() {
        tracks.clear()
    }
}

/**
 * What one frame shows after hysteresis, given the visible [tracks] after that frame's update:
 * its own detections ≥ [ACCEPT_SCORE], its weaker ones (≥ [KEEP_SCORE]) that continue a visible
 * track, plus tracks the frame missed (seen ≤ [FILL_MAX_MISSES] frames ago, not covered by a kept
 * detection) with the track's label and box, marked [Detection.fromTracking]. By descending score,
 * duplicates suppressed.
 */
fun stableDetections(frame: List<Detection>, tracks: List<TrackedDetection>): List<Detection> {
    val kept = frame.filter { d ->
        d.score >= ACCEPT_SCORE ||
            (d.score >= KEEP_SCORE && tracks.any { iou(it.detection.box, d.box) >= TRACK_MATCH_IOU })
    }
    val filled = tracks.filter { t ->
        t.missed in 1..FILL_MAX_MISSES && kept.none { iou(it.box, t.detection.box) >= FILL_COVER_IOU }
    }.map { it.detection.copy(fromTracking = true) }
    return suppressDuplicates((kept + filled).sortedByDescending { it.score })
}

private fun lerp(a: Box, b: Box, t: Float): Box = Box(
    a.left + (b.left - a.left) * t,
    a.top + (b.top - a.top) * t,
    a.right + (b.right - a.right) * t,
    a.bottom + (b.bottom - a.bottom) * t,
)
