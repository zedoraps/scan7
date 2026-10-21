package net.moesli.scan7.multiplayer

import net.moesli.scan7.game.GameState

/**
 * Player ids are local Room ids: the protocol uses the **host's** ids, a guest stores its mirror
 * of the game with its own (hidden) players. These map one side to the other (docs/dev/MULTI_DEVICE.md §4.4).
 */
fun GameState.mapPlayerIds(f: (Long) -> Long): GameState = copy(
    seats = seats.map { it.copy(playerId = f(it.playerId)) },
    rounds = rounds.map { r -> r.copy(results = r.results.mapKeys { (p, _) -> f(p) }, endedBy = r.endedBy?.let(f)) },
    adjustments = adjustments.map { it.copy(playerId = f(it.playerId)) },
    joinedRounds = joinedRounds.mapKeys { (p, _) -> f(p) },
    departed = departed.map { it.copy(playerId = f(it.playerId)) },
)

/** The same action with every player id mapped (a guest's local ids → the host's, before it is sent). */
fun GameAction.mapPlayerIds(f: (Long) -> Long): GameAction = when (this) {
    is ScoreCards -> copy(playerId = f(playerId))
    is ScorePoints -> copy(playerId = f(playerId))
    is Bust -> copy(playerId = f(playerId))
    is UndoBust -> copy(playerId = f(playerId))
    is ResetToOpen -> copy(playerId = f(playerId))
    is AdjustTotal -> copy(playerId = f(playerId))
    is RestoreAdjustment -> copy(adjustment = adjustment.copy(playerId = f(adjustment.playerId)))
    is ReorderSeats -> copy(order = order.map(f))
    is MovePosition -> copy(playerId = f(playerId))
    is SetRoundEnder -> copy(playerId = playerId?.let(f))
    is ChangeLineup -> copy(
        seats = seats.map { it.copy(playerId = f(it.playerId)) },
        newPlayers = newPlayers.map { it.copy(id = f(it.id)) },
    )
    is RemoveAdjustment, is CloseRoundAndStartNext, is CloseRound, is NextRound, is ReopenRound, is EndGame,
    is SetTarget, is SwapSeats, is MoveSeat,
    -> this
}
