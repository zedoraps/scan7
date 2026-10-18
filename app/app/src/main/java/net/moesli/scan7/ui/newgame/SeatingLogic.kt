package net.moesli.scan7.ui.newgame

import kotlin.math.roundToInt
import net.moesli.scan7.game.MAX_PLAYERS

/** Players needed to continue from "pick players" (a game of Flip 7 needs at least two). */
const val MIN_PLAYERS = 2

/** Target stepper: step and bounds. */
const val TARGET_STEP = 10
const val MIN_TARGET = 50
const val MAX_TARGET = 1000

/** This list with the element at [from] moved to [to] (the others shift). */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    require(from in indices && to in indices) { "Index out of range" }
    if (from == to) return this
    val out = toMutableList()
    out.add(to, out.removeAt(from))
    return out
}

/**
 * Index a dragged row lands on: the row started at [from] and has been dragged by [offset] px;
 * each row takes [span] px (height + gap). Clamped to 0 until [count].
 */
fun dragTargetIndex(from: Int, offset: Float, span: Float, count: Int): Int {
    if (count <= 0) return 0
    if (span <= 0f) return from.coerceIn(0, count - 1)
    return (from + (offset / span).roundToInt()).coerceIn(0, count - 1)
}

/**
 * Toggles [id] in the selection. The selection keeps the order players were picked in (that is the
 * initial seating order). Adding beyond [max] is ignored.
 */
fun toggleSelection(selected: List<Long>, id: Long, max: Int = MAX_PLAYERS): List<Long> = when {
    id in selected -> selected - id
    selected.size >= max -> selected
    else -> selected + id
}

/** Seating order for step 2: kept order of [previous] players still selected, then newly picked ones. */
fun seatingFor(selected: List<Long>, previous: List<Long>): List<Long> =
    previous.filter { it in selected } + selected.filter { it !in previous }

/** Target after [steps] presses of the stepper (negative = minus), on the [TARGET_STEP] grid and clamped. */
fun steppedTarget(current: Int, steps: Int): Int {
    val snapped = if (steps >= 0) {
        (current / TARGET_STEP) * TARGET_STEP
    } else {
        ((current + TARGET_STEP - 1) / TARGET_STEP) * TARGET_STEP
    }
    return (snapped + steps * TARGET_STEP).coerceIn(MIN_TARGET, MAX_TARGET)
}

/** Stepper positions between [MIN_TARGET] and [MAX_TARGET] (for the adjustable semantics' `steps`). */
const val TARGET_STEPS = (MAX_TARGET - MIN_TARGET) / TARGET_STEP - 1

/** Target for a TalkBack "set progress" [value]: nearest [TARGET_STEP], clamped. */
fun targetFromProgress(value: Float): Int {
    if (value.isNaN()) return MIN_TARGET
    return ((value / TARGET_STEP).roundToInt() * TARGET_STEP).coerceIn(MIN_TARGET, MAX_TARGET)
}

/** A TalkBack reorder action on a seat row: [label] moves the row to index [to]. */
data class SeatMove(val label: String, val to: Int)

/** Reorder actions for the row at [index] of [count]: up, down, and to first / last when that is further. */
fun seatMoves(index: Int, count: Int): List<SeatMove> = buildList {
    if (index !in 0 until count) return@buildList
    if (index > 0) add(SeatMove("Move up", index - 1))
    if (index < count - 1) add(SeatMove("Move down", index + 1))
    if (index > 1) add(SeatMove("Move to first seat", 0))
    if (index < count - 2) add(SeatMove("Move to last seat", count - 1))
}

/**
 * Auto-scroll step (px per frame) while a row is dragged: the row spans [top]..[bottom] in the
 * viewport [viewportStart]..[viewportEnd]. Inside the [edge] zone at either end it scrolls toward
 * that end, faster the deeper the row is in the zone (up to [maxStep]); otherwise 0.
 */
fun autoScrollStep(top: Float, bottom: Float, viewportStart: Float, viewportEnd: Float, edge: Float, maxStep: Float): Float {
    if (edge <= 0f || maxStep <= 0f || viewportEnd - viewportStart <= 2 * edge) return 0f
    val intoTop = viewportStart + edge - top
    val intoBottom = bottom - (viewportEnd - edge)
    return when {
        intoTop > 0f && intoBottom <= 0f -> -maxStep * (intoTop / edge).coerceAtMost(1f)
        intoBottom > 0f && intoTop <= 0f -> maxStep * (intoBottom / edge).coerceAtMost(1f)
        else -> 0f
    }
}

/** "Next" is enabled with [MIN_PLAYERS]..[MAX_PLAYERS] players (avatar colors may repeat). */
fun canContinue(selectedCount: Int): Boolean =
    selectedCount in MIN_PLAYERS..MAX_PLAYERS
