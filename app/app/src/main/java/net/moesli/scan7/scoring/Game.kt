package net.moesli.scan7.scoring

const val WINNING_SCORE = 200

/**
 * Immutable game state. `rounds[r][p]` is the score of `players[p]` in round `r`.
 */
data class Game(
    val players: List<String>,
    val rounds: List<List<Int>> = emptyList(),
) {
    init {
        require(players.isNotEmpty()) { "A game needs at least one player" }
        require(rounds.all { it.size == players.size }) { "Every round needs one score per player" }
    }

    val totals: List<Int>
        get() = players.indices.map { p -> rounds.sumOf { it[p] } }

    val isOver: Boolean
        get() = totals.any { it >= WINNING_SCORE }

    /** Names of the players with the highest total once the game is over (several on a tie). */
    val winners: List<String>
        get() {
            if (!isOver) return emptyList()
            val t = totals
            val best = t.max()
            return players.filterIndexed { i, _ -> t[i] == best }
        }

    /** Returns a new game with one more round; [scores] are in [players] order. */
    fun addRound(scores: List<Int>): Game {
        check(!isOver) { "Game is already over" }
        require(scores.size == players.size) { "Expected ${players.size} scores, got ${scores.size}" }
        return copy(rounds = rounds + listOf(scores.toList()))
    }
}
