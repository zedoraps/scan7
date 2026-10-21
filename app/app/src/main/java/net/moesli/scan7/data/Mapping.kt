package net.moesli.scan7.data

import net.moesli.scan7.game.Avatar
import net.moesli.scan7.game.ConfirmedCard
import net.moesli.scan7.game.DepartedPlayer
import net.moesli.scan7.game.GameRules
import net.moesli.scan7.game.GameState
import net.moesli.scan7.game.Player
import net.moesli.scan7.game.PlayerResult
import net.moesli.scan7.game.RoundState
import net.moesli.scan7.game.ScoreAdjustment
import net.moesli.scan7.game.Seat
import net.moesli.scan7.game.TablePosition
import net.moesli.scan7.game.defaultPositions

internal fun PlayerEntity.toPlayer() =
    Player(id, name, Avatar(avatarFace, avatarPattern, avatarColor).normalized(), createdAt, archived)

internal fun ResultWithCards.toResult(rules: GameRules) = PlayerResult(
    status = result.status,
    points = result.points,
    source = result.source,
    // Unknown labels (e.g. a class removed from a future model) are dropped.
    cards = cards.sortedBy { it.position }.mapNotNull { c -> rules.cardFromLabel(c.label)?.let { ConfirmedCard(it, c.ignored, c.slot) } },
    updatedAt = result.updatedAt,
)

internal fun GameWithDetails.toState(): GameState {
    val ordered = seats.filter { it.leftRound == null }.sortedBy { it.seatIndex }
    val left = seats.filter { it.leftRound != null }.sortedWith(compareBy({ it.leftRound }, { it.seatIndex }))
    val fallback = defaultPositions(ordered.size)
    return GameState(
        id = game.id,
        kind = game.kind,
        enderPenalty = game.enderPenalty,
        target = game.target,
        seats = ordered.mapIndexed { i, s ->
            val pos = if (s.tableX != null && s.tableY != null) TablePosition(s.tableX, s.tableY) else fallback[i]
            Seat(s.playerId, pos)
        },
        rounds = rounds.sortedBy { it.round.number }.map { r ->
            RoundState(
                number = r.round.number,
                results = r.results.associate { it.result.playerId to it.toResult(game.kind.rules) },
                closedAt = r.round.closedAt,
                endedBy = r.round.endedBy,
            )
        },
        startedAt = game.startedAt,
        finishedAt = game.finishedAt,
        abandoned = game.abandoned,
        endedEarly = game.endedEarly,
        viewMode = game.viewMode,
        adjustments = adjustments.sortedWith(compareBy({ it.createdAt }, { it.id })).map { it.toAdjustment() },
        joinedRounds = ordered.filter { it.joinedRound > 1 }.associate { it.playerId to it.joinedRound },
        departed = left.map { DepartedPlayer(it.playerId, it.joinedRound, it.leftRound!!) },
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
    kind = kind,
    enderPenalty = enderPenalty,
)

/** Seated players (seatIndex 0…n-1), then the departed ones (after them, without a table spot). */
internal fun GameState.toSeatEntities(gameId: Long) = seats.mapIndexed { i, s ->
    GamePlayerEntity(gameId, s.playerId, i, s.position.x, s.position.y, joinedRound(s.playerId))
} + departed.mapIndexed { i, d ->
    GamePlayerEntity(gameId, d.playerId, seats.size + i, null, null, d.joinedRound, d.leftRound)
}

internal fun ConfirmedCard.toEntity(resultId: Long, position: Int) =
    ResultCardEntity(resultId = resultId, position = position, label = card.label, ignored = ignored, slot = slot)
