package net.moesli.scan7.game

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Immutable state of one game. All mutations return a new state and throw
 * [IllegalArgumentException]/[IllegalStateException] for actions that are not allowed.
 *
 * Invariants: [rounds] is never empty, numbered 1..n; every round except the last is closed;
 * a closed round has no open results. The game is over only when the last round is closed and
 * someone's total reached [target], or it was ended early from the menu ([endedEarly], see
 * [isOver]); [finishedAt] follows that automatically.
 *
 * What depends on the game ([kind]: who wins, bust, the cards' points, the round ender) comes from
 * its [GameRules]; "best" below means highest for Flip 7 and lowest for Skyjo.
 *
 * Mutations that record a time take `now` (epoch millis); this class never reads the clock.
 */
data class GameState(
    val id: Long = 0,
    val kind: GameKind = GameKind.FLIP7,
    /** Skyjo: how the round ender is penalised ([RoundState.endedBy]); unused by games without a round ender. */
    val enderPenalty: EnderPenalty = EnderPenalty.OFFICIAL,
    val target: Int = DEFAULT_TARGET,
    /** Seating order (= clockwise order around the table = list order). */
    val seats: List<Seat>,
    val rounds: List<RoundState>,
    val startedAt: Long = 0,
    val finishedAt: Long? = null,
    /** Ended from the menu before anyone won; never set by the rules. */
    val abandoned: Boolean = false,
    /**
     * Ended early from the round menu ("End game", [endGame]): the game is over with the current
     * leader(s) as winners although nobody reached [target]. Cleared by [reopenRound].
     */
    val endedEarly: Boolean = false,
    /** View chosen for this game; `null` = use the app preference / default. */
    val viewMode: ViewMode? = null,
    /**
     * Manual total adjustments ("Adjust total"), oldest first. They count in [totals] (and so in
     * leaders, standings, ring progress and game over) but are not round results.
     */
    val adjustments: List<ScoreAdjustment> = emptyList(),
    /**
     * "Scan next" order: an app-wide setting, not part of the stored game (the repository fills it
     * in from the settings table). Default for [nextOpenPlayer].
     */
    val scanDirection: ScanDirection = ScanDirection.CLOCKWISE,
    /**
     * Players who joined after round 1 ("Players" in the round menu): player id → the round they
     * joined in. Missing = played from round 1. Only for players in [seats].
     */
    val joinedRounds: Map<Long, Int> = emptyMap(),
    /**
     * Players who left mid-game, in the order they left. They are not in [seats] (so not in the
     * round, Scan next, progress, leaders or winners), but their past results and adjustments stay
     * in the scoreboard and in [totals].
     */
    val departed: List<DepartedPlayer> = emptyList(),
    /**
     * Multiplayer: the players this phone's "Scan next" walks (a guest: the ones it scans; the host:
     * everyone no guest scans), `null` = everyone. Not stored; the repository fills it in like
     * [scanDirection] (docs/dev/MULTI_DEVICE.md rule 16).
     */
    val scanOnly: Set<Long>? = null,
) {
    init {
        require(seats.isNotEmpty()) { "A game needs at least one player" }
        require(seats.size <= MAX_PLAYERS) { "At most $MAX_PLAYERS players" }
        require(seats.map { it.playerId }.toSet().size == seats.size) { "Duplicate player" }
        require(departed.map { it.playerId }.toSet().size == departed.size) { "Duplicate departed player" }
        require(departed.none { d -> seats.any { it.playerId == d.playerId } }) { "A departed player can't be seated" }
        require(target > 0) { "Target must be positive" }
        require(rounds.isNotEmpty()) { "A game has at least one round" }
        require(rounds.mapIndexed { i, r -> r.number == i + 1 }.all { it }) { "Rounds must be numbered 1..n" }
        require(rounds.dropLast(1).all { it.isClosed }) { "Only the last round may be open" }
    }

    val rules: GameRules get() = kind.rules

    // ---- Queries -------------------------------------------------------------------------

    /** Players at the table (who play the running round), in seating order. */
    val playerIds: List<Long> get() = seats.map { it.playerId }

    /**
     * Everyone who played in this game: [playerIds] (seating order), then the [departed] players in
     * the order they left. Scoreboard columns and the Home history use this.
     */
    val everyoneIds: List<Long> get() = playerIds + departed.map { it.playerId }

    /** Round [playerId] joined in (1 = from the start). */
    fun joinedRound(playerId: Long): Int =
        joinedRounds[playerId] ?: departed.firstOrNull { it.playerId == playerId }?.joinedRound ?: 1

    /** Round [playerId] left in, `null` while they play. */
    fun leftRound(playerId: Long): Int? = departed.firstOrNull { it.playerId == playerId }?.leftRound

    /**
     * Whether [playerId] plays / played [round]: the running round = everyone seated; a closed round
     * = everyone with a result there (a player who joined later or had left has none: "–").
     */
    fun playedIn(playerId: Long, round: Int): Boolean {
        val r = round(round)
        return if (r.isClosed) playerId in r.results else playerId in playerIds
    }

    val currentRound: RoundState get() = rounds.last()

    fun round(number: Int): RoundState {
        require(number in 1..rounds.size) { "No round $number" }
        return rounds[number - 1]
    }

    fun seatIndex(playerId: Long): Int = playerIds.indexOf(playerId).also {
        require(it >= 0) { "Player $playerId is not in this game" }
    }

    /** [playerId]'s result in [round] (any player of the game, also a departed one; open if none). */
    fun result(playerId: Long, round: Int = currentRound.number): PlayerResult {
        require(playerId in everyoneIds) { "Player $playerId is not in this game" }
        return round(round).result(playerId)
    }

    fun status(playerId: Long, round: Int = currentRound.number): ResultStatus = result(playerId, round).status

    /**
     * Totals over all rounds (incl. points already scored in the running round) plus all
     * [adjustments], for [everyoneIds] (seated players first, in seating order; departed ones keep
     * what they had). The single source of truth for every total in the app.
     */
    val totals: Map<Long, Int> get() = totalsThrough(rounds.size)

    /** Totals after rounds 1..[round] plus the adjustments made up to that round, in seating order. */
    fun totalsThrough(round: Int): Map<Long, Int> {
        require(round in 0..rounds.size) { "No round $round" }
        val counted = rounds.take(round)
        val adjusted = adjustments.filter { it.round <= round }
        return everyoneIds.associateWith { p -> counted.sumOf { roundPoints(it, p) } + adjusted.filter { it.playerId == p }.sumOf { it.delta } }
    }

    /**
     * [playerId]'s counted points in [round]: as entered, doubled for a penalised round ender
     * ([isDoubled]). Every total uses this.
     */
    fun roundPoints(round: RoundState, playerId: Long): Int =
        round.points(playerId).let { if (isDoubled(round, playerId)) it * 2 else it }

    /** [roundPoints] by round number. */
    fun roundPoints(round: Int, playerId: Long): Int = roundPoints(round(round), playerId)

    /**
     * Skyjo: [playerId] ended [round] and the game's [enderPenalty] doubles their points (they don't
     * have the strictly lowest score of the round). Always `false` for games without a round ender.
     */
    fun isDoubled(round: RoundState, playerId: Long): Boolean {
        if (!rules.hasRoundEnder || round.endedBy != playerId) return false
        val res = round.result(playerId)
        if (!res.isDone) return false
        val others = round.results.filter { (p, r) -> p != playerId && r.isDone }.map { it.value.points }
        return enderPenalty.doubles(res.points, others)
    }

    fun total(playerId: Long): Int = totals.getValue(playerId)

    /** [playerId]'s adjustments, oldest first. */
    fun adjustmentsOf(playerId: Long): List<ScoreAdjustment> = adjustments.filter { it.playerId == playerId }

    /** Sum of [playerId]'s adjustments (0 without any). */
    fun adjustmentTotal(playerId: Long): Int = adjustmentsOf(playerId).sumOf { it.delta }

    /** Totals of the seated players only (leaders, winners and game over ignore departed players). */
    private val seatedTotals: Map<Long, Int> get() = totals.filterKeys { it in playerIds }

    /**
     * Seated player(s) with the best total, in seating order; empty while nobody has points (lowest
     * wins: while nobody has a result or adjustment yet).
     */
    val leaders: List<Long>
        get() {
            val t = seatedTotals
            if (rules.lowestWins) {
                val started = adjustments.isNotEmpty() || rounds.any { r -> r.results.values.any { it.isDone } }
                return if (!started) emptyList() else t.filterValues { it == t.values.min() }.keys.toList()
            }
            val best = t.values.max()
            return if (best <= 0) emptyList() else t.filterValues { it == best }.keys.toList()
        }

    /** Open players of [round], in seating order. */
    fun openPlayers(round: Int = currentRound.number): List<Long> =
        round(round).let { r -> playerIds.filter { r.result(it).isOpen } }

    fun doneCount(round: Int = currentRound.number): Int = seats.size - openPlayers(round).size

    val isRoundComplete: Boolean get() = openPlayers().isEmpty()

    /**
     * Next open player of the running round for "Scan next", walking the seats in [direction]
     * (clockwise = seating order): the first open player after [after] (wrapping around), or, if
     * [after] is `null`, the first open player starting at the first seat. [after] itself is
     * returned only if it is the sole open player. `null` when nobody is open or the round is
     * closed.
     */
    fun nextOpenPlayer(after: Long? = null, direction: ScanDirection = scanDirection): Long? {
        if (currentRound.isClosed) return null
        val open = openPlayers().filter { scanOnly == null || it in scanOnly }.toSet()
        if (open.isEmpty()) return null
        val n = seats.size
        val step = if (direction == ScanDirection.CLOCKWISE) 1 else -1
        // Without [after]: start at the first seat itself (offset 0), else at the seat after it.
        val (start, offsets) = if (after == null) 0 to (0 until n) else seatIndex(after) to (1..n)
        return offsets.map { playerIds[(start + step * it).mod(n)] }.first { it in open }
    }

    /**
     * Game over: the last round is closed and someone reached [target] or the game was
     * [endedEarly]. Never mid-round.
     */
    val isOver: Boolean get() = currentRound.isClosed && (endedEarly || seatedTotals.values.any { it >= target })

    /**
     * Best total of the seated players once [isOver] (several on an exact tie = shared win), in
     * seating order. A player who left can't win.
     */
    val winners: List<Long>
        get() {
            if (!isOver) return emptyList()
            val t = seatedTotals
            val best = if (rules.lowestWins) t.values.min() else t.values.max()
            return t.filterValues { it == best }.keys.toList()
        }

    /**
     * Rank of each player by counted points in [round] (1 = best; equal points share a rank, the
     * next rank is skipped: 1, 1, 3). A bust ranks below a scored 0; open players rank last.
     */
    fun roundRanks(round: Int = currentRound.number): Map<Long, Int> {
        val r = round(round)
        val sign = if (rules.lowestWins) -1L else 1L
        return rank { p ->
            val res = r.result(p)
            val statusKey = when (res.status) {
                ResultStatus.SCORED -> 2
                ResultStatus.BUST -> 1
                ResultStatus.OPEN -> 0
            }
            // Open players rank last also when the lowest wins: their 0 must not beat a real score.
            if (res.isOpen) Long.MIN_VALUE else sign * roundPoints(r, p) * 4 + statusKey
        }
    }

    /** Standings after rounds 1..[round] by total (same ranking scheme as [roundRanks]). */
    fun standings(round: Int = rounds.size): Map<Long, Int> {
        val t = totalsThrough(round)
        val sign = if (rules.lowestWins) -1L else 1L
        return rank { sign * t.getValue(it) }
    }

    private fun rank(key: (Long) -> Long): Map<Long, Int> {
        val keys = playerIds.associateWith(key)
        return playerIds.associateWith { p -> 1 + keys.values.count { it > keys.getValue(p) } }
    }

    // ---- Scoring -------------------------------------------------------------------------

    /**
     * Scores [playerId] from confirmed cards (scan review OK, manual save, edit) by the game's
     * [GameRules.score] (Flip 7: duplicate numbers make it a bust, 0 points). [round] defaults to
     * the running round; a closed round can be edited and totals / game over are recomputed.
     */
    fun scoreCards(
        playerId: Long,
        cards: List<ConfirmedCard>,
        source: ResultSource,
        now: Long,
        round: Int = currentRound.number,
    ): GameState {
        require(source == ResultSource.SCAN || source == ResultSource.MANUAL) { "Cards come from a scan or manual entry" }
        require(cards.all { rules.cardFromLabel(it.card.label) == it.card }) { "Cards of another game" }
        val score = rules.score(cards)
        val result = PlayerResult(
            status = if (score.bust) ResultStatus.BUST else ResultStatus.SCORED,
            points = score.points,
            source = source,
            cards = cards,
            updatedAt = now,
        )
        return setResult(playerId, round, result, now)
    }

    /** Sets [points] directly without cards (manual correction; at least [GameRules.minPoints]). */
    fun scorePoints(playerId: Long, points: Int, now: Long, round: Int = currentRound.number): GameState {
        require(points >= rules.minPoints) { "Points below ${rules.minPoints}" }
        val result = PlayerResult(ResultStatus.SCORED, points, ResultSource.MANUAL, emptyList(), now)
        return setResult(playerId, round, result, now)
    }

    /** One-tap bust (in-card Bust button, player sheet, or editing a closed round). */
    fun bust(playerId: Long, now: Long, round: Int = currentRound.number): GameState {
        check(rules.hasBust) { "$kind has no bust" }
        return setResult(playerId, round, PlayerResult(ResultStatus.BUST, 0, ResultSource.BUST_TAP, emptyList(), now), now)
    }

    /** Undo of [bust] from the toast: back to open. Only for a tap bust in the running round. */
    fun undoBust(playerId: Long, now: Long): GameState {
        val res = result(playerId)
        check(res.status == ResultStatus.BUST && res.source == ResultSource.BUST_TAP) { "Nothing to undo" }
        return resetToOpen(playerId, now)
    }

    /** "Reset to open" from the player sheet. Only in the running (unclosed) round. */
    fun resetToOpen(playerId: Long, now: Long): GameState =
        setResult(playerId, currentRound.number, PlayerResult(updatedAt = now), now)

    private fun setResult(playerId: Long, round: Int, result: PlayerResult, now: Long): GameState {
        require(playerId in everyoneIds) { "Player $playerId is not in this game" }
        // A round from before they joined / after they left has no result to edit ("–").
        require(playedIn(playerId, round)) { "Player $playerId didn't play round $round" }
        val r = round(round)
        check(!abandoned) { "Game was abandoned" }
        check(!(r.isClosed && result.isOpen)) { "A closed round can't have open players" }
        val updated = r.copy(results = r.results + (playerId to result))
        return copy(rounds = rounds.replace(round, updated)).settled(now)
    }

    // ---- Total adjustments ---------------------------------------------------------------

    /**
     * "Adjust total": adds [delta] (signed, not 0, at most [MAX_ADJUSTMENT] either way) to
     * [playerId]'s total with an optional [note], recorded for the current round. The game-over
     * rule is unchanged: mid-round it never ends the game; on the end-of-round view (round closed)
     * totals are re-checked like for any edit of a closed round.
     */
    fun adjustTotal(playerId: Long, delta: Int, note: String?, now: Long): GameState {
        seatIndex(playerId)
        require(delta != 0) { "Adjustment can't be 0" }
        require(delta in -MAX_ADJUSTMENT..MAX_ADJUSTMENT) { "Adjustment out of range" }
        check(!abandoned) { "Game was abandoned" }
        val adjustment = ScoreAdjustment(
            playerId = playerId,
            delta = delta,
            note = note?.trim()?.takeIf { it.isNotEmpty() },
            round = currentRound.number,
            createdAt = now,
        )
        return copy(adjustments = adjustments + adjustment).settled(now)
    }

    /** Removes the stored adjustment [adjustmentId] (totals / game over recomputed). */
    fun removeAdjustment(adjustmentId: Long, now: Long): GameState {
        check(!abandoned) { "Game was abandoned" }
        require(adjustments.any { it.id == adjustmentId }) { "No adjustment $adjustmentId" }
        return copy(adjustments = adjustments.filterNot { it.id == adjustmentId }).settled(now)
    }

    /** Undo of [removeAdjustment]: puts [adjustment] back as it was (same round, note and time; new id). */
    fun restoreAdjustment(adjustment: ScoreAdjustment, now: Long): GameState {
        require(adjustment.playerId in everyoneIds) { "Player ${adjustment.playerId} is not in this game" }
        check(!abandoned) { "Game was abandoned" }
        require(adjustment.round in 1..rounds.size) { "No round ${adjustment.round}" }
        val restored = adjustment.copy(id = 0)
        return copy(adjustments = (adjustments + restored).sortedBy { it.createdAt }).settled(now)
    }

    // ---- Round lifecycle -----------------------------------------------------------------

    /**
     * Closes the running round: every open player becomes BUST (source CLOSE_ROUND); in a game
     * without bust they score 0 instead (same source, so [reopenRound] opens them again).
     */
    fun closeRound(now: Long): GameState {
        val r = currentRound
        check(!r.isClosed) { "Round ${r.number} is already closed" }
        check(!abandoned) { "Game was abandoned" }
        val closedStatus = if (rules.hasBust) ResultStatus.BUST else ResultStatus.SCORED
        val results = playerIds.associateWith { p ->
            val res = r.result(p)
            if (res.isOpen) PlayerResult(closedStatus, 0, ResultSource.CLOSE_ROUND, emptyList(), now) else res
        }
        return copy(rounds = rounds.replace(r.number, r.copy(results = results, closedAt = now))).settled(now)
    }

    /**
     * Skyjo: [playerId] ended [round] (revealed all cards first; `null` = nobody / not known), so the
     * [enderPenalty] may double their points. Set when the round is closed, changeable afterwards
     * (also on a closed round: totals and game over are recomputed).
     */
    fun withRoundEnder(playerId: Long?, now: Long, round: Int = currentRound.number): GameState {
        check(rules.hasRoundEnder) { "$kind has no round ender" }
        check(!abandoned) { "Game was abandoned" }
        require(playerId == null || playedIn(playerId, round)) { "Player $playerId didn't play round $round" }
        val r = round(round)
        return copy(rounds = rounds.replace(round, r.copy(endedBy = playerId))).settled(now)
    }

    /** Starts the next round with everyone open. Only after the round is closed and the game isn't over. */
    fun nextRound(): GameState {
        check(currentRound.isClosed) { "Close round ${currentRound.number} first" }
        check(!isOver) { "Game is over" }
        check(!abandoned) { "Game was abandoned" }
        val next = RoundState(rounds.size + 1, playerIds.associateWith { PlayerResult.OPEN })
        return copy(rounds = rounds + next)
    }

    /**
     * "Next round" / confirmed "Close round" in one step: closes the running round ([closeRound])
     * and, unless the game is now over, starts the next one ([nextRound]).
     */
    fun closeAndStartNext(now: Long): GameState {
        val closed = closeRound(now)
        return if (closed.isOver) closed else closed.nextRound()
    }

    /**
     * Reopens the last round (only while no next round was started): players busted by closing
     * become open again, the game-over state is cleared. Older rounds are edited in place instead
     * (pass `round` to the scoring functions).
     */
    fun reopenRound(now: Long): GameState {
        val r = currentRound
        check(r.isClosed) { "Round ${r.number} is not closed" }
        check(!abandoned) { "Game was abandoned" }
        val results = r.results.mapValues { (_, res) ->
            if (res.source == ResultSource.CLOSE_ROUND) PlayerResult(updatedAt = now) else res
        }
        return copy(rounds = rounds.replace(r.number, r.copy(results = results, closedAt = null)), endedEarly = false).settled(now)
    }

    /**
     * "End game" from the round menu: the running round is closed as it is (open players become
     * BUST, as in [closeRound]) and the game is over; the current leader(s) win (ties share the
     * win, see [winners]). Also works on the end-of-round view (round already closed). A game that
     * is already over stays as it is. Recorded as finished, not [abandoned].
     */
    fun endGame(now: Long): GameState {
        check(!abandoned) { "Game was abandoned" }
        if (isOver) return this
        val closed = if (currentRound.isClosed) this else closeRound(now)
        return closed.copy(endedEarly = true).settled(now)
    }

    /** Ends the game from the menu without a winner. */
    fun abandon(now: Long): GameState = if (finishedAt != null) this else copy(abandoned = true, finishedAt = now)

    /** Keeps [finishedAt] in sync with [isOver] after a change. */
    private fun settled(now: Long): GameState = when {
        abandoned -> this
        isOver && finishedAt == null -> copy(finishedAt = now)
        !isOver && finishedAt != null -> copy(finishedAt = null)
        else -> this
    }

    // ---- Players joining / leaving mid-game ("Players" in the round menu) -------------------

    /** Whether players can join or leave now: only while a round is running (not over, not abandoned). */
    val canChangePlayers: Boolean get() = !abandoned && !isOver && !currentRound.isClosed

    /**
     * [playerId] joins the running round as open, seated after the last seat (between the last and
     * the first seat, see [seatsFor]) unless [position] is given. Their earlier rounds count 0 and
     * show "–"; a player who had left comes back with their old results.
     */
    fun addPlayer(playerId: Long, now: Long, position: TablePosition? = null): GameState {
        check(seats.size < MAX_PLAYERS) { "At most $MAX_PLAYERS players" }
        require(playerId !in playerIds) { "Player $playerId is already seated" }
        val lineup = seatsFor(playerIds + playerId).let { s ->
            if (position == null) s else s.map { if (it.playerId == playerId) Seat(playerId, position.clamped()) else it }
        }
        return withLineup(lineup.sortedBy { clockwiseAngle(it.position) }, now)
    }

    /**
     * [playerId] leaves the running round: any result they have in it is dropped, their past
     * results and adjustments stay ("left" in the scoreboard). Someone who joined this very round
     * and has nothing recorded just goes. The game keeps at least [MIN_PLAYERS] players.
     */
    fun removePlayer(playerId: Long, now: Long): GameState {
        seatIndex(playerId)
        return withLineup(seats.filter { it.playerId != playerId }, now)
    }

    /**
     * The changed lineup from the "Players" flow in one step: [lineup] (seating order + table
     * positions) is the new table. Seated players missing from it leave ([removePlayer]), new ones
     * join the running round as open ([addPlayer]). [MIN_PLAYERS]..[MAX_PLAYERS] players.
     */
    fun withLineup(lineup: List<Seat>, now: Long): GameState {
        check(canChangePlayers) { "Players can only change during a running round" }
        val ids = lineup.map { it.playerId }
        require(ids.toSet().size == ids.size) { "Duplicate player" }
        require(ids.size in MIN_PLAYERS..MAX_PLAYERS) { "A game has $MIN_PLAYERS to $MAX_PLAYERS players" }
        val round = currentRound
        val leaving = playerIds.filter { it !in ids }
        val joining = ids.filter { it !in playerIds }

        var departedNow = departed.filter { it.playerId !in joining }
        var joined = joinedRounds.filterKeys { it in ids }
        for (p in leaving) {
            val playedBefore = rounds.any { it.isClosed && p in it.results }
            val adjusted = adjustments.any { it.playerId == p }
            // Joined this round and nothing recorded: gone without a trace (no "left" column).
            if (playedBefore || adjusted) departedNow = departedNow + DepartedPlayer(p, joinedRound(p), round.number)
        }
        for (p in joining) joined = if (round.number > 1) joined + (p to round.number) else joined - p
        val results = round.results.filterKeys { it !in leaving } + joining.associateWith { PlayerResult(updatedAt = now) }
        val endedBy = round.endedBy?.takeIf { it !in leaving }
        return copy(
            seats = lineup.map { Seat(it.playerId, it.position.clamped()) },
            rounds = rounds.replace(round.number, round.copy(results = results, endedBy = endedBy)),
            joinedRounds = joined,
            departed = departedNow,
        ).settled(now)
    }

    /**
     * Default seats for a changed lineup [ids] (seated players first, in their order; new ones
     * appended): if the table is still on the default ring, everyone moves to the default ring of
     * the new size; otherwise the players who stay keep their spots and new ones get spots on the
     * ring between the last and the first seat ("after the last seat"). Clockwise order = seating
     * order, as always.
     */
    fun seatsFor(ids: List<Long>): List<Seat> {
        val staying = seats.filter { it.playerId in ids }
        val joining = ids.filter { id -> seats.none { it.playerId == id } }
        val order = staying.map { it.playerId } + joining
        val onRing = seats.map { it.position }.zip(defaultPositions(seats.size)).all { (a, b) ->
            abs(a.x - b.x) < RING_TOLERANCE && abs(a.y - b.y) < RING_TOLERANCE
        }
        if (onRing) return order.zip(defaultPositions(order.size)) { p, pos -> Seat(p, pos) }
        val last = staying.lastOrNull()?.let { clockwiseAngle(it.position) } ?: 0.0
        val first = staying.firstOrNull()?.let { clockwiseAngle(it.position) + 2 * PI } ?: (2 * PI)
        val added = joining.mapIndexed { i, p -> Seat(p, ringPosition(last + (first - last) * (i + 1) / (joining.size + 1))) }
        return (staying + added).sortedBy { clockwiseAngle(it.position) }
    }

    // ---- Seating -------------------------------------------------------------------------

    /**
     * New seating order (list view arrange, seating step). The table positions stay where they
     * are and are handed out clockwise from the top in the new order, so list order always equals
     * clockwise table order.
     */
    fun reorderSeats(order: List<Long>): GameState {
        require(order.size == seats.size && order.toSet() == playerIds.toSet()) { "Order must be a permutation of the players" }
        val slots = seats.map { it.position }.sortedClockwise()
        return copy(seats = order.mapIndexed { i, p -> Seat(p, slots[i]) })
    }

    /** Moves the player at seat [from] to seat [to] (others shift). */
    fun moveSeat(from: Int, to: Int): GameState {
        require(from in seats.indices && to in seats.indices) { "Seat index out of range" }
        val order = playerIds.toMutableList()
        order.add(to, order.removeAt(from))
        return reorderSeats(order)
    }

    /**
     * List-view arrange drop: the players at seats [a] and [b] swap places (prototype `swap`);
     * everyone else keeps their seat. Positions are handed out as in [reorderSeats].
     */
    fun swapSeats(a: Int, b: Int): GameState {
        require(a in seats.indices && b in seats.indices) { "Seat index out of range" }
        if (a == b) return this
        val order = playerIds.toMutableList()
        order[a] = order[b].also { order[b] = order[a] }
        return reorderSeats(order)
    }

    /**
     * Moves a card in the table view (clamped to 0–1). The seating order is then re-derived from
     * the positions: clockwise from the top around the center.
     */
    fun movePosition(playerId: Long, position: TablePosition): GameState {
        val i = seatIndex(playerId)
        val moved = seats.toMutableList().apply { this[i] = Seat(playerId, position.clamped()) }
        return copy(seats = moved.sortedBy { clockwiseAngle(it.position) })
    }

    fun withViewMode(mode: ViewMode?): GameState = copy(viewMode = mode)

    /**
     * Changes the game's [target] (round menu "Target points"). Mid-round this only decides the
     * game-over check at the next round close ([isOver]); on a closed last round [finishedAt]
     * follows at once.
     */
    fun withTarget(target: Int, now: Long): GameState {
        require(target > 0) { "Target must be positive" }
        check(!abandoned) { "Game was abandoned" }
        return copy(target = target).settled(now)
    }

    /** Someone's total (incl. adjustments) is at or above [target]: with that target the game ends at the round close. */
    fun reachesTarget(target: Int): Boolean = totals.values.any { it >= target }

    companion object {
        /**
         * New game of [kind] with [players] in seating order (avatar colors may repeat).
         * Positions default to an even ring; with [previousPositions] (from the last game with the
         * same player set) those spots are reused, handed out clockwise in the new seating order.
         * Explicit [positions] (one per player, in seating order: arranged on the seating step) win
         * over both and are kept as they are.
         */
        fun newGame(
            players: List<Player>,
            target: Int = DEFAULT_TARGET,
            startedAt: Long = 0,
            previousPositions: Map<Long, TablePosition>? = null,
            id: Long = 0,
            positions: List<TablePosition>? = null,
            kind: GameKind = GameKind.FLIP7,
            enderPenalty: EnderPenalty = EnderPenalty.OFFICIAL,
        ): GameState {
            require(players.isNotEmpty()) { "A game needs at least one player" }
            require(players.size <= MAX_PLAYERS) { "At most $MAX_PLAYERS players" }
            require(positions == null || positions.size == players.size) { "One position per player" }
            val ids = players.map { it.id }
            return GameState(
                id = id,
                kind = kind,
                enderPenalty = enderPenalty,
                target = target,
                seats = positions?.let { p -> ids.mapIndexed { i, id -> Seat(id, p[i].clamped()) } }
                    ?: initialSeats(ids, previousPositions),
                rounds = listOf(RoundState(1, ids.associateWith { PlayerResult.OPEN })),
                startedAt = startedAt,
            )
        }

        /**
         * Seats for player [ids] in seating order: the spots of [previousPositions] when it covers
         * exactly these players (handed out clockwise in this order), else the default ring.
         */
        fun initialSeats(ids: List<Long>, previousPositions: Map<Long, TablePosition>? = null): List<Seat> {
            val reuse = previousPositions != null && previousPositions.keys == ids.toSet()
            val slots = if (reuse) previousPositions.values.toList().sortedClockwise() else defaultPositions(ids.size)
            return ids.mapIndexed { i, p -> Seat(p, slots[i]) }
        }
    }
}

/** How far (normalized) a seat may be off the default ring and still count as "on the ring". */
private const val RING_TOLERANCE = 0.01f

/** Spot on the default ellipse at [angle] (clockwise from 12 o'clock, radians). */
private fun ringPosition(angle: Double) = TablePosition(
    x = (0.5 + DEFAULT_ELLIPSE_RX * sin(angle)).toFloat(),
    y = (0.5 - DEFAULT_ELLIPSE_RY * cos(angle)).toFloat(),
)

private fun List<RoundState>.replace(number: Int, round: RoundState): List<RoundState> =
    mapIndexed { i, r -> if (i == number - 1) round else r }
