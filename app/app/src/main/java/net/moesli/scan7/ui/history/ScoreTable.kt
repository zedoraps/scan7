package net.moesli.scan7.ui.history

import net.moesli.scan7.game.GameState
import net.moesli.scan7.game.ResultStatus

/** One cell of the scoreboard: a player's result in a round. */
data class ScoreCell(val status: ResultStatus, val points: Int = 0)

/** One round of the scoreboard; [isRunning] = the round is not closed yet (never in an abandoned game). */
data class ScoreTableRow(val round: Int, val cells: List<ScoreCell>, val isRunning: Boolean)

/**
 * Rounds × players table. Columns are [playerIds] in seating order; [totals] in the same order
 * (incl. total adjustments, like `GameState.totals`). [leaders] are the winners once the game is
 * over, else the current leaders. [adjusted]: players with at least one total adjustment (the
 * total cell shows a small "±"; there is no separate adjustments row).
 */
data class ScoreTable(
    val playerIds: List<Long>,
    val rows: List<ScoreTableRow>,
    val totals: List<Int>,
    val leaders: Set<Long>,
    val adjusted: Set<Long> = emptySet(),
)

fun scoreTable(state: GameState): ScoreTable {
    val ids = state.playerIds
    val rows = state.rounds.map { r ->
        ScoreTableRow(
            round = r.number,
            cells = ids.map { id -> r.result(id).let { ScoreCell(it.status, it.points) } },
            isRunning = !r.isClosed && !state.abandoned,
        )
    }
    val totals = state.totals
    return ScoreTable(
        playerIds = ids,
        rows = rows,
        totals = ids.map { totals.getValue(it) },
        leaders = state.winners.ifEmpty { state.leaders }.toSet(),
        adjusted = state.adjustments.map { it.playerId }.toSet(),
    )
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
