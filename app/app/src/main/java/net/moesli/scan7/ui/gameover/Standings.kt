package net.moesli.scan7.ui.gameover

import net.moesli.scan7.game.GameState

/** One line of the final standings. [rank] is shared on equal totals (1, 1, 3). */
data class Standing(val playerId: Long, val rank: Int, val total: Int, val isWinner: Boolean)

/** Standings after the last round, best first; equal totals keep the seating order. */
fun standingsOf(state: GameState): List<Standing> {
    val ranks = state.standings()
    val totals = state.totals
    val winners = state.winners.toSet()
    return state.playerIds
        .map { Standing(it, ranks.getValue(it), totals.getValue(it), it in winners) }
        .sortedBy { it.rank }
}

/** "1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", … */
fun ordinal(n: Int): String {
    val suffix = when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}

/** "Anna", "Anna and David", "Anna, Ben and David". */
fun joinNames(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> names[0]
    else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
}

/** "Ben wins" / "Ben and Mia share the win". */
fun winnerHeadline(names: List<String>): String = when (names.size) {
    0 -> "No winner"
    1 -> "${names[0]} wins"
    else -> "${joinNames(names)} share the win"
}
