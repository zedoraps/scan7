package net.moesli.scan7.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StampedPathEffectStyle
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The avatar pattern (design/avatars) redrawn for a thin band — the score ring's arc, a progress
 * strip segment — where the kit's 100 × 100 tiles would only be noise. Each pattern becomes one
 * row of shapes stamped along the band's centre line (rotated with it) and scaled to the band
 * width: stripes = slanted bars, dots = a row of dots, zigzag / waves = one zigzag / wavy line,
 * grid = cross bars + a centre line. Ink = the kit's: ground `#0A1220` at ~30 %.
 *
 * Built once per (pattern, band width, path length) — callers keep it in `drawWithCache` — and
 * drawn with [drawStamps], so nothing is allocated while drawing.
 */
internal class BandPattern private constructor(
    effect: PathEffect,
    private val centerLine: Stroke?,
    private val ink: Color,
) {
    private val stampStroke = Stroke(width = 1f, pathEffect = effect)

    /**
     * Draws the pattern along [centre] (the band's centre line) with SrcAtop, so it only lands on
     * what is already in the current layer: draw the band into an offscreen layer first
     * ([layerPaint]), call this, then restore. Stamps overhanging the band edges or round caps are
     * cut off that way.
     */
    fun drawStamps(scope: DrawScope, centre: Path) = with(scope) {
        drawPath(centre, ink, style = stampStroke, blendMode = BlendMode.SrcAtop)
        centerLine?.let { drawPath(centre, ink, style = it, blendMode = BlendMode.SrcAtop) }
    }

    companion object {
        /** Plain paint for the offscreen layer the band + stamps go into (shared, never mutated). */
        val layerPaint: Paint = Paint()

        /** Kit pattern ink alpha (0.26–0.30), a touch stronger: the band is only a few px wide. */
        private const val INK_ALPHA = 0.32f

        /**
         * The band pattern for [pattern] (catalog id) on a band [width] px wide along a path
         * [length] px long ([closed]: a full circle, so the stamps are spaced to meet evenly);
         * `null` for `plain` / unknown ids (flat colour). [ink] = the ground colour.
         */
        fun create(pattern: String, width: Float, length: Float, ink: Color, closed: Boolean = false): BandPattern? {
            if (width <= 0f) return null
            val w = width
            val half = w / 2
            val (stamp, step) = when (pattern) {
                "stripes" -> {
                    // Bars ~35 % of the band, slanted ~45°, like the kit's diagonal stripes.
                    val bar = w * 0.36f
                    Path().apply {
                        moveTo(half * 0.9f, -half)
                        lineTo(half * 0.9f + bar, -half)
                        lineTo(-half * 0.9f + bar, half)
                        lineTo(-half * 0.9f, half)
                        close()
                    } to w * 0.85f
                }
                "dots" -> Path().apply { addOval(Rect(Offset.Zero, w * 0.23f)) } to w * 0.95f
                "zigzag" -> {
                    // One period: a 0.2 w thick line from −a to +a and back.
                    val p = w * 1.3f
                    val a = w * 0.2f
                    val t = w * 0.1f
                    Path().apply {
                        moveTo(0f, -a - t)
                        lineTo(p / 2, a - t)
                        lineTo(p, -a - t)
                        lineTo(p, -a + t)
                        lineTo(p / 2, a + t)
                        lineTo(0f, -a + t)
                        close()
                    } to p
                }
                "waves" -> {
                    // One period of a sine-like line (two quadratic humps), 0.2 w thick.
                    val p = w * 1.7f
                    val a = w * 0.36f
                    val t = w * 0.1f
                    Path().apply {
                        moveTo(0f, -t)
                        quadraticTo(p / 4, -a - t, p / 2, -t)
                        quadraticTo(p * 3 / 4, a - t, p, -t)
                        lineTo(p, t)
                        quadraticTo(p * 3 / 4, a + t, p / 2, t)
                        quadraticTo(p / 4, -a + t, 0f, t)
                        close()
                    } to p
                }
                "grid" -> {
                    val t = w * 0.15f
                    Path().apply { addRect(Rect(-t / 2, -half, t / 2, half)) } to w * 0.8f
                }
                else -> return null
            }
            // Closed paths: a whole number of stamps per lap, so start and end meet without a seam.
            val advance = if (closed && length > 0f) length / max(1, (length / step).roundToInt()) else step
            val effect = PathEffect.stampedPathEffect(stamp, advance, phase = 0f, style = StampedPathEffectStyle.Rotate)
            val centerLine = if (pattern == "grid") Stroke(width = w * 0.15f) else null
            return BandPattern(effect, centerLine, ink.copy(alpha = INK_ALPHA))
        }
    }
}
