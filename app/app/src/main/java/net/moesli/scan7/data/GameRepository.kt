package net.moesli.scan7.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import net.moesli.scan7.BuildConfig
import net.moesli.scan7.game.Avatar
import net.moesli.scan7.game.ConfirmedCard
import net.moesli.scan7.game.DEFAULT_TARGET
import net.moesli.scan7.game.GameState
import net.moesli.scan7.game.Player
import net.moesli.scan7.game.ResultSource
import net.moesli.scan7.game.ScanDirection
import net.moesli.scan7.game.ScoreAdjustment
import net.moesli.scan7.game.TablePosition
import net.moesli.scan7.game.ViewMode
import net.moesli.scan7.game.newPlayerAvatar
import kotlin.random.Random

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
    /** Random face + pattern of new players without an explicit avatar. */
    private val random: Random = Random.Default,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Whether "Save scans for training" can be on at all: debug builds only. */
    private val trainingScansAllowed: Boolean = BuildConfig.DEBUG,
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

    /**
     * Setting "Save scans for training": every shutter photo + JSON to the public Pictures /
     * Documents folders. Debug builds: default on (also for installs that never touched the
     * toggle), off once the user switched it off. Release builds: always off, whatever is stored
     * (the toggle lives in the debug menu, which release builds don't have, so a value restored
     * from a debug install's backup could never be switched off again).
     */
    val saveScansForTraining: Flow<Boolean> = if (!trainingScansAllowed) {
        flowOf(false)
    } else {
        settingsDao.observe(KEY_SAVE_SCANS)
            .map { it != "false" }
            .distinctUntilChanged()
    }

    suspend fun setSaveScansForTraining(on: Boolean) = settingsDao.put(SettingEntity(KEY_SAVE_SCANS, on.toString()))

    /** "Scan next" direction (round menu), app-wide; clockwise until changed. */
    val scanDirection: Flow<ScanDirection> = settingsDao.observe(KEY_SCAN_DIRECTION)
        .map(::parseScanDirection)
        .distinctUntilChanged()

    suspend fun setScanDirection(direction: ScanDirection) = settingsDao.put(SettingEntity(KEY_SCAN_DIRECTION, direction.name))

    /** A remembered plain setting value by [key] (`null` if never set), e.g. the review's "after scan" choice. */
    fun setting(key: String): Flow<String?> = settingsDao.observe(key).distinctUntilChanged()

    suspend fun putSetting(key: String, value: String) = settingsDao.put(SettingEntity(key, value))

    private val allPlayers: Flow<Map<Long, Player>> =
        playerDao.observeAll().map { list -> list.associate { it.id to it.toPlayer() } }

    /** The unfinished game to resume (most recent), or `null`. */
    val activeGame: Flow<GameSnapshot?> = snapshots(gameDao.observeActive())

    /** Finished and abandoned games (Home history), most recently finished first. */
    val pastGames: Flow<List<GameSnapshot>> =
        combine(gameDao.observePast(), allPlayers, preferredViewMode) { games, players, pref ->
            games.map { GameSnapshot(it.toState(), players, pref) }
        }.distinctUntilChanged()

    /** Any game by id (e.g. game over / history after it finished). */
    fun observeGame(gameId: Long): Flow<GameSnapshot?> = snapshots(gameDao.observe(gameId))

    private fun snapshots(source: Flow<GameWithDetails?>): Flow<GameSnapshot?> =
        combine(source, allPlayers, preferredViewMode, scanDirection) { details, players, pref, direction ->
            details?.let { GameSnapshot(it.toState().copy(scanDirection = direction), players, pref) }
        }.distinctUntilChanged()

    // ---- Players -------------------------------------------------------------------------

    /**
     * Adds a player; without [avatar] a random face + pattern and the first color no other stored
     * player uses (else the first color). Returns the id.
     */
    suspend fun addPlayer(name: String, avatar: Avatar? = null): Long {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Name is empty" }
        return db.withTransaction {
            val a = (avatar ?: newPlayerAvatar(playerDao.activeColors(), random)).normalized()
            playerDao.insert(
                PlayerEntity(name = clean, avatarFace = a.face, avatarPattern = a.pattern, avatarColor = a.color, createdAt = clock()),
            )
        }
    }

    /** Renames a player / changes their avatar (colors may repeat within a game). */
    suspend fun updatePlayer(id: Long, name: String, avatar: Avatar) {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Name is empty" }
        val a = avatar.normalized()
        db.withTransaction {
            val player = checkNotNull(playerDao.byId(id)) { "No player $id" }
            playerDao.update(player.copy(name = clean, avatarFace = a.face, avatarPattern = a.pattern, avatarColor = a.color))
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
     * Seating order of the last game played with exactly [players] (in any order), or null. The new
     * game screen starts from it, so everyone gets their old table spot back (spots are handed out in
     * seating order, see GameState.newGame).
     */
    suspend fun previousSeating(players: Collection<Long>): List<Long>? {
        val ids = players.distinct()
        if (ids.isEmpty()) return null
        val gameId = gameDao.lastGameWithPlayers(ids, ids.size) ?: return null
        return gameDao.seats(gameId).map { it.playerId }
    }

    /** Table spots of the last game played with exactly [players], or null (see [previousSeating]). */
    suspend fun previousPositions(players: Collection<Long>): Map<Long, TablePosition>? {
        val ids = players.distinct()
        if (ids.isEmpty()) return null
        val gameId = gameDao.lastGameWithPlayers(ids, ids.size) ?: return null
        return gameDao.seats(gameId)
            .filter { it.tableX != null && it.tableY != null }
            .associate { it.playerId to TablePosition(it.tableX!!, it.tableY!!) }
    }

    /**
     * Starts a game with [seating] (player ids in seating order). Table positions: [positions] (one
     * per player in seating order, arranged on the seating step) if given, else those of the last
     * game with exactly the same players, else the default ring. Any other unfinished game is
     * abandoned (there is only one game to resume). Returns the new game id.
     */
    suspend fun startGame(
        seating: List<Long>,
        target: Int = DEFAULT_TARGET,
        positions: List<TablePosition>? = null,
    ): Long = db.withTransaction {
        val now = clock()
        val byId = playerDao.byIds(seating).associateBy { it.id }
        val players = seating.map { checkNotNull(byId[it]) { "No player $it" }.toPlayer() }
        val previousPositions = if (positions == null) previousPositions(seating) else null
        val state = GameState.newGame(players, target, now, previousPositions, positions = positions)
        for (gameId in gameDao.unfinishedIds()) {
            val other = gameDao.load(gameId) ?: continue
            persist(other, other.toState(), other.toState().abandon(now))
        }
        val gameId = gameDao.insertGame(state.toGameEntity())
        val inserted = GameWithDetails(state.toGameEntity().copy(id = gameId), emptyList(), emptyList(), emptyList())
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

    /** "Adjust total" from the player sheet: signed [delta] with an optional [note], for the current round. */
    suspend fun adjustTotal(gameId: Long, playerId: Long, delta: Int, note: String? = null): GameState =
        mutate(gameId) { s, now -> s.adjustTotal(playerId, delta, note, now) }

    /** Removes a stored adjustment (player sheet ✕). */
    suspend fun removeAdjustment(gameId: Long, adjustmentId: Long): GameState =
        mutate(gameId) { s, now -> s.removeAdjustment(adjustmentId, now) }

    /** Undo of [removeAdjustment]: stores [adjustment] again as it was (new id). */
    suspend fun restoreAdjustment(gameId: Long, adjustment: ScoreAdjustment): GameState =
        mutate(gameId) { s, now -> s.restoreAdjustment(adjustment, now) }

    /** One-tap bust. */
    suspend fun bust(gameId: Long, playerId: Long, round: Int? = null): GameState =
        mutate(gameId) { s, now -> s.bust(playerId, now, round ?: s.currentRound.number) }

    /** "Undo" on the bust toast. */
    suspend fun undoBust(gameId: Long, playerId: Long): GameState = mutate(gameId) { s, now -> s.undoBust(playerId, now) }

    suspend fun resetToOpen(gameId: Long, playerId: Long): GameState = mutate(gameId) { s, now -> s.resetToOpen(playerId, now) }

    /** Open players become BUST; sets finishedAt if the game is now over. */
    suspend fun closeRound(gameId: Long): GameState = mutate(gameId) { s, now -> s.closeRound(now) }

    suspend fun nextRound(gameId: Long): GameState = mutate(gameId) { s, _ -> s.nextRound() }

    /**
     * "Next round" (everyone done) or a confirmed "Close round": closes the running round and,
     * unless the game is now over, starts the next one, in one transaction.
     */
    suspend fun closeRoundAndStartNext(gameId: Long): GameState = mutate(gameId) { s, now -> s.closeAndStartNext(now) }

    /** Reopens the last (closed) round; also un-finishes the game if it was over. */
    suspend fun reopenRound(gameId: Long): GameState = mutate(gameId) { s, now -> s.reopenRound(now) }

    suspend fun reorderSeats(gameId: Long, order: List<Long>): GameState = mutate(gameId) { s, _ -> s.reorderSeats(order) }

    suspend fun moveSeat(gameId: Long, from: Int, to: Int): GameState = mutate(gameId) { s, _ -> s.moveSeat(from, to) }

    /** List-view arrange drop: the players at seats [a] and [b] swap places. */
    suspend fun swapSeats(gameId: Long, a: Int, b: Int): GameState = mutate(gameId) { s, _ -> s.swapSeats(a, b) }

    /** Drops a card at [x], [y] (normalized 0–1) in the table view; seating order follows clockwise. */
    suspend fun movePosition(gameId: Long, playerId: Long, x: Float, y: Float): GameState =
        mutate(gameId) { s, _ -> s.movePosition(playerId, TablePosition(x, y)) }

    /** Switches the game's view and remembers it as the app preference. */
    suspend fun setViewMode(gameId: Long, mode: ViewMode): GameState = db.withTransaction {
        settingsDao.put(SettingEntity(KEY_VIEW_MODE, mode.name))
        mutate(gameId) { s, _ -> s.withViewMode(mode) }
    }

    /** Round menu "Target points": this game's target (game over is checked with it at the round close). */
    suspend fun setTarget(gameId: Long, target: Int): GameState = mutate(gameId) { s, now -> s.withTarget(target, now) }

    suspend fun setPreferredViewMode(mode: ViewMode) = settingsDao.put(SettingEntity(KEY_VIEW_MODE, mode.name))

    /**
     * "End game" from the round menu: closes the running round as it is (open players BUST) and
     * finishes the game with the current leader(s) as winners (recorded as finished, not abandoned).
     */
    suspend fun endGame(gameId: Long): GameState = mutate(gameId) { s, now -> s.endGame(now) }

    /**
     * Home → long-press → Delete game: removes the game and everything keyed by it (seats + table
     * positions, rounds, results, their cards, total adjustments; foreign-key cascades) in one
     * transaction. Players stay, except ones the user already deleted (archived) that no remaining
     * game shows any more. Works for the running game too (Home then has nothing to resume).
     */
    suspend fun deleteGame(gameId: Long) = db.withTransaction {
        gameDao.deleteGame(gameId)
        playerDao.deleteUnreferencedArchived()
    }

    suspend fun loadGame(gameId: Long): GameState? =
        gameDao.load(gameId)?.toState()?.copy(scanDirection = parseScanDirection(settingsDao.get(KEY_SCAN_DIRECTION)))

    // ---- Internals -----------------------------------------------------------------------

    private suspend fun mutate(gameId: Long, op: (GameState, Long) -> GameState): GameState = db.withTransaction {
        val details = checkNotNull(gameDao.load(gameId)) { "No game $gameId" }
        // The scan direction is an app setting carried on the state (Scan next after saving).
        val direction = parseScanDirection(settingsDao.get(KEY_SCAN_DIRECTION))
        val old = details.toState().copy(scanDirection = direction)
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

        // Adjustments: new ones have id 0, removed ones are missing from [new].
        val keptIds = new.adjustments.map { it.id }.toSet()
        for (a in details.adjustments) if (a.id !in keptIds) gameDao.deleteAdjustment(a.id)
        for (a in new.adjustments) if (a.id == 0L) gameDao.insertAdjustment(a.toEntity(gameId))

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
        const val KEY_SAVE_SCANS = "save_scans_for_training"
        const val KEY_SCAN_DIRECTION = "scan_direction"

        fun parseScanDirection(value: String?): ScanDirection =
            value?.let { v -> ScanDirection.entries.firstOrNull { it.name == v } } ?: ScanDirection.CLOCKWISE
    }
}
