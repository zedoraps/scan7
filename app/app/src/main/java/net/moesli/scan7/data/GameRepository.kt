package net.moesli.scan7.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import net.moesli.scan7.BuildConfig
import net.moesli.scan7.game.Avatar
import net.moesli.scan7.game.ConfirmedCard
import net.moesli.scan7.game.DEFAULT_TARGET
import net.moesli.scan7.game.EnderPenalty
import net.moesli.scan7.game.GameKind
import net.moesli.scan7.game.GameState
import net.moesli.scan7.game.MAX_NAME_LENGTH
import net.moesli.scan7.game.Player
import net.moesli.scan7.game.ResultSource
import net.moesli.scan7.game.ScanDirection
import net.moesli.scan7.game.ScoreAdjustment
import net.moesli.scan7.game.Seat
import net.moesli.scan7.game.TablePosition
import net.moesli.scan7.game.ViewMode
import net.moesli.scan7.game.newPlayerAvatar
import net.moesli.scan7.multiplayer.AdjustTotal
import net.moesli.scan7.multiplayer.AdjustmentDto
import net.moesli.scan7.multiplayer.Bust
import net.moesli.scan7.multiplayer.ChangeLineup
import net.moesli.scan7.multiplayer.CloseRound
import net.moesli.scan7.multiplayer.CloseRoundAndStartNext
import net.moesli.scan7.multiplayer.EndGame
import net.moesli.scan7.multiplayer.GameAction
import net.moesli.scan7.multiplayer.MovePosition
import net.moesli.scan7.multiplayer.MoveSeat
import net.moesli.scan7.multiplayer.NextRound
import net.moesli.scan7.multiplayer.RemoveAdjustment
import net.moesli.scan7.multiplayer.ReopenRound
import net.moesli.scan7.multiplayer.ReorderSeats
import net.moesli.scan7.multiplayer.ResetToOpen
import net.moesli.scan7.multiplayer.RestoreAdjustment
import net.moesli.scan7.multiplayer.ScoreCards
import net.moesli.scan7.multiplayer.ScorePoints
import net.moesli.scan7.multiplayer.SetRoundEnder
import net.moesli.scan7.multiplayer.SetTarget
import net.moesli.scan7.multiplayer.SwapSeats
import net.moesli.scan7.multiplayer.UndoBust
import net.moesli.scan7.multiplayer.WireCard
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/** A game with everything the round / end-of-round / game-over screens need. */
data class GameSnapshot(
    val state: GameState,
    /** Every player of the game, by id (archived ones included). */
    val players: Map<Long, Player>,
    val preferredViewMode: ViewMode?,
    /** Multiplayer guest copy: the host's name; `null` = a game of this phone (GameEntity.joinedFrom). */
    val joinedFrom: String? = null,
) {
    /** A joined game is a read-only copy (only its live session changes it). */
    val isJoined: Boolean get() = joinedFrom != null

    /** Players in seating order. */
    val seatedPlayers: List<Player> get() = state.playerIds.map { players.getValue(it) }

    /** Everyone who played (seated in seating order, then the ones who left). */
    val everyone: List<Player> get() = state.everyoneIds.map { players.getValue(it) }

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

    /** Multiplayer: per shared game, the players this phone's "Scan next" walks ([GameState.scanOnly]). */
    private val scanFilters = MutableStateFlow<Map<Long, Set<Long>>>(emptyMap())


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

    /**
     * Home's Resume card: [activeGame] or the joined game this phone is still a guest of, whichever
     * started last (a guest's own older game must not stand in for the joined one).
     */
    val resumeGame: Flow<GameSnapshot?> =
        combine(activeGame, snapshots(gameDao.observeJoinedActive())) { own, joined ->
            listOfNotNull(own, joined).maxByOrNull { it.state.startedAt }
        }.distinctUntilChanged()

    /** Finished and abandoned games (Home history), most recently finished first. */
    val pastGames: Flow<List<GameSnapshot>> =
        combine(gameDao.observePast(), allPlayers, preferredViewMode) { games, players, pref ->
            games.map { GameSnapshot(it.toState(), players, pref, it.game.joinedFrom) }
        }.distinctUntilChanged()

    /** Any game by id (e.g. game over / history after it finished). */
    fun observeGame(gameId: Long): Flow<GameSnapshot?> = snapshots(gameDao.observe(gameId))

    private fun snapshots(source: Flow<GameWithDetails?>): Flow<GameSnapshot?> =
        combine(source, allPlayers, preferredViewMode, scanDirection, scanFilters) { details, players, pref, direction, filters ->
            details?.let {
                val state = it.toState().copy(scanDirection = direction, scanOnly = filters[it.game.id])
                GameSnapshot(state, players, pref, it.game.joinedFrom)
            }
        }.distinctUntilChanged()

    /** Sets (or clears with `null`) [gameId]'s "Scan next" players on this phone. */
    fun setScanFilter(gameId: Long, players: Set<Long>?) {
        scanFilters.update { if (players == null) it - gameId else it + (gameId to players) }
    }

    private fun GameState.filtered(): GameState = copy(scanOnly = scanFilters.value[id])

    // ---- Players -------------------------------------------------------------------------

    /**
     * Adds a player; without [avatar] a random face + pattern and the first color no other stored
     * player uses (else the first color). Returns the id.
     */
    suspend fun addPlayer(name: String, avatar: Avatar? = null): Long {
        val clean = name.trim().take(MAX_NAME_LENGTH).trim()
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
        val clean = name.trim().take(MAX_NAME_LENGTH).trim()
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

    /**
     * The seated players (seating order, without the ones who left mid-game) of the most recently
     * finished or abandoned game: New game preselects them. Empty without such a game.
     */
    suspend fun lastLineup(): List<Long> {
        val gameId = gameDao.lastFinishedId() ?: return emptyList()
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
        kind: GameKind = GameKind.FLIP7,
        enderPenalty: EnderPenalty = EnderPenalty.OFFICIAL,
    ): Long = db.withTransaction {
        val now = clock()
        val byId = playerDao.byIds(seating).associateBy { it.id }
        val players = seating.map { checkNotNull(byId[it]) { "No player $it" }.toPlayer() }
        val previousPositions = if (positions == null) previousPositions(seating) else null
        val state = GameState.newGame(players, target, now, previousPositions, positions = positions, kind = kind, enderPenalty = enderPenalty)
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
    ): GameState = apply(gameId) { s ->
        ScoreCards(playerId, round ?: s.currentRound.number, cards.map(WireCard::of), manual = source == ResultSource.MANUAL)
    }

    /** Sets points directly (manual correction without cards). */
    suspend fun scorePoints(gameId: Long, playerId: Long, points: Int, round: Int? = null): GameState =
        apply(gameId) { s -> ScorePoints(playerId, round ?: s.currentRound.number, points) }

    /** "Adjust total" from the player sheet: signed [delta] with an optional [note], for the current round. */
    suspend fun adjustTotal(gameId: Long, playerId: Long, delta: Int, note: String? = null): GameState =
        apply(gameId) { AdjustTotal(playerId, delta, note) }

    /** Removes a stored adjustment (player sheet ✕). */
    suspend fun removeAdjustment(gameId: Long, adjustmentId: Long): GameState =
        apply(gameId) { RemoveAdjustment(adjustmentId) }

    /** Undo of [removeAdjustment]: stores [adjustment] again as it was (new id). */
    suspend fun restoreAdjustment(gameId: Long, adjustment: ScoreAdjustment): GameState =
        apply(gameId) { RestoreAdjustment(AdjustmentDto.of(adjustment)) }

    /** One-tap bust. */
    suspend fun bust(gameId: Long, playerId: Long, round: Int? = null): GameState =
        apply(gameId) { s -> Bust(playerId, round ?: s.currentRound.number) }

    /** "Undo" on the bust toast. */
    suspend fun undoBust(gameId: Long, playerId: Long): GameState = apply(gameId) { s -> UndoBust(playerId, s.currentRound.number) }

    suspend fun resetToOpen(gameId: Long, playerId: Long): GameState =
        apply(gameId) { s -> ResetToOpen(playerId, s.currentRound.number) }

    /** Open players become BUST; sets finishedAt if the game is now over. */
    suspend fun closeRound(gameId: Long): GameState = apply(gameId) { s -> CloseRound(s.currentRound.number) }

    suspend fun nextRound(gameId: Long): GameState = apply(gameId) { s -> NextRound(s.currentRound.number) }

    /** Skyjo: who ended [round] (null = the running one; [playerId] null = nobody), before closing it or later. */
    suspend fun setRoundEnder(gameId: Long, playerId: Long?, round: Int? = null): GameState =
        apply(gameId) { s -> SetRoundEnder(round ?: s.currentRound.number, playerId) }

    /**
     * "Next round" (everyone done) or a confirmed "Close round": closes the running round and,
     * unless the game is now over, starts the next one, in one transaction.
     */
    suspend fun closeRoundAndStartNext(gameId: Long): GameState =
        apply(gameId) { s -> CloseRoundAndStartNext(s.currentRound.number) }

    /** Reopens the last (closed) round; also un-finishes the game if it was over. */
    suspend fun reopenRound(gameId: Long): GameState = apply(gameId) { s -> ReopenRound(s.currentRound.number) }

    /**
     * "Players" from the round menu: the changed lineup ([lineup] = seating order + table spots)
     * becomes the table in one transaction. New players join the running round as open; players who
     * are missing leave it (their result there is dropped, past results stay).
     */
    suspend fun changePlayers(gameId: Long, lineup: List<Seat>): GameState {
        val known = playerDao.byIds(lineup.map { it.playerId }).map { it.toPlayer() }
        return apply(gameId) { s -> ChangeLineup.of(lineup, known.filter { it.id !in s.playerIds }) }
    }

    /** One player joins the running round (seated after the last seat). */
    suspend fun addPlayerToGame(gameId: Long, playerId: Long): GameState = db.withTransaction {
        val player = checkNotNull(playerDao.byId(playerId)) { "No player $playerId" }.toPlayer()
        apply(gameId) { s -> ChangeLineup.of(s.addPlayer(playerId, clock()).seats, listOf(player)) }
    }

    /** One player leaves the running round. */
    suspend fun removePlayerFromGame(gameId: Long, playerId: Long): GameState = apply(gameId) { s ->
        s.seatIndex(playerId)
        ChangeLineup.of(s.seats.filter { it.playerId != playerId }, emptyList())
    }

    suspend fun reorderSeats(gameId: Long, order: List<Long>): GameState = apply(gameId) { ReorderSeats(order) }

    suspend fun moveSeat(gameId: Long, from: Int, to: Int): GameState = apply(gameId) { MoveSeat(from, to) }

    /** List-view arrange drop: the players at seats [a] and [b] swap places. */
    suspend fun swapSeats(gameId: Long, a: Int, b: Int): GameState = apply(gameId) { SwapSeats(a, b) }

    /** Drops a card at [x], [y] (normalized 0–1) in the table view; seating order follows clockwise. */
    suspend fun movePosition(gameId: Long, playerId: Long, x: Float, y: Float): GameState =
        apply(gameId) { MovePosition(playerId, x, y) }

    /** Switches the game's view and remembers it as the app preference. */
    suspend fun setViewMode(gameId: Long, mode: ViewMode): GameState = db.withTransaction {
        settingsDao.put(SettingEntity(KEY_VIEW_MODE, mode.name))
        mutate(gameId) { s, _ -> s.withViewMode(mode) }
    }

    /** Round menu "Target points": this game's target (game over is checked with it at the round close). */
    suspend fun setTarget(gameId: Long, target: Int): GameState = apply(gameId) { SetTarget(target) }

    suspend fun setPreferredViewMode(mode: ViewMode) = settingsDao.put(SettingEntity(KEY_VIEW_MODE, mode.name))

    /**
     * "End game" from the round menu: closes the running round as it is (open players BUST) and
     * finishes the game with the current leader(s) as winners (recorded as finished, not abandoned).
     */
    suspend fun endGame(gameId: Long): GameState = apply(gameId) { s -> EndGame(s.currentRound.number) }

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
        gameDao.load(gameId)?.toState()?.copy(scanDirection = parseScanDirection(settingsDao.get(KEY_SCAN_DIRECTION)))?.filtered()

    // ---- Action path (docs/dev/MULTI_DEVICE.md §4.1) -----------------------------------------

    /** Where a shared game's actions go instead of straight to the database (multiplayer session). */
    fun interface ActionRouter {
        /** Applies the action [build] makes from the current state; returns the state to show. */
        suspend fun perform(build: (GameState) -> GameAction): GameState
    }

    private val routers = ConcurrentHashMap<Long, ActionRouter>()

    /** Routes [gameId]'s actions through [router] (a host or guest session), or back to the database with `null`. */
    fun route(gameId: Long, router: ActionRouter?) {
        if (router == null) routers.remove(gameId) else routers[gameId] = router
    }

    /**
     * Every game change of the UI goes through here as a [GameAction] built from the current state:
     * straight into the database ([applyLocal]), or, while the game is shared, through its session.
     */
    suspend fun apply(gameId: Long, build: (GameState) -> GameAction): GameState =
        routers[gameId]?.perform(build) ?: applyLocal(gameId, build)

    /**
     * Applies the action in one transaction and returns the stored state (new adjustments with the
     * ids the database gave them). [now] is the action's time (the host passes the event's).
     * Joined games (guest copies) are read-only here.
     */
    suspend fun applyLocal(gameId: Long, build: (GameState) -> GameAction, now: Long = clock()): GameState = db.withTransaction {
        val details = checkNotNull(gameDao.load(gameId)) { "No game $gameId" }
        check(details.game.joinedFrom == null) { "Joined game $gameId is read-only" }
        val direction = parseScanDirection(settingsDao.get(KEY_SCAN_DIRECTION))
        val old = details.toState().copy(scanDirection = direction)
        val new = build(old).apply(old, now, seq = 0)
        persist(details, old, new)
        val stored = if (new.adjustments.any { it.id == 0L }) checkNotNull(gameDao.load(gameId)).toState().copy(scanDirection = direction) else new
        stored.filtered()
    }

    // ---- Multiplayer storage (docs/dev/MULTI_DEVICE.md §4.5) ----------------------------------

    private val shareDao = db.shareDao()

    /** This install's random multiplayer device id (created on first use; never shown). */
    suspend fun deviceId(): String = db.withTransaction {
        settingsDao.get(KEY_DEVICE_ID) ?: java.util.UUID.randomUUID().toString().also { settingsDao.put(SettingEntity(KEY_DEVICE_ID, it)) }
    }

    suspend fun shareSession(gameId: Long): ShareSessionEntity? = shareDao.get(gameId)

    suspend fun shareSessions(): List<ShareSessionEntity> = shareDao.all()

    suspend fun saveShareSession(session: ShareSessionEntity) = shareDao.put(session)

    suspend fun deleteShareSession(gameId: Long) = shareDao.delete(gameId)

    /** The joined copy of the shared game [uid] on this phone, if any. */
    suspend fun joinedGameId(uid: String): Long? = shareDao.joinedGame(uid)

    /**
     * A guest's local stand-ins for the host's [players] (hidden, `joinedOnly`): [known] maps host
     * ids to existing local ids (names / avatars are refreshed); new ones are created. Returns the
     * full host → local map.
     */
    suspend fun joinedPlayers(players: Collection<Player>, known: Map<Long, Long>): Map<Long, Long> = db.withTransaction {
        players.associate { p ->
            val local = known[p.id]?.let { playerDao.byId(it) }
            val a = p.avatar.normalized()
            val id = if (local == null) {
                playerDao.insert(
                    PlayerEntity(
                        name = p.name, avatarFace = a.face, avatarPattern = a.pattern, avatarColor = a.color,
                        createdAt = clock(), joinedOnly = true,
                    ),
                )
            } else {
                val updated = local.copy(name = p.name, avatarFace = a.face, avatarPattern = a.pattern, avatarColor = a.color)
                if (updated != local) playerDao.update(updated)
                local.id
            }
            p.id to id
        }
    }

    /** "Save players to my list" on a joined game: these hidden players become normal stored players. */
    suspend fun keepJoinedPlayers(localIds: Collection<Long>) = shareDao.keepPlayers(localIds.toList())

    /**
     * Stores [state] (already in local player ids) as the joined copy of a shared game: created on
     * first use ([localGameId] `null`), else updated in place. This phone's view choice and
     * adjustment row ids are kept. Returns the local game id.
     */
    suspend fun saveJoinedGame(localGameId: Long?, uid: String, hostName: String, state: GameState): Long = db.withTransaction {
        val existing = localGameId?.let { gameDao.load(it) }
        if (existing == null) {
            val entity = state.toGameEntity().copy(id = 0, viewMode = null, joinedFrom = hostName, gameUid = uid)
            val id = gameDao.insertGame(entity)
            val inserted = GameWithDetails(entity.copy(id = id), emptyList(), emptyList(), emptyList())
            persist(inserted, null, state.copy(id = id, viewMode = null, adjustments = state.adjustments.map { it.copy(id = 0) }))
            id
        } else {
            val old = existing.toState()
            val unused = old.adjustments.toMutableList()
            val adjustments = state.adjustments.map { a ->
                val same = unused.firstOrNull { o -> o.playerId == a.playerId && o.delta == a.delta && o.round == a.round && o.createdAt == a.createdAt && o.note == a.note }
                if (same != null) {
                    unused.remove(same)
                    a.copy(id = same.id)
                } else {
                    a.copy(id = 0)
                }
            }
            persist(existing, old, state.copy(id = existing.game.id, viewMode = old.viewMode, adjustments = adjustments))
            if (existing.game.joinedFrom != hostName) gameDao.updateGame(gameDao.load(existing.game.id)!!.game.copy(joinedFrom = hostName))
            existing.game.id
        }
    }

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
        val game = new.toGameEntity().copy(id = gameId, joinedFrom = details.game.joinedFrom, gameUid = details.game.gameUid)
        if (game != details.game) gameDao.updateGame(game)
        if (old == null || old.seats != new.seats || old.departed != new.departed || old.joinedRounds != new.joinedRounds) {
            // Players gone without a trace (joined and left in the same round) lose their row.
            val everyone = new.everyoneIds.toSet()
            for (seat in details.seats) if (seat.playerId !in everyone) gameDao.deleteSeat(gameId, seat.playerId)
            gameDao.upsertSeats(new.toSeatEntities(gameId))
        }

        // Adjustments: new ones have id 0, removed ones are missing from [new].
        val keptIds = new.adjustments.map { it.id }.toSet()
        for (a in details.adjustments) if (a.id !in keptIds) gameDao.deleteAdjustment(a.id)
        for (a in new.adjustments) if (a.id == 0L) gameDao.insertAdjustment(a.toEntity(gameId))

        val roundIds = details.rounds.associate { it.round.number to it.round.id }
        val resultIds = details.rounds.flatMap { r -> r.results.map { (r.round.number to it.result.playerId) to it.result.id } }.toMap()
        for (round in new.rounds) {
            val oldRound = old?.rounds?.getOrNull(round.number - 1)
            val roundId = roundIds[round.number]
                ?: gameDao.insertRound(RoundEntity(gameId = gameId, number = round.number, closedAt = round.closedAt, endedBy = round.endedBy))
            if (oldRound != null && (oldRound.closedAt != round.closedAt || oldRound.endedBy != round.endedBy)) {
                gameDao.updateRound(RoundEntity(roundId, gameId, round.number, round.closedAt, round.endedBy))
            }
            // Results of the players in that round: everyone seated in the running round; in a closed
            // one whoever has a result (players who joined later / had left have none).
            val inRound = if (round.isClosed) round.results.keys else new.playerIds.toSet() + round.results.keys
            // A player who left the running round: their result there is dropped.
            for ((key, id) in resultIds) if (key.first == round.number && key.second !in inRound) gameDao.deleteResult(id)
            for (playerId in inRound) {
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
        const val KEY_DEVICE_ID = "multiplayer_device_id"

        fun parseScanDirection(value: String?): ScanDirection =
            value?.let { v -> ScanDirection.entries.firstOrNull { it.name == v } } ?: ScanDirection.CLOCKWISE
    }
}
