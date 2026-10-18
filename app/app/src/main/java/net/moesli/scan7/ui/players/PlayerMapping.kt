package net.moesli.scan7.ui.players

import androidx.compose.ui.graphics.Color
import net.moesli.scan7.game.DEFAULT_TARGET
import net.moesli.scan7.game.GameState
import net.moesli.scan7.game.Player
import net.moesli.scan7.game.ResultStatus
import net.moesli.scan7.ui.components.PlayerStatus
import net.moesli.scan7.ui.components.PlayerUi
import net.moesli.scan7.ui.theme.PlayerColors

/** The player's palette color. */
val Player.color: Color get() = PlayerColors[colorIndex.coerceIn(0, PlayerColors.lastIndex)]

/** Component model for a stored player (outside a game: total 0, open). */
fun Player.toUi(
    total: Int = 0,
    status: PlayerStatus = PlayerStatus.Open,
    target: Int = DEFAULT_TARGET,
    isLeader: Boolean = false,
): PlayerUi = PlayerUi(
    name = name,
    color = color,
    total = total,
    status = status,
    target = target,
    isLeader = isLeader,
    id = id.toString(),
)

/** Component models for a game's players in seating order (status of [round], default the running one). */
fun GameState.playerUis(players: Map<Long, Player>, round: Int = currentRound.number): List<PlayerUi> {
    val totals = totals
    val leaders = leaders.toSet()
    return playerIds.map { id ->
        val result = result(id, round)
        val status = when (result.status) {
            ResultStatus.OPEN -> PlayerStatus.Open
            ResultStatus.SCORED -> PlayerStatus.Scored(result.points)
            ResultStatus.BUST -> PlayerStatus.Bust
        }
        players.getValue(id).toUi(totals.getValue(id), status, target, id in leaders)
    }
}
