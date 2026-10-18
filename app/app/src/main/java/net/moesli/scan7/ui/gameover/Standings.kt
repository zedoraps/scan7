package net.moesli.scan7.ui.gameover

import net.moesli.scan7.ui.text.uiText
import net.moesli.scan7.ui.text.UiText
import net.moesli.scan7.R
import net.moesli.scan7.game.GameState

/**
 * One line of the final standings. [rank] is shared on equal totals (1, 1, 3); a player who [left]
 * mid-game has no rank (0).
 */
data class Standing(val playerId: Long, val rank: Int, val total: Int, val isWinner: Boolean, val left: Boolean = false)

/**
 * Standings after the last round, best first; equal totals keep the seating order. Players who
 * left mid-game follow, unranked, by total.
 */
fun standingsOf(state: GameState): List<Standing> {
    val ranks = state.standings()
    val totals = state.totals
    val winners = state.winners.toSet()
    val seated = state.playerIds
        .map { Standing(it, ranks.getValue(it), totals.getValue(it), it in winners) }
        .sortedBy { it.rank }
    val left = state.departed
        .map { Standing(it.playerId, 0, totals.getValue(it.playerId), isWinner = false, left = true) }
        .sortedByDescending { it.total }
    return seated + left
}

/**
 * "1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", … (German: "1.", "2.", …). The
 * English suffix rule picks the string; other languages give every id the same format.
 */
fun ordinal(n: Int): UiText {
    val id = when {
        n % 100 in 11..13 -> R.string.ordinal_th
        n % 10 == 1 -> R.string.ordinal_st
        n % 10 == 2 -> R.string.ordinal_nd
        n % 10 == 3 -> R.string.ordinal_rd
        else -> R.string.ordinal_th
    }
    return uiText(id, n)
}

/** "Ben wins" / "Ben and Mia share the win". */
fun winnerHeadline(names: List<String>): UiText = when (names.size) {
    0 -> uiText(R.string.gameover_no_winner)
    1 -> uiText(R.string.gameover_wins, names[0])
    else -> uiText(R.string.gameover_share, UiText.Names(names))
}
