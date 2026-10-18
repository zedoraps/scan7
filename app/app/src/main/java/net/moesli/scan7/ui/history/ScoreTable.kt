package net.moesli.scan7.ui.history

import net.moesli.scan7.game.GameState
import net.moesli.scan7.game.ResultStatus

/** One cell of the scoreboard: a player's result in a round. */
data class ScoreCell(val status: ResultStatus, val points: Int = 0)

/** One round of the scoreboard; [isRunning] = the round is not closed yet. */
data class ScoreTableRow(val round: Int, val cells: List<ScoreCell>, val isRunning: Boolean)

/**
 * Rounds × players table. Columns are [playerIds] in seating order; [totals] in the same order.
 * [leaders] are the winners once the game is over, else the current leaders.
 */
data class ScoreTable(
    val playerIds: List<Long>,
    val rows: List<ScoreTableRow>,
    val totals: List<Int>,
    val leaders: Set<Long>,
)

fun scoreTable(state: GameState): ScoreTable {
    val ids = state.playerIds
    val rows = state.rounds.map { r ->
        ScoreTableRow(
            round = r.number,
            cells = ids.map { id -> r.result(id).let { ScoreCell(it.status, it.points) } },
            isRunning = !r.isClosed,
        )
    }
    val totals = state.totals
    return ScoreTable(ids, rows, ids.map { totals.getValue(it) }, state.winners.ifEmpty { state.leaders }.toSet())
}

/** Cell text: points, "BUST" (always the word, never color alone) or "–" while open. */
fun ScoreCell.text(): String = when (status) {
    ResultStatus.SCORED -> points.toString()
    ResultStatus.BUST -> "BUST"
    ResultStatus.OPEN -> "–"
}

/** Content description of a cell, e.g. "Round 2, Anna: bust". */
fun ScoreCell.describe(round: Int, name: String): String = "Round $round, $name: " + when (status) {
    ResultStatus.SCORED -> "$points points"
    ResultStatus.BUST -> "bust"
    ResultStatus.OPEN -> "not scored yet"
}
