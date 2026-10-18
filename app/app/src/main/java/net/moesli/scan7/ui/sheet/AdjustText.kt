package net.moesli.scan7.ui.sheet

import net.moesli.scan7.game.MAX_ADJUSTMENT
import net.moesli.scan7.game.ScoreAdjustment

/*
 * Pure text / input logic of "Adjust total" (player sheet). No Android dependencies; JVM unit
 * tested (AdjustTextTest).
 */

/** Typographic minus (U+2212), used for display; input also accepts it. */
private const val MINUS = '−'

/** Stepper steps of the adjust dialog, left to right. */
val AdjustSteps = listOf(-10, -5, -1, 1, 5, 10)

/**
 * The signed delta typed into the adjust field ("+10", "-12", "−12", "12", " 7 "), or `null` if
 * empty, not a number or beyond ±[MAX_ADJUSTMENT]. A lone sign is `null` too.
 */
fun parseDelta(text: String): Int? {
    val t = text.trim().replace(MINUS, '-')
    if (!Regex("[+-]?\\d{1,4}").matches(t)) return null
    val v = t.removePrefix("+").toInt()
    return v.takeIf { it in -MAX_ADJUSTMENT..MAX_ADJUSTMENT }
}

/** Keeps only what the field accepts: one leading sign and at most 3 digits. */
fun filterDeltaInput(text: String): String {
    val t = text.replace(MINUS, '-')
    val sign = t.firstOrNull()?.takeIf { it == '+' || it == '-' }?.toString().orEmpty()
    val digits = t.filter { it.isDigit() }.take(MAX_ADJUSTMENT.toString().length)
    return sign + digits
}

/** Editable field text for [delta]: "+10", "-12", "" for 0. */
fun deltaInput(delta: Int): String = when {
    delta > 0 -> "+$delta"
    delta < 0 -> "-${-delta}"
    else -> ""
}

/** [current] moved by [step], clamped to ±[MAX_ADJUSTMENT]. */
fun stepDelta(current: Int, step: Int): Int = (current + step).coerceIn(-MAX_ADJUSTMENT, MAX_ADJUSTMENT)

/** Field text with the sign flipped ("+10" → "-10", "7" → "-7", "-3" → "3", "" → "-"). */
fun flipSign(text: String): String {
    val t = text.trim().replace(MINUS, '-')
    return when {
        t.startsWith("-") -> t.drop(1)
        t.startsWith("+") -> "-" + t.drop(1)
        else -> "-$t"
    }
}

/** Display text: "+10", "−12" (typographic minus), "0". */
fun formatDelta(delta: Int): String = when {
    delta > 0 -> "+$delta"
    delta < 0 -> "$MINUS${-delta}"
    else -> "0"
}

/** Step button label: "+5", "−10". */
fun stepLabel(step: Int): String = formatDelta(step)

/** Spoken step: "Add 5" / "Subtract 10". */
fun stepDescription(step: Int): String = if (step > 0) "Add $step" else "Subtract ${-step}"

/** Preview under the field: "Total 123 → 133". */
fun adjustPreview(total: Int, delta: Int): String = "Total $total → ${total + delta}"

/** One adjustment in the sheet list: "penalty · round 2" / "round 2" (the delta is shown separately). */
fun adjustmentDetail(adjustment: ScoreAdjustment): String =
    listOfNotNull(adjustment.note, "round ${adjustment.round}").joinToString(" · ")

/** Spoken adjustment: "plus 10, penalty, round 2" / "minus 12, round 1". */
fun adjustmentDescription(adjustment: ScoreAdjustment): String {
    val d = adjustment.delta
    val amount = if (d > 0) "plus $d" else "minus ${-d}"
    return listOfNotNull(amount, adjustment.note, "round ${adjustment.round}").joinToString(", ")
}
