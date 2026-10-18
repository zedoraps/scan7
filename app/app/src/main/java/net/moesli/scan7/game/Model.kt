package net.moesli.scan7.game

import net.moesli.scan7.scoring.Card
import net.moesli.scan7.scoring.CardKind
import net.moesli.scan7.scoring.RowScore
import net.moesli.scan7.scoring.scoreRow

const val MAX_PLAYERS = 10
const val PALETTE_SIZE = 10
const val DEFAULT_TARGET = 200

/** Player count up to which the table view is the default (more players → list view). */
const val TABLE_VIEW_MAX_PLAYERS = 6

/** A stored player. [colorIndex] is an index into the fixed 10-color player palette. */
data class Player(
    val id: Long,
    val name: String,
    val colorIndex: Int,
    val createdAt: Long = 0,
    val archived: Boolean = false,
) {
    /** Initial shown in the player token. */
    val initial: String get() = name.trim().take(1).uppercase()
}

/** First palette color not in [used], or `null` if all 10 are taken. */
fun firstFreeColor(used: Collection<Int>): Int? = (0 until PALETTE_SIZE).firstOrNull { it !in used }

enum class ResultStatus { OPEN, SCORED, BUST }

/** How a result was entered. */
enum class ResultSource { SCAN, MANUAL, BUST_TAP, CLOSE_ROUND }

enum class ViewMode {
    TABLE,
    LIST,
    ;

    companion object {
        fun defaultFor(playerCount: Int): ViewMode = if (playerCount <= TABLE_VIEW_MAX_PLAYERS) TABLE else LIST
    }
}

/**
 * A card confirmed for a player's row. [ignored] cards (action cards, `back`) are kept for
 * statistics and shown as grey chips but never count towards the score.
 */
data class ConfirmedCard(
    val card: Card,
    val ignored: Boolean = card.kind == CardKind.ACTION || card.kind == CardKind.BACK,
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

    /** Score breakdown of the confirmed cards, or `null` if no cards were recorded. */
    val breakdown: RowScore? get() = if (cards.isEmpty()) null else scoreRow(cards.countedCards())

    companion object {
        val OPEN = PlayerResult()
    }
}

/**
 * The cards that count for scoring. A Second Chance is stored as an ignored card (it never adds
 * points) but still counts: it cancels one duplicate in [scoreRow].
 */
fun List<ConfirmedCard>.countedCards(): List<Card> =
    filter { !it.ignored || it.card == Card.SECOND_CHANCE }.map { it.card }

/** Free card position in the table view, normalized to 0–1 within the arrangeable area. */
data class TablePosition(val x: Float, val y: Float) {
    fun clamped(): TablePosition = TablePosition(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
}

/** A player at the table. The order of [GameState.seats] is the seating order. */
data class Seat(val playerId: Long, val position: TablePosition)

data class RoundState(
    /** 1-based. */
    val number: Int,
    val results: Map<Long, PlayerResult>,
    val closedAt: Long? = null,
) {
    val isClosed: Boolean get() = closedAt != null

    fun result(playerId: Long): PlayerResult = results[playerId] ?: PlayerResult.OPEN

    fun points(playerId: Long): Int = result(playerId).points
}
