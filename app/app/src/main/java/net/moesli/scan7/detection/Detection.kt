package net.moesli.scan7.detection

import android.graphics.RectF

/** One detected card. [box] is normalized to 0–1 in upright image coordinates. */
data class Detection(val label: String, val score: Float, val box: RectF)
