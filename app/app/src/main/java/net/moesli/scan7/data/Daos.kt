package net.moesli.scan7.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerDao {
    @Query("SELECT * FROM players WHERE archived = 0 AND joinedOnly = 0 ORDER BY name COLLATE NOCASE, id")
    fun observeActive(): Flow<List<PlayerEntity>>

    /** All players incl. archived ones (past games may reference them). */
    @Query("SELECT * FROM players ORDER BY id")
    fun observeAll(): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<PlayerEntity>

    @Query("SELECT * FROM players WHERE id = :id")
    suspend fun byId(id: Long): PlayerEntity?

    @Query("SELECT avatarColor FROM players WHERE archived = 0 AND joinedOnly = 0")
    suspend fun activeColors(): List<String>

    @Insert
    suspend fun insert(player: PlayerEntity): Long

    @Update
    suspend fun update(player: PlayerEntity)

    @Query("DELETE FROM players WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM game_players WHERE playerId = :id")
    suspend fun gameCount(id: Long): Int

    /** Archived players no game references any more (their last game was deleted). */
    @Query("DELETE FROM players WHERE archived = 1 AND id NOT IN (SELECT playerId FROM game_players)")
    suspend fun deleteUnreferencedArchived()
}

@Dao
interface GameDao {
    @Transaction
    @Query("SELECT * FROM games WHERE finishedAt IS NULL AND joinedFrom IS NULL ORDER BY startedAt DESC, id DESC LIMIT 1")
    fun observeActive(): Flow<GameWithDetails?>

    @Transaction
    @Query("SELECT * FROM games WHERE id = :id")
    fun observe(id: Long): Flow<GameWithDetails?>

    /**
     * Finished and abandoned games (Home history), most recently finished first; joined games
     * (multiplayer guest copies) also while unfinished (the host may never finish them).
     */
    @Transaction
    @Query(
        "SELECT * FROM games WHERE finishedAt IS NOT NULL OR joinedFrom IS NOT NULL " +
            "ORDER BY COALESCE(finishedAt, startedAt) DESC, id DESC",
    )
    fun observePast(): Flow<List<GameWithDetails>>

    @Transaction
    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun load(id: Long): GameWithDetails?

    @Query("SELECT id FROM games WHERE finishedAt IS NULL AND joinedFrom IS NULL")
    suspend fun unfinishedIds(): List<Long>

    /** The most recently finished or abandoned game (the first Home history entry), or null. */
    @Query("SELECT id FROM games WHERE finishedAt IS NOT NULL ORDER BY finishedAt DESC, id DESC LIMIT 1")
    suspend fun lastFinishedId(): Long?

    /** Most recent game whose (final, seated) players are exactly [ids]. */
    @Query(
        """
        SELECT gp.gameId FROM game_players gp JOIN games g ON g.id = gp.gameId
        WHERE gp.leftRound IS NULL AND g.joinedFrom IS NULL
        GROUP BY gp.gameId
        HAVING COUNT(*) = :count AND SUM(gp.playerId IN (:ids)) = :count
        ORDER BY MAX(g.startedAt) DESC, gp.gameId DESC LIMIT 1
        """,
    )
    suspend fun lastGameWithPlayers(ids: List<Long>, count: Int): Long?

    /** Seated players of a game (not the ones who left mid-game), in seating order. */
    @Query("SELECT * FROM game_players WHERE gameId = :gameId AND leftRound IS NULL ORDER BY seatIndex")
    suspend fun seats(gameId: Long): List<GamePlayerEntity>

    /** A player removed from a game without any trace (joined and left in the same round). */
    @Query("DELETE FROM game_players WHERE gameId = :gameId AND playerId = :playerId")
    suspend fun deleteSeat(gameId: Long, playerId: Long)

    @Insert
    suspend fun insertGame(game: GameEntity): Long

    @Update
    suspend fun updateGame(game: GameEntity)

    /** Seats, rounds (→ results → cards) and adjustments go with it (foreign keys ON DELETE CASCADE). */
    @Query("DELETE FROM games WHERE id = :id")
    suspend fun deleteGame(id: Long)

    @Upsert
    suspend fun upsertSeats(seats: List<GamePlayerEntity>)

    @Insert
    suspend fun insertRound(round: RoundEntity): Long

    @Update
    suspend fun updateRound(round: RoundEntity)

    @Insert
    suspend fun insertResult(result: RoundResultEntity): Long

    @Update
    suspend fun updateResult(result: RoundResultEntity)

    /** A dropped result (a player left the running round); its cards go with it (cascade). */
    @Query("DELETE FROM round_results WHERE id = :id")
    suspend fun deleteResult(id: Long)

    @Query("DELETE FROM result_cards WHERE resultId = :resultId")
    suspend fun deleteCards(resultId: Long)

    @Insert
    suspend fun insertCards(cards: List<ResultCardEntity>)

    @Insert
    suspend fun insertAdjustment(adjustment: ScoreAdjustmentEntity): Long

    @Query("DELETE FROM score_adjustments WHERE id = :id")
    suspend fun deleteAdjustment(id: Long)
}

@Dao
interface SettingsDao {
    @Query("SELECT value FROM settings WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(setting: SettingEntity)
}

@Dao
interface ShareDao {
    @Query("SELECT * FROM share_sessions WHERE gameId = :gameId")
    suspend fun get(gameId: Long): ShareSessionEntity?

    @Query("SELECT * FROM share_sessions ORDER BY updatedAt DESC")
    suspend fun all(): List<ShareSessionEntity>

    @Upsert
    suspend fun put(session: ShareSessionEntity)

    @Query("DELETE FROM share_sessions WHERE gameId = :gameId")
    suspend fun delete(gameId: Long)

    /** The joined copy of a shared game ([GameEntity.gameUid]), if this phone has one. */
    @Query("SELECT id FROM games WHERE gameUid = :uid AND joinedFrom IS NOT NULL LIMIT 1")
    suspend fun joinedGame(uid: String): Long?

    @Query("UPDATE players SET joinedOnly = 0 WHERE id IN (:ids)")
    suspend fun keepPlayers(ids: List<Long>)
}
