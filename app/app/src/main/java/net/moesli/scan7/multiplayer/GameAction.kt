package net.moesli.scan7.multiplayer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.moesli.scan7.game.ConfirmedCard
import net.moesli.scan7.game.GameState
import net.moesli.scan7.game.Player
import net.moesli.scan7.game.ResultSource
import net.moesli.scan7.game.Seat
import net.moesli.scan7.game.TablePosition
import net.moesli.scan7.scoring.Card

/**
 * One game mutation as data: the wire form of the existing [GameState] actions (docs/MULTI_DEVICE.md §5).
 * The host applies it and broadcasts it as an [Event]; every client applies the same action with the
 * same `now` and `seq` and so ends up with an identical [GameState].
 *
 * Player actions name their **round explicitly** (never "the running round"): an intent that arrives
 * after the host closed the round must not land in the next one.
 */
@Serializable
sealed interface GameAction {
    /**
     * Only the host may do this (round / game lifecycle, totals, seating). Guests get
     * [RejectReason.NOT_ALLOWED]; their other limits are in [GuestRules].
     */
    val hostOnly: Boolean get() = false

    /** Results this action changes in [state] (staleness check: [HostSession]). */
    fun touches(state: GameState): Set<ResultKey>

    /**
     * Applies the action. [seq] is the event's sequence number: it becomes the id of a new total
     * adjustment, so adjustment ids agree on every device (Room ids are local, see the spec).
     */
    fun apply(state: GameState, now: Long, seq: Long): GameState
}

/** One player's result in one round. */
@Serializable
data class ResultKey(val round: Int, val playerId: Long)

/** A confirmed card on the wire: the detector label (stable, = ml/config/cards.yaml) + ignored flag. */
@Serializable
data class WireCard(val label: String, val ignored: Boolean) {
    fun toCard(): ConfirmedCard = ConfirmedCard(requireNotNull(Card.fromLabel(label)) { "Unknown card $label" }, ignored)

    companion object {
        fun of(card: ConfirmedCard) = WireCard(card.card.label, card.ignored)
    }
}

/** An action on one player's result in one round. */
@Serializable
sealed interface PlayerAction : GameAction {
    val playerId: Long
    val round: Int

    override fun touches(state: GameState): Set<ResultKey> = setOf(ResultKey(round, playerId))
}

@Serializable
@SerialName("score_cards")
data class ScoreCards(
    override val playerId: Long,
    override val round: Int,
    val cards: List<WireCard>,
    val manual: Boolean = false,
) : PlayerAction {
    override fun apply(state: GameState, now: Long, seq: Long) =
        state.scoreCards(playerId, cards.map { it.toCard() }, if (manual) ResultSource.MANUAL else ResultSource.SCAN, now, round)
}

@Serializable
@SerialName("score_points")
data class ScorePoints(override val playerId: Long, override val round: Int, val points: Int) : PlayerAction {
    override fun apply(state: GameState, now: Long, seq: Long) = state.scorePoints(playerId, points, now, round)
}

@Serializable
@SerialName("bust")
data class Bust(override val playerId: Long, override val round: Int) : PlayerAction {
    override fun apply(state: GameState, now: Long, seq: Long) = state.bust(playerId, now, round)
}

@Serializable
@SerialName("undo_bust")
data class UndoBust(override val playerId: Long, override val round: Int) : PlayerAction {
    override fun apply(state: GameState, now: Long, seq: Long): GameState {
        require(round == state.currentRound.number) { "Undo only in the running round" }
        return state.undoBust(playerId, now)
    }
}

@Serializable
@SerialName("reset_to_open")
data class ResetToOpen(override val playerId: Long, override val round: Int) : PlayerAction {
    override fun apply(state: GameState, now: Long, seq: Long): GameState {
        require(round == state.currentRound.number) { "Reset only in the running round" }
        return state.resetToOpen(playerId, now)
    }
}

/** "Adjust total": host only (guests can't change totals); additive, so never stale. */
@Serializable
@SerialName("adjust_total")
data class AdjustTotal(val playerId: Long, val delta: Int, val note: String? = null) : GameAction {
    override val hostOnly get() = true

    override fun touches(state: GameState): Set<ResultKey> = emptySet()

    override fun apply(state: GameState, now: Long, seq: Long): GameState {
        val adjusted = state.adjustTotal(playerId, delta, note, now)
        return adjusted.copy(adjustments = adjusted.adjustments.map { if (it.id == 0L) it.copy(id = seq) else it })
    }
}

@Serializable
@SerialName("remove_adjustment")
data class RemoveAdjustment(val adjustmentId: Long) : GameAction {
    override val hostOnly get() = true

    override fun touches(state: GameState): Set<ResultKey> = emptySet()

    override fun apply(state: GameState, now: Long, seq: Long) = state.removeAdjustment(adjustmentId, now)
}

// ---- Host only -------------------------------------------------------------------------------

/** Every result of [round] (closing / reopening changes the open ones; a stale scan must not survive it). */
private fun GameState.allOf(round: Int): Set<ResultKey> =
    if (round in 1..rounds.size) playerIds.map { ResultKey(round, it) }.toSet() else emptySet()

private fun GameState.requireCurrent(round: Int) =
    require(round == currentRound.number) { "Round $round is not the current round ${currentRound.number}" }

/** "Next round" / confirmed "Close round": [GameState.closeAndStartNext]. */
@Serializable
@SerialName("close_round_and_start_next")
data class CloseRoundAndStartNext(val round: Int) : GameAction {
    override val hostOnly get() = true

    override fun touches(state: GameState) = state.allOf(round)

    override fun apply(state: GameState, now: Long, seq: Long): GameState {
        state.requireCurrent(round)
        return state.closeAndStartNext(now)
    }
}

@Serializable
@SerialName("reopen_round")
data class ReopenRound(val round: Int) : GameAction {
    override val hostOnly get() = true

    override fun touches(state: GameState) = state.allOf(round)

    override fun apply(state: GameState, now: Long, seq: Long): GameState {
        state.requireCurrent(round)
        return state.reopenRound(now)
    }
}

@Serializable
@SerialName("end_game")
data class EndGame(val round: Int) : GameAction {
    override val hostOnly get() = true

    override fun touches(state: GameState) = state.allOf(round)

    override fun apply(state: GameState, now: Long, seq: Long): GameState {
        state.requireCurrent(round)
        return state.endGame(now)
    }
}

@Serializable
@SerialName("reorder_seats")
data class ReorderSeats(val order: List<Long>) : GameAction {
    override val hostOnly get() = true

    override fun touches(state: GameState): Set<ResultKey> = emptySet()

    override fun apply(state: GameState, now: Long, seq: Long) = state.reorderSeats(order)
}

@Serializable
@SerialName("move_position")
data class MovePosition(val playerId: Long, val x: Float, val y: Float) : GameAction {
    override val hostOnly get() = true

    override fun touches(state: GameState): Set<ResultKey> = emptySet()

    override fun apply(state: GameState, now: Long, seq: Long) = state.movePosition(playerId, TablePosition(x, y))
}

/**
 * "Players" in the round menu (main's [GameState.withLineup]): the new table in seating order.
 * [newPlayers] carries names + avatars of players the guests haven't seen yet.
 */
@Serializable
@SerialName("change_lineup")
data class ChangeLineup(val seats: List<SeatDto>, val newPlayers: List<PlayerDto> = emptyList()) : GameAction {
    override val hostOnly get() = true

    /** The running round's results of everyone who joins or leaves. */
    override fun touches(state: GameState): Set<ResultKey> {
        val ids = seats.map { it.playerId }.toSet()
        val before = state.playerIds.toSet()
        return ((before - ids) + (ids - before)).map { ResultKey(state.currentRound.number, it) }.toSet()
    }

    override fun apply(state: GameState, now: Long, seq: Long) =
        state.withLineup(seats.map { Seat(it.playerId, TablePosition(it.x, it.y)) }, now)

    companion object {
        fun of(lineup: List<Seat>, newPlayers: List<Player>) = ChangeLineup(
            seats = lineup.map { SeatDto(it.playerId, it.position.x, it.position.y) },
            newPlayers = newPlayers.map { PlayerDto.of(it) },
        )
    }
}
