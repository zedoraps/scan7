package net.moesli.scan7.ui.players

import net.moesli.scan7.game.PALETTE_SIZE
import net.moesli.scan7.game.Player

/** Names of the 10 palette colors (same order as `PlayerColors`), for content descriptions. */
val PlayerColorNames: List<String> = listOf(
    "Amber", "Violet", "Lime", "Sand", "Sky", "Mint", "Khaki", "Salmon", "Steel", "Chartreuse",
)

/** Name of palette color [index] ("Color 12" if out of range). */
fun colorName(index: Int): String = PlayerColorNames.getOrNull(index) ?: "Color ${index + 1}"

/**
 * Stored players by the palette color they use, without the player being edited ([exceptId]).
 * The editor shows these colors as taken (still selectable: colors only have to be unique within a game).
 */
fun colorOwners(players: List<Player>, exceptId: Long?): Map<Int, List<Player>> =
    players.filter { it.id != exceptId && it.colorIndex in 0 until PALETTE_SIZE }.groupBy { it.colorIndex }

/**
 * Color conflicts among the [selected] players (a game needs unique colors): for every player that
 * shares a color with another selected player, the other players with that color. Empty = no conflict.
 */
fun colorConflicts(selected: List<Player>): Map<Long, List<Player>> {
    val byColor = selected.groupBy { it.colorIndex }.filterValues { it.size > 1 }
    return byColor.values.flatMap { group -> group.map { p -> p.id to group.filter { it.id != p.id } } }.toMap()
}

/** Default color for a new player: the first color no stored player uses, else the least used one. */
fun suggestedColor(players: List<Player>): Int {
    val counts = players.groupingBy { it.colorIndex }.eachCount()
    return (0 until PALETTE_SIZE).minBy { counts[it] ?: 0 }
}
