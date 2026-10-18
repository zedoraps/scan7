package net.moesli.scan7.ui.newgame

import kotlin.math.roundToInt
import net.moesli.scan7.game.GameState
import net.moesli.scan7.game.MAX_PLAYERS
import net.moesli.scan7.game.MIN_PLAYERS
import net.moesli.scan7.game.PlayerResult
import net.moesli.scan7.game.RoundState
import net.moesli.scan7.game.Seat
import net.moesli.scan7.game.TablePosition

/** Target stepper: step and bounds. */
const val TARGET_STEP = 10
const val MIN_TARGET = 50
const val MAX_TARGET = 1000

/**
 * Toggles [id] in the selection. The selection keeps the order players were picked in (that is the
 * initial seating order). Adding beyond [max] is ignored.
 */
fun toggleSelection(selected: List<Long>, id: Long, max: Int = MAX_PLAYERS): List<Long> = when {
    id in selected -> selected - id
    selected.size >= max -> selected
    else -> selected + id
}

/** Seating order for the seating step: kept order of [previous] players still selected, then newly picked ones. */
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

/** "Next" is enabled with [MIN_PLAYERS]..[MAX_PLAYERS] players (avatar colors may repeat). */
fun canContinue(selectedCount: Int): Boolean =
    selectedCount in MIN_PLAYERS..MAX_PLAYERS

/**
 * The seating step's seats before the game exists: player ids in seating order with their table
 * positions. Moves follow the game's own rules ([GameState.movePosition]: order re-derived
 * clockwise; [GameState.swapSeats]: list drop swaps), so the arranged table is exactly what the
 * game starts with.
 */
data class SeatingDraft(val seats: List<Seat>) {
    val ids: List<Long> get() = seats.map { it.playerId }
    val positions: List<TablePosition> get() = seats.map { it.position }

    /** Table view drop of [playerId] at [position]. */
    fun moveCard(playerId: Long, position: TablePosition): SeatingDraft =
        if (playerId in ids) SeatingDraft(asGame().movePosition(playerId, position).seats) else this

    /** List view drop: the players at seats [from] and [to] swap. */
    fun swapRows(from: Int, to: Int): SeatingDraft =
        if (from in seats.indices && to in seats.indices) SeatingDraft(asGame().swapSeats(from, to).seats) else this

    /** Without players that no longer exist (deleted meanwhile); the others keep their spots. */
    fun retain(existing: Set<Long>): SeatingDraft =
        if (ids.all { it in existing }) this else SeatingDraft(seats.filter { it.playerId in existing })

    private fun asGame() = GameState(seats = seats, rounds = listOf(RoundState(1, ids.associateWith { PlayerResult.OPEN })))

    companion object {
        /**
         * [order] on the spots of [previousPositions] (same player set only, as at game start),
         * else on the default ring ("Skip seats": tick order, default ring).
         */
        fun of(order: List<Long>, previousPositions: Map<Long, TablePosition>? = null) =
            SeatingDraft(GameState.initialSeats(order, previousPositions))

        /** Flat form for `rememberSaveable`: ids, then x / y per seat. */
        fun save(d: SeatingDraft): LongArray = d.seats.flatMap { s ->
            listOf(s.playerId, s.position.x.toRawBits().toLong(), s.position.y.toRawBits().toLong())
        }.toLongArray()

        fun restore(a: LongArray): SeatingDraft = SeatingDraft(
            a.toList().chunked(3).filter { it.size == 3 }.map { (id, x, y) ->
                Seat(id, TablePosition(Float.fromBits(x.toInt()), Float.fromBits(y.toInt())))
            },
        )
    }
}
