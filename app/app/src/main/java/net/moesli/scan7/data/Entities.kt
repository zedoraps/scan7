package net.moesli.scan7.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import net.moesli.scan7.game.EnderPenalty
import net.moesli.scan7.game.GameKind
import net.moesli.scan7.game.ResultSource
import net.moesli.scan7.game.ResultStatus
import net.moesli.scan7.game.ViewMode

// Enums are stored by name (TEXT) via Room's built-in enum support: never rename enum constants.

@Entity(tableName = "players")
data class PlayerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Avatar ids from the catalog (game/Avatar.kt); unknown ids fall back when read. Added in DB version 4. */
    val avatarFace: String,
    val avatarPattern: String,
    val avatarColor: String,
    val createdAt: Long,
    /** Deleted by the user but still referenced by past games. */
    val archived: Boolean = false,
    /**
     * A player of a game joined on another phone (multiplayer guest): kept for that game's history,
     * never in the Players list or New game until saved ("Save players to my list"). Added in DB version 6.
     */
    @ColumnInfo(defaultValue = "0") val joinedOnly: Boolean = false,
)

@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    /** Set when the game is over (or abandoned); `null` = unfinished, can be resumed. */
    val finishedAt: Long? = null,
    val abandoned: Boolean = false,
    /** Ended early from the round menu ("End game"): over, current leader(s) win. Added in DB version 2. */
    @ColumnInfo(defaultValue = "0") val endedEarly: Boolean = false,
    val target: Int,
    val viewMode: ViewMode? = null,
    /**
     * Multiplayer guest: the game was joined on another phone; this is the host's name ("Philip"),
     * `null` = a game of this phone. Joined games are read-only copies. Added in DB version 6.
     */
    val joinedFrom: String? = null,
    /** Stable id of a shared game across phones (a rejoin updates the same copy). Added in DB version 6. */
    val gameUid: String? = null,
    /** Which game (Flip 7 / Skyjo). Added in DB version 7 (older games are Flip 7). */
    @ColumnInfo(defaultValue = "FLIP7") val kind: GameKind = GameKind.FLIP7,
    /** Skyjo's round-ender rule (unused for Flip 7). Added in DB version 7. */
    @ColumnInfo(defaultValue = "OFFICIAL") val enderPenalty: EnderPenalty = EnderPenalty.OFFICIAL,
)

@Entity(
    tableName = "game_players",
    primaryKeys = ["gameId", "playerId"],
    foreignKeys = [
        ForeignKey(entity = GameEntity::class, parentColumns = ["id"], childColumns = ["gameId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PlayerEntity::class, parentColumns = ["id"], childColumns = ["playerId"]),
    ],
    indices = [Index("playerId")],
)
data class GamePlayerEntity(
    val gameId: Long,
    val playerId: Long,
    /** 0-based seating order (clockwise around the table). */
    val seatIndex: Int,
    /** Free card position in the table view, normalized 0–1. */
    val tableX: Float?,
    val tableY: Float?,
    /** Round the player joined in (1 = from the start; later = added mid-game). Added in DB version 5. */
    @ColumnInfo(defaultValue = "1") val joinedRound: Int = 1,
    /**
     * Round the player left in (removed mid-game), `null` while they play. A departed player's row
     * stays so their past results keep showing; seated rows come first in [seatIndex]. Added in DB
     * version 5.
     */
    val leftRound: Int? = null,
)

@Entity(
    tableName = "rounds",
    foreignKeys = [
        ForeignKey(entity = GameEntity::class, parentColumns = ["id"], childColumns = ["gameId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["gameId", "number"], unique = true)],
)
data class RoundEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: Long,
    /** 1-based. */
    val number: Int,
    val closedAt: Long? = null,
    /** Skyjo: the player who ended the round (a player id; no foreign key, like the session state). Added in DB version 7. */
    val endedBy: Long? = null,
)

@Entity(
    tableName = "round_results",
    foreignKeys = [
        ForeignKey(entity = RoundEntity::class, parentColumns = ["id"], childColumns = ["roundId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PlayerEntity::class, parentColumns = ["id"], childColumns = ["playerId"]),
    ],
    indices = [Index(value = ["roundId", "playerId"], unique = true), Index("playerId")],
)
data class RoundResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roundId: Long,
    val playerId: Long,
    val status: ResultStatus,
    val points: Int,
    /** `null` while open. */
    val source: ResultSource?,
    val updatedAt: Long,
)

/** A confirmed card of a result (for statistics); [label] is the detector class label. */
@Entity(
    tableName = "result_cards",
    foreignKeys = [
        ForeignKey(entity = RoundResultEntity::class, parentColumns = ["id"], childColumns = ["resultId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("resultId")],
)
data class ResultCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val resultId: Long,
    /** Order of the card in the row / chip list. */
    val position: Int,
    val label: String,
    val ignored: Boolean,
    /** Skyjo: slot 0–11 in the 3 × 4 grid; `null` for a free row (Flip 7). Added in DB version 7. */
    val slot: Int? = null,
)

/**
 * A manual total adjustment ("Adjust total" in the player sheet), separate from the round
 * results. [delta] is signed; [roundNumber] is the round running when it was made. Added in DB
 * version 3.
 */
@Entity(
    tableName = "score_adjustments",
    foreignKeys = [
        ForeignKey(entity = GameEntity::class, parentColumns = ["id"], childColumns = ["gameId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PlayerEntity::class, parentColumns = ["id"], childColumns = ["playerId"]),
    ],
    indices = [Index("gameId"), Index("playerId")],
)
data class ScoreAdjustmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: Long,
    val playerId: Long,
    val delta: Int,
    val note: String?,
    val createdAt: Long,
    val roundNumber: Int,
)

/**
 * A multiplayer session of a game on this phone (host or guest), so it survives an app restart:
 * [stateJson] = `HostSessionState` / guest state as JSON (multiplayer/SessionState.kt). Added in DB version 6.
 */
@Entity(
    tableName = "share_sessions",
    foreignKeys = [
        ForeignKey(entity = GameEntity::class, parentColumns = ["id"], childColumns = ["gameId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class ShareSessionEntity(
    @PrimaryKey val gameId: Long,
    /** `HOST` or `GUEST`. */
    val role: String,
    val sessionId: String,
    val token: String,
    val stateJson: String,
    val updatedAt: Long,
)

/** App-level key/value settings (e.g. the preferred view mode). */
@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)

data class ResultWithCards(
    @Embedded val result: RoundResultEntity,
    @Relation(parentColumn = "id", entityColumn = "resultId")
    val cards: List<ResultCardEntity>,
)

data class RoundWithResults(
    @Embedded val round: RoundEntity,
    @Relation(entity = RoundResultEntity::class, parentColumn = "id", entityColumn = "roundId")
    val results: List<ResultWithCards>,
)

data class GameWithDetails(
    @Embedded val game: GameEntity,
    @Relation(parentColumn = "id", entityColumn = "gameId")
    val seats: List<GamePlayerEntity>,
    @Relation(entity = RoundEntity::class, parentColumn = "id", entityColumn = "gameId")
    val rounds: List<RoundWithResults>,
    @Relation(parentColumn = "id", entityColumn = "gameId")
    val adjustments: List<ScoreAdjustmentEntity>,
)
