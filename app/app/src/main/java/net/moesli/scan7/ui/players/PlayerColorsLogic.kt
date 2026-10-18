package net.moesli.scan7.ui.players

import net.moesli.scan7.game.Avatar
import net.moesli.scan7.game.Player
import net.moesli.scan7.game.newPlayerAvatar
import kotlin.random.Random

/**
 * Players by the avatar color they use, without the player being edited ([exceptId]). [players]
 * are the other players of the current game (or of the game being set up): the editor marks these
 * colors with a dot so people avoid duplicates (still allowed).
 */
fun colorOwners(players: List<Player>, exceptId: Long?): Map<String, List<Player>> =
    players.filter { it.id != exceptId }.groupBy { it.avatar.color }

/**
 * Default avatar for a new player: random face + pattern and the first color none of [taken] uses
 * (in New game: the selected players; in the players list: every stored player), else the first.
 */
fun suggestedAvatar(taken: List<Player>, random: Random = Random.Default): Avatar =
    newPlayerAvatar(taken.map { it.avatar.color }, random)
