package net.moesli.scan7.data

import net.moesli.scan7.game.ConfirmedCard
import net.moesli.scan7.game.GameState
import net.moesli.scan7.game.Player
import net.moesli.scan7.game.PlayerResult
import net.moesli.scan7.game.RoundState
import net.moesli.scan7.game.ScoreAdjustment
import net.moesli.scan7.game.Seat
import net.moesli.scan7.game.TablePosition
import net.moesli.scan7.game.defaultPositions
import net.moesli.scan7.scoring.Card

internal fun PlayerEntity.toPlayer() = Player(id, name, colorIndex, createdAt, archived)

internal fun ResultWithCards.toResult() = PlayerResult(
    status = result.status,
    points = result.points,
    source = result.source,
    // Unknown labels (e.g. a class removed from a future model) are dropped.
    cards = cards.sortedBy { it.position }.mapNotNull { c -> Card.fromLabel(c.label)?.let { ConfirmedCard(it, c.ignored) } },
    updatedAt = result.updatedAt,
)

internal fun GameWithDetails.toState(): GameState {
    val ordered = seats.sortedBy { it.seatIndex }
    val fallback = defaultPositions(ordered.size)
    return GameState(
        id = game.id,
        target = game.target,
        seats = ordered.mapIndexed { i, s ->
            val pos = if (s.tableX != null && s.tableY != null) TablePosition(s.tableX, s.tableY) else fallback[i]
            Seat(s.playerId, pos)
        },
        rounds = rounds.sortedBy { it.round.number }.map { r ->
            RoundState(
                number = r.round.number,
                results = r.results.associate { it.result.playerId to it.toResult() },
                closedAt = r.round.closedAt,
            )
        },
        startedAt = game.startedAt,
        finishedAt = game.finishedAt,
        abandoned = game.abandoned,
        endedEarly = game.endedEarly,
        viewMode = game.viewMode,
        adjustments = adjustments.sortedWith(compareBy({ it.createdAt }, { it.id })).map { it.toAdjustment() },
    )
}

internal fun ScoreAdjustmentEntity.toAdjustment() =
    ScoreAdjustment(id = id, playerId = playerId, delta = delta, note = note, round = roundNumber, createdAt = createdAt)

internal fun ScoreAdjustment.toEntity(gameId: Long) = ScoreAdjustmentEntity(
    id = id,
    gameId = gameId,
    playerId = playerId,
    delta = delta,
    note = note,
    createdAt = createdAt,
    roundNumber = round,
)

internal fun GameState.toGameEntity() = GameEntity(
    id = id,
    startedAt = startedAt,
    finishedAt = finishedAt,
    abandoned = abandoned,
    endedEarly = endedEarly,
    target = target,
    viewMode = viewMode,
)

internal fun GameState.toSeatEntities(gameId: Long) = seats.mapIndexed { i, s ->
    GamePlayerEntity(gameId, s.playerId, i, s.position.x, s.position.y)
}

internal fun ConfirmedCard.toEntity(resultId: Long, position: Int) =
    ResultCardEntity(resultId = resultId, position = position, label = card.label, ignored = ignored)
