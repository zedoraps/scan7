package net.moesli.scan7.ui.history

import net.moesli.scan7.ui.text.uiPlural
import net.moesli.scan7.ui.text.uiText
import net.moesli.scan7.ui.text.UiText
import net.moesli.scan7.R
import net.moesli.scan7.game.GameState
import net.moesli.scan7.game.ResultStatus

/**
 * One cell of the scoreboard: a player's result in a round, [points] as counted (Skyjo: [doubled]
 * for the round ender). [played] = false: the player wasn't in that round (joined later or had
 * left): "–", not editable.
 */
data class ScoreCell(val status: ResultStatus, val points: Int = 0, val played: Boolean = true, val doubled: Boolean = false)

/** One round of the scoreboard; [isRunning] = the round is not closed yet (never in an abandoned game). */
data class ScoreTableRow(val round: Int, val cells: List<ScoreCell>, val isRunning: Boolean)

/**
 * Rounds × players table. Columns are [playerIds]: the seated players in seating order, then the
 * ones who left mid-game ([left]: id → round they left in; [joined]: id → round a player joined
 * in after round 1); [totals] in the same order
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
    val joined: Map<Long, Int> = emptyMap(),
    val left: Map<Long, Int> = emptyMap(),
)

fun scoreTable(state: GameState): ScoreTable {
    val ids = state.everyoneIds
    val rows = state.rounds.map { r ->
        ScoreTableRow(
            round = r.number,
            cells = ids.map { id ->
                if (state.playedIn(id, r.number)) {
                    ScoreCell(r.result(id).status, state.roundPoints(r, id), doubled = state.isDoubled(r, id))
                } else {
                    ScoreCell(ResultStatus.OPEN, played = false)
                }
            },
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
        joined = ids.associateWith { state.joinedRound(it) }.filterValues { it > 1 },
        left = state.departed.associate { it.playerId to it.leftRound },
    )
}

/** Cell text: points, "BUST" (always the word, never color alone) or "–" while open. */
fun ScoreCell.text(): UiText = when (status) {
    ResultStatus.SCORED -> UiText.Raw(points.toString())
    ResultStatus.BUST -> uiText(R.string.label_bust)
    ResultStatus.OPEN -> UiText.Raw("–")
}

/** Content description of a cell, e.g. "Round 2, Anna: bust". */
fun ScoreCell.describe(round: Int, name: String): UiText = if (!played) uiText(R.string.scoreboard_cell_absent, round, name) else when (status) {
    ResultStatus.SCORED -> {
        val cell = uiText(R.string.scoreboard_cell_points, round, name, uiPlural(R.plurals.points, points))
        if (doubled) UiText.Join(listOf(cell, uiText(R.string.scoreboard_cell_doubled)), ", ") else cell
    }
    ResultStatus.BUST -> uiText(R.string.scoreboard_cell_bust, round, name)
    ResultStatus.OPEN -> uiText(R.string.scoreboard_cell_open, round, name)
}
