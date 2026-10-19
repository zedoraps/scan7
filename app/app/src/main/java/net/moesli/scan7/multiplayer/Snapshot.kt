package net.moesli.scan7.multiplayer

import kotlinx.serialization.Serializable
import net.moesli.scan7.game.Avatar
import net.moesli.scan7.game.DepartedPlayer
import net.moesli.scan7.game.EnderPenalty
import net.moesli.scan7.game.GameKind
import net.moesli.scan7.game.GameRules
import net.moesli.scan7.game.GameState
import net.moesli.scan7.game.Player
import net.moesli.scan7.game.PlayerResult
import net.moesli.scan7.game.ResultSource
import net.moesli.scan7.game.ResultStatus
import net.moesli.scan7.game.RoundState
import net.moesli.scan7.game.ScoreAdjustment
import net.moesli.scan7.game.Seat
import net.moesli.scan7.game.TablePosition
import net.moesli.scan7.game.ViewMode

/**
 * Full game state on the wire (Welcome / resync). Own DTOs instead of annotating the domain classes,
 * so a refactor of [GameState] can't silently change the protocol. Player ids are the **host's** ids;
 * a client keeps them only inside the session (it never writes them to its own player table).
 * The app-wide scan direction is a per-device setting and not part of it.
 */
@Serializable
data class GameSnapshot(
    val game: GameDto,
    val players: List<PlayerDto>,
    /** Guest-entered results of the running round (for "only edit your own entries"). */
    val authors: List<ResultAuthor> = emptyList(),
)

/** What a guest needs to draw a player: name + avatar (no stats, no other games). */
@Serializable
data class PlayerDto(val id: Long, val name: String, val avatar: Avatar) {
    fun toPlayer() = Player(id, name, avatar)

    companion object {
        fun of(p: Player) = PlayerDto(p.id, p.name, p.avatar)
    }
}

@Serializable
data class GameDto(
    val id: Long,
    /** Absent from older peers = Flip 7 (they only know Flip 7). */
    val kind: GameKind = GameKind.FLIP7,
    val enderPenalty: EnderPenalty = EnderPenalty.OFFICIAL,
    val target: Int,
    val seats: List<SeatDto>,
    val rounds: List<RoundDto>,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val abandoned: Boolean = false,
    val endedEarly: Boolean = false,
    val viewMode: ViewMode? = null,
    val adjustments: List<AdjustmentDto> = emptyList(),
    /** Players who joined after round 1: (player, round). */
    val joinedRounds: List<JoinedDto> = emptyList(),
    /** Players who left mid-game, in the order they left. */
    val departed: List<DepartedDto> = emptyList(),
) {
    fun toState() = GameState(
        id = id,
        kind = kind,
        enderPenalty = enderPenalty,
        target = target,
        seats = seats.map { Seat(it.playerId, TablePosition(it.x, it.y)) },
        rounds = rounds.map { it.toRound(kind.rules) },
        startedAt = startedAt,
        finishedAt = finishedAt,
        abandoned = abandoned,
        endedEarly = endedEarly,
        viewMode = viewMode,
        adjustments = adjustments.map { it.toAdjustment() },
        joinedRounds = joinedRounds.associate { it.playerId to it.round },
        departed = departed.map { DepartedPlayer(it.playerId, it.joinedRound, it.leftRound) },
    )

    companion object {
        fun of(s: GameState) = GameDto(
            id = s.id,
            kind = s.kind,
            enderPenalty = s.enderPenalty,
            target = s.target,
            seats = s.seats.map { SeatDto(it.playerId, it.position.x, it.position.y) },
            rounds = s.rounds.map { RoundDto.of(it) },
            startedAt = s.startedAt,
            finishedAt = s.finishedAt,
            abandoned = s.abandoned,
            endedEarly = s.endedEarly,
            viewMode = s.viewMode,
            adjustments = s.adjustments.map { AdjustmentDto.of(it) },
            joinedRounds = s.joinedRounds.map { (p, r) -> JoinedDto(p, r) },
            departed = s.departed.map { DepartedDto(it.playerId, it.joinedRound, it.leftRound) },
        )
    }
}

@Serializable
data class JoinedDto(val playerId: Long, val round: Int)

@Serializable
data class DepartedDto(val playerId: Long, val joinedRound: Int, val leftRound: Int)

@Serializable
data class SeatDto(val playerId: Long, val x: Float, val y: Float)

/** Results as a list keyed by player (JSON maps need string keys; a list keeps the ids typed). */
@Serializable
data class RoundDto(val number: Int, val results: List<ResultDto>, val closedAt: Long? = null, val endedBy: Long? = null) {
    fun toRound(rules: GameRules) = RoundState(number, results.associate { it.playerId to it.toResult(rules) }, closedAt, endedBy)

    companion object {
        fun of(r: RoundState) = RoundDto(
            number = r.number,
            results = r.results.map { (p, res) -> ResultDto.of(p, res) },
            closedAt = r.closedAt,
            endedBy = r.endedBy,
        )
    }
}

@Serializable
data class ResultDto(
    val playerId: Long,
    val status: ResultStatus = ResultStatus.OPEN,
    val points: Int = 0,
    val source: ResultSource? = null,
    val cards: List<WireCard> = emptyList(),
    val updatedAt: Long = 0,
) {
    fun toResult(rules: GameRules) = PlayerResult(status, points, source, cards.map { it.toCard(rules) }, updatedAt)

    companion object {
        fun of(playerId: Long, r: PlayerResult) =
            ResultDto(playerId, r.status, r.points, r.source, r.cards.map { WireCard.of(it) }, r.updatedAt)
    }
}

@Serializable
data class AdjustmentDto(
    val id: Long,
    val playerId: Long,
    val delta: Int,
    val note: String? = null,
    val round: Int,
    val createdAt: Long = 0,
) {
    fun toAdjustment() = ScoreAdjustment(id, playerId, delta, note, round, createdAt)

    companion object {
        fun of(a: ScoreAdjustment) = AdjustmentDto(a.id, a.playerId, a.delta, a.note, a.round, a.createdAt)
    }
}
