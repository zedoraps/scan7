package net.moesli.scan7.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import net.moesli.scan7.game.ConfirmedCard
import net.moesli.scan7.game.DEFAULT_TARGET
import net.moesli.scan7.game.GameState
import net.moesli.scan7.game.PALETTE_SIZE
import net.moesli.scan7.game.Player
import net.moesli.scan7.game.ResultSource
import net.moesli.scan7.game.TablePosition
import net.moesli.scan7.game.ViewMode
import net.moesli.scan7.game.firstFreeColor

/** A game with everything the round / end-of-round / game-over screens need. */
data class GameSnapshot(
    val state: GameState,
    /** Every player of the game, by id (archived ones included). */
    val players: Map<Long, Player>,
    val preferredViewMode: ViewMode?,
) {
    /** Players in seating order. */
    val seatedPlayers: List<Player> get() = state.playerIds.map { players.getValue(it) }

    fun player(id: Long): Player = players.getValue(id)

    /** The game's own choice, else the remembered app preference, else Table for ≤ 6 / List for 7+. */
    val viewMode: ViewMode get() = state.viewMode ?: preferredViewMode ?: ViewMode.defaultFor(state.seats.size)
}

/**
 * Single entry point for players, games and settings. Every action loads the game, applies the
 * pure [GameState] logic and writes the changes back in one transaction; the flows update.
 * Invalid actions throw [IllegalArgumentException] / [IllegalStateException] (nothing is written).
 */
class GameRepository(
    private val db: Scan7Database,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val playerDao = db.playerDao()
    private val gameDao = db.gameDao()
    private val settingsDao = db.settingsDao()

    // ---- Flows ---------------------------------------------------------------------------

    /** Stored (not deleted) players, by name. */
    val players: Flow<List<Player>> = playerDao.observeActive().map { list -> list.map { it.toPlayer() } }

    /** The remembered view mode (last one the user picked), `null` if never chosen. */
    val preferredViewMode: Flow<ViewMode?> = settingsDao.observe(KEY_VIEW_MODE)
        .map { v -> v?.let { runCatching { ViewMode.valueOf(it) }.getOrNull() } }
        .distinctUntilChanged()

    private val allPlayers: Flow<Map<Long, Player>> =
        playerDao.observeAll().map { list -> list.associate { it.id to it.toPlayer() } }

    /** The unfinished game to resume (most recent), or `null`. */
    val activeGame: Flow<GameSnapshot?> = snapshots(gameDao.observeActive())

    /** Any game by id (e.g. game over / history after it finished). */
    fun observeGame(gameId: Long): Flow<GameSnapshot?> = snapshots(gameDao.observe(gameId))

    private fun snapshots(source: Flow<GameWithDetails?>): Flow<GameSnapshot?> =
        combine(source, allPlayers, preferredViewMode) { details, players, pref ->
            details?.let { GameSnapshot(it.toState(), players, pref) }
        }.distinctUntilChanged()

    // ---- Players -------------------------------------------------------------------------

    /** Adds a player; without [colorIndex] the first color not used by another stored player. Returns the id. */
    suspend fun addPlayer(name: String, colorIndex: Int? = null): Long {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Name is empty" }
        return db.withTransaction {
            val color = colorIndex ?: firstFreeColor(playerDao.activeColors()) ?: 0
            require(color in 0 until PALETTE_SIZE) { "Color index out of range" }
            playerDao.insert(PlayerEntity(name = clean, colorIndex = color, createdAt = clock()))
        }
    }

    /** Renames / recolors a player. The color must stay unique within any unfinished game they're in. */
    suspend fun updatePlayer(id: Long, name: String, colorIndex: Int) {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Name is empty" }
        require(colorIndex in 0 until PALETTE_SIZE) { "Color index out of range" }
        db.withTransaction {
            val player = checkNotNull(playerDao.byId(id)) { "No player $id" }
            for (gameId in gameDao.gamesOf(id)) {
                val game = gameDao.load(gameId) ?: continue
                if (game.game.finishedAt != null) continue
                val others = playerDao.byIds(game.seats.map { it.playerId }.filter { it != id })
                require(others.none { it.colorIndex == colorIndex }) { "Color already used in the running game" }
            }
            playerDao.update(player.copy(name = clean, colorIndex = colorIndex))
        }
    }

    /** Deletes a player; one who played (games reference them) is archived instead and keeps showing in those games. */
    suspend fun deletePlayer(id: Long) {
        db.withTransaction {
            val player = playerDao.byId(id) ?: return@withTransaction
            if (playerDao.gameCount(id) > 0) playerDao.update(player.copy(archived = true)) else playerDao.delete(id)
        }
    }

    // ---- Games ---------------------------------------------------------------------------

    /**
     * Starts a game with [seating] (player ids in seating order). Table positions come from the
     * last game with exactly the same players, else the default ring. Any other unfinished game
     * is abandoned (there is only one game to resume). Returns the new game id.
     */
    suspend fun startGame(seating: List<Long>, target: Int = DEFAULT_TARGET): Long = db.withTransaction {
        val now = clock()
        val byId = playerDao.byIds(seating).associateBy { it.id }
        val players = seating.map { checkNotNull(byId[it]) { "No player $it" }.toPlayer() }
        val previous = if (seating.isEmpty()) null else gameDao.lastGameWithPlayers(seating.distinct(), seating.distinct().size)
        val previousPositions = previous?.let { gameId ->
            gameDao.seats(gameId)
                .filter { it.tableX != null && it.tableY != null }
                .associate { it.playerId to TablePosition(it.tableX!!, it.tableY!!) }
        }
        val state = GameState.newGame(players, target, now, previousPositions)
        for (gameId in gameDao.unfinishedIds()) {
            val other = gameDao.load(gameId) ?: continue
            persist(other, other.toState(), other.toState().abandon(now))
        }
        val gameId = gameDao.insertGame(state.toGameEntity())
        val inserted = GameWithDetails(state.toGameEntity().copy(id = gameId), emptyList(), emptyList())
        persist(inserted, null, state.copy(id = gameId))
        gameId
    }

    /** Scores a player from confirmed cards (scan OK / manual save / edit); duplicates → bust. [round] null = running round. */
    suspend fun scoreCards(
        gameId: Long,
        playerId: Long,
        cards: List<ConfirmedCard>,
        source: ResultSource = ResultSource.SCAN,
        round: Int? = null,
    ): GameState = mutate(gameId) { s, now -> s.scoreCards(playerId, cards, source, now, round ?: s.currentRound.number) }

    /** Sets points directly (manual correction without cards). */
    suspend fun scorePoints(gameId: Long, playerId: Long, points: Int, round: Int? = null): GameState =
        mutate(gameId) { s, now -> s.scorePoints(playerId, points, now, round ?: s.currentRound.number) }

    /** One-tap bust. */
    suspend fun bust(gameId: Long, playerId: Long, round: Int? = null): GameState =
        mutate(gameId) { s, now -> s.bust(playerId, now, round ?: s.currentRound.number) }

    /** "Undo" on the bust toast. */
    suspend fun undoBust(gameId: Long, playerId: Long): GameState = mutate(gameId) { s, now -> s.undoBust(playerId, now) }

    suspend fun resetToOpen(gameId: Long, playerId: Long): GameState = mutate(gameId) { s, now -> s.resetToOpen(playerId, now) }

    /** Open players become BUST; sets finishedAt if the game is now over. */
    suspend fun closeRound(gameId: Long): GameState = mutate(gameId) { s, now -> s.closeRound(now) }

    suspend fun nextRound(gameId: Long): GameState = mutate(gameId) { s, _ -> s.nextRound() }

    /** Reopens the last (closed) round; also un-finishes the game if it was over. */
    suspend fun reopenRound(gameId: Long): GameState = mutate(gameId) { s, now -> s.reopenRound(now) }

    suspend fun reorderSeats(gameId: Long, order: List<Long>): GameState = mutate(gameId) { s, _ -> s.reorderSeats(order) }

    suspend fun moveSeat(gameId: Long, from: Int, to: Int): GameState = mutate(gameId) { s, _ -> s.moveSeat(from, to) }

    /** Drops a card at [x], [y] (normalized 0–1) in the table view; seating order follows clockwise. */
    suspend fun movePosition(gameId: Long, playerId: Long, x: Float, y: Float): GameState =
        mutate(gameId) { s, _ -> s.movePosition(playerId, TablePosition(x, y)) }

    /** Switches the game's view and remembers it as the app preference. */
    suspend fun setViewMode(gameId: Long, mode: ViewMode): GameState = db.withTransaction {
        settingsDao.put(SettingEntity(KEY_VIEW_MODE, mode.name))
        mutate(gameId) { s, _ -> s.withViewMode(mode) }
    }

    suspend fun setPreferredViewMode(mode: ViewMode) = settingsDao.put(SettingEntity(KEY_VIEW_MODE, mode.name))

    /** Ends the game from the menu without a winner. */
    suspend fun abandonGame(gameId: Long): GameState = mutate(gameId) { s, now -> s.abandon(now) }

    suspend fun deleteGame(gameId: Long) = gameDao.deleteGame(gameId)

    suspend fun loadGame(gameId: Long): GameState? = gameDao.load(gameId)?.toState()

    // ---- Internals -----------------------------------------------------------------------

    private suspend fun mutate(gameId: Long, op: (GameState, Long) -> GameState): GameState = db.withTransaction {
        val details = checkNotNull(gameDao.load(gameId)) { "No game $gameId" }
        val old = details.toState()
        val new = op(old, clock())
        persist(details, old, new)
        new
    }

    /** Writes what changed between [old] (as loaded in [details]; `null` = nothing stored yet) and [new]. */
    private suspend fun persist(details: GameWithDetails, old: GameState?, new: GameState) {
        val gameId = details.game.id
        val game = new.toGameEntity().copy(id = gameId)
        if (game != details.game) gameDao.updateGame(game)
        if (old == null || old.seats != new.seats) gameDao.upsertSeats(new.toSeatEntities(gameId))

        val roundIds = details.rounds.associate { it.round.number to it.round.id }
        val resultIds = details.rounds.flatMap { r -> r.results.map { (r.round.number to it.result.playerId) to it.result.id } }.toMap()
        for (round in new.rounds) {
            val oldRound = old?.rounds?.getOrNull(round.number - 1)
            val roundId = roundIds[round.number]
                ?: gameDao.insertRound(RoundEntity(gameId = gameId, number = round.number, closedAt = round.closedAt))
            if (oldRound != null && oldRound.closedAt != round.closedAt) {
                gameDao.updateRound(RoundEntity(roundId, gameId, round.number, round.closedAt))
            }
            for (playerId in new.playerIds) {
                val result = round.result(playerId)
                val existingId = resultIds[round.number to playerId]
                if (existingId != null && oldRound?.result(playerId) == result) continue
                val entity = RoundResultEntity(
                    id = existingId ?: 0,
                    roundId = roundId,
                    playerId = playerId,
                    status = result.status,
                    points = result.points,
                    source = result.source,
                    updatedAt = result.updatedAt,
                )
                val resultId = if (existingId == null) {
                    gameDao.insertResult(entity)
                } else {
                    gameDao.updateResult(entity)
                    gameDao.deleteCards(existingId)
                    existingId
                }
                if (result.cards.isNotEmpty()) gameDao.insertCards(result.cards.mapIndexed { i, c -> c.toEntity(resultId, i) })
            }
        }
    }

    private companion object {
        const val KEY_VIEW_MODE = "view_mode"
    }
}
