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
    @Query("SELECT * FROM players WHERE archived = 0 ORDER BY name COLLATE NOCASE, id")
    fun observeActive(): Flow<List<PlayerEntity>>

    /** All players incl. archived ones (past games may reference them). */
    @Query("SELECT * FROM players ORDER BY id")
    fun observeAll(): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<PlayerEntity>

    @Query("SELECT * FROM players WHERE id = :id")
    suspend fun byId(id: Long): PlayerEntity?

    @Query("SELECT avatarColor FROM players WHERE archived = 0")
    suspend fun activeColors(): List<String>

    @Insert
    suspend fun insert(player: PlayerEntity): Long

    @Update
    suspend fun update(player: PlayerEntity)

    @Query("DELETE FROM players WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM game_players WHERE playerId = :id")
    suspend fun gameCount(id: Long): Int
}

@Dao
interface GameDao {
    @Transaction
    @Query("SELECT * FROM games WHERE finishedAt IS NULL ORDER BY startedAt DESC, id DESC LIMIT 1")
    fun observeActive(): Flow<GameWithDetails?>

    @Transaction
    @Query("SELECT * FROM games WHERE id = :id")
    fun observe(id: Long): Flow<GameWithDetails?>

    @Transaction
    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun load(id: Long): GameWithDetails?

    @Query("SELECT id FROM games WHERE finishedAt IS NULL")
    suspend fun unfinishedIds(): List<Long>

    /** Most recent game whose players are exactly [ids]. */
    @Query(
        """
        SELECT gp.gameId FROM game_players gp JOIN games g ON g.id = gp.gameId
        GROUP BY gp.gameId
        HAVING COUNT(*) = :count AND SUM(gp.playerId IN (:ids)) = :count
        ORDER BY MAX(g.startedAt) DESC, gp.gameId DESC LIMIT 1
        """,
    )
    suspend fun lastGameWithPlayers(ids: List<Long>, count: Int): Long?

    @Query("SELECT * FROM game_players WHERE gameId = :gameId ORDER BY seatIndex")
    suspend fun seats(gameId: Long): List<GamePlayerEntity>

    @Query("SELECT gameId FROM game_players WHERE playerId = :playerId")
    suspend fun gamesOf(playerId: Long): List<Long>

    @Insert
    suspend fun insertGame(game: GameEntity): Long

    @Update
    suspend fun updateGame(game: GameEntity)

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
