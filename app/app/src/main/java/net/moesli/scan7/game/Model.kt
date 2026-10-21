package net.moesli.scan7.game

import net.moesli.scan7.scoring.Card
import net.moesli.scan7.scoring.CardKind
import net.moesli.scan7.scoring.RowScore
import net.moesli.scan7.scoring.scoreRow

const val MAX_PLAYERS = 10

/** Fewest players a game can have (a game of Flip 7 needs at least two; also mid-game). */
const val MIN_PLAYERS = 2
const val DEFAULT_TARGET = 200

/**
 * Longest player name (characters). Cards / rows / the scoreboard ellipsize long names; the
 * editor shows a counter near the limit and the repository cuts longer names (debug seeds too).
 */
const val MAX_NAME_LENGTH = 20

/** Player count up to which the table view is the default (more players → list view). */
const val TABLE_VIEW_MAX_PLAYERS = 6

/**
 * A stored player with their [avatar] (color + pattern + face, reused across games). Avatar colors
 * may repeat within a game (the editor only hints at colors already in use).
 */
data class Player(
    val id: Long,
    val name: String,
    val avatar: Avatar = Avatar.DEFAULT,
    val createdAt: Long = 0,
    val archived: Boolean = false,
) {
    /** Initial shown in the player token. */
    val initial: String get() = name.trim().take(1).uppercase()
}

enum class ResultStatus { OPEN, SCORED, BUST }

/** How a result was entered. */
enum class ResultSource { SCAN, MANUAL, BUST_TAP, CLOSE_ROUND }

/**
 * Order in which "Scan next" walks the table (app setting, round menu). Only decides who is
 * "next"; the seating order (= list order = clockwise table order) never changes.
 */
enum class ScanDirection {
    CLOCKWISE,
    COUNTER_CLOCKWISE,
    ;

    fun toggled(): ScanDirection = if (this == CLOCKWISE) COUNTER_CLOCKWISE else CLOCKWISE
}

enum class ViewMode {
    TABLE,
    LIST,
    ;

    companion object {
        fun defaultFor(playerCount: Int): ViewMode = if (playerCount <= TABLE_VIEW_MAX_PLAYERS) TABLE else LIST
    }
}

/**
 * A card confirmed for a player's result, of the game's card set ([GameRules.cardFromLabel]).
 * [ignored] cards (Flip 7: action cards, `back`) are kept for statistics and shown as grey chips
 * but never count towards the score. [slot] = the card's place in a fixed layout (Skyjo: 0–11 in
 * the 3 × 4 grid, row by row; an empty slot = a removed card), `null` for a free row (Flip 7).
 */
data class ConfirmedCard(
    val card: GameCard,
    val ignored: Boolean = card is Card && (card.kind == CardKind.ACTION || card.kind == CardKind.BACK),
    val slot: Int? = null,
)

/** One player's result in one round. [points] is 0 unless [status] is [ResultStatus.SCORED]. */
data class PlayerResult(
    val status: ResultStatus = ResultStatus.OPEN,
    val points: Int = 0,
    val source: ResultSource? = null,
    val cards: List<ConfirmedCard> = emptyList(),
    val updatedAt: Long = 0,
) {
    val isOpen: Boolean get() = status == ResultStatus.OPEN
    val isDone: Boolean get() = status != ResultStatus.OPEN

    /** Flip 7 score breakdown of the confirmed cards, or `null` if no Flip 7 cards were recorded. */
    val breakdown: RowScore? get() = if (cards.none { it.card is Card }) null else scoreRow(cards.countedCards())

    companion object {
        val OPEN = PlayerResult()
    }
}

/**
 * The cards that count for scoring. A Second Chance is stored as an ignored card (it never adds
 * points) but still counts: it cancels one duplicate in [scoreRow].
 */
fun List<ConfirmedCard>.countedCards(): List<Card> =
    filter { !it.ignored || it.card == Card.SECOND_CHANCE }.flip7Cards()

/** The Flip 7 cards of a result (other games' cards are skipped). */
fun List<ConfirmedCard>.flip7Cards(): List<Card> = mapNotNull { it.card as? Card }

/** Free card position in the table view, normalized to 0–1 within the arrangeable area. */
data class TablePosition(val x: Float, val y: Float) {
    fun clamped(): TablePosition = TablePosition(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
}

/**
 * A player who left a running game ("Players" in the round menu). Their results of rounds
 * [joinedRound] until before [leftRound] stay in the scoreboard and their total; they no longer
 * play, count for the round's progress, Scan next or the winner.
 */
data class DepartedPlayer(val playerId: Long, val joinedRound: Int, val leftRound: Int)

/** A player at the table. The order of [GameState.seats] is the seating order. */
data class Seat(val playerId: Long, val position: TablePosition)

data class RoundState(
    /** 1-based. */
    val number: Int,
    val results: Map<Long, PlayerResult>,
    val closedAt: Long? = null,
    /** Skyjo: the player who ended the round (revealed all cards first), for [EnderPenalty]; `null` = not set. */
    val endedBy: Long? = null,
) {
    val isClosed: Boolean get() = closedAt != null

    fun result(playerId: Long): PlayerResult = results[playerId] ?: PlayerResult.OPEN

    /** [playerId]'s points as entered, before a round-end rule ([GameState.roundPoints] has the counted ones). */
    fun points(playerId: Long): Int = result(playerId).points
}

/** Largest |delta| of one total adjustment (keeps typos like 1000 out). */
const val MAX_ADJUSTMENT = 999

/**
 * A manual change of a player's game total ("Adjust total" in the player sheet: penalty, house
 * rule, …), stored separately from the round results. [delta] is signed and never 0; [round] is
 * the round that was running (or just closed) when it was made. [id] is 0 until stored.
 */
data class ScoreAdjustment(
    val id: Long = 0,
    val playerId: Long,
    val delta: Int,
    val note: String? = null,
    val round: Int,
    val createdAt: Long = 0,
)
