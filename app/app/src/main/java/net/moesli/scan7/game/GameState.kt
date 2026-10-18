package net.moesli.scan7.game

import net.moesli.scan7.scoring.scoreRow

/**
 * Immutable state of one game. All mutations return a new state and throw
 * [IllegalArgumentException]/[IllegalStateException] for actions that are not allowed.
 *
 * Invariants: [rounds] is never empty, numbered 1..n; every round except the last is closed;
 * a closed round has no open results. The game is over only when the last round is closed and
 * someone's total reached [target], or it was ended early from the menu ([endedEarly], see
 * [isOver]); [finishedAt] follows that automatically.
 *
 * Mutations that record a time take `now` (epoch millis); this class never reads the clock.
 */
data class GameState(
    val id: Long = 0,
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
) {
    init {
        require(seats.isNotEmpty()) { "A game needs at least one player" }
        require(seats.size <= MAX_PLAYERS) { "At most $MAX_PLAYERS players" }
        require(seats.map { it.playerId }.toSet().size == seats.size) { "Duplicate player" }
        require(target > 0) { "Target must be positive" }
        require(rounds.isNotEmpty()) { "A game has at least one round" }
        require(rounds.mapIndexed { i, r -> r.number == i + 1 }.all { it }) { "Rounds must be numbered 1..n" }
        require(rounds.dropLast(1).all { it.isClosed }) { "Only the last round may be open" }
    }

    // ---- Queries -------------------------------------------------------------------------

    val playerIds: List<Long> get() = seats.map { it.playerId }

    val currentRound: RoundState get() = rounds.last()

    fun round(number: Int): RoundState {
        require(number in 1..rounds.size) { "No round $number" }
        return rounds[number - 1]
    }

    fun seatIndex(playerId: Long): Int = playerIds.indexOf(playerId).also {
        require(it >= 0) { "Player $playerId is not in this game" }
    }

    fun result(playerId: Long, round: Int = currentRound.number): PlayerResult {
        seatIndex(playerId)
        return round(round).result(playerId)
    }

    fun status(playerId: Long, round: Int = currentRound.number): ResultStatus = result(playerId, round).status

    /**
     * Totals over all rounds (incl. points already scored in the running round) plus all
     * [adjustments], in seating order. The single source of truth for every total in the app.
     */
    val totals: Map<Long, Int> get() = totalsThrough(rounds.size)

    /** Totals after rounds 1..[round] plus the adjustments made up to that round, in seating order. */
    fun totalsThrough(round: Int): Map<Long, Int> {
        require(round in 0..rounds.size) { "No round $round" }
        val counted = rounds.take(round)
        val adjusted = adjustments.filter { it.round <= round }
        return playerIds.associateWith { p -> counted.sumOf { it.points(p) } + adjusted.filter { it.playerId == p }.sumOf { it.delta } }
    }

    fun total(playerId: Long): Int = totals.getValue(playerId)

    /** [playerId]'s adjustments, oldest first. */
    fun adjustmentsOf(playerId: Long): List<ScoreAdjustment> = adjustments.filter { it.playerId == playerId }

    /** Sum of [playerId]'s adjustments (0 without any). */
    fun adjustmentTotal(playerId: Long): Int = adjustmentsOf(playerId).sumOf { it.delta }

    /** Player(s) with the highest total, in seating order; empty while nobody has points. */
    val leaders: List<Long>
        get() {
            val t = totals
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
        val open = openPlayers().toSet()
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
    val isOver: Boolean get() = currentRound.isClosed && (endedEarly || totals.values.any { it >= target })

    /** Highest total once [isOver] (several on an exact tie = shared win), in seating order. */
    val winners: List<Long>
        get() {
            if (!isOver) return emptyList()
            val t = totals
            val best = t.values.max()
            return t.filterValues { it == best }.keys.toList()
        }

    /**
     * Rank of each player by points in [round] (1 = best; equal points share a rank, the next rank
     * is skipped: 1, 1, 3). A bust ranks below a scored 0; open players rank last.
     */
    fun roundRanks(round: Int = currentRound.number): Map<Long, Int> {
        val r = round(round)
        return rank { p ->
            val res = r.result(p)
            val statusKey = when (res.status) {
                ResultStatus.SCORED -> 2
                ResultStatus.BUST -> 1
                ResultStatus.OPEN -> 0
            }
            res.points.toLong() * 4 + statusKey
        }
    }

    /** Standings after rounds 1..[round] by total (same ranking scheme as [roundRanks]). */
    fun standings(round: Int = rounds.size): Map<Long, Int> {
        val t = totalsThrough(round)
        return rank { t.getValue(it).toLong() }
    }

    private fun rank(key: (Long) -> Long): Map<Long, Int> {
        val keys = playerIds.associateWith(key)
        return playerIds.associateWith { p -> 1 + keys.values.count { it > keys.getValue(p) } }
    }

    // ---- Scoring -------------------------------------------------------------------------

    /**
     * Scores [playerId] from confirmed cards (scan review OK, manual save, edit). Duplicate
     * numbers make it a bust (0 points). [round] defaults to the running round; a closed round
     * can be edited and totals / game over are recomputed.
     */
    fun scoreCards(
        playerId: Long,
        cards: List<ConfirmedCard>,
        source: ResultSource,
        now: Long,
        round: Int = currentRound.number,
    ): GameState {
        require(source == ResultSource.SCAN || source == ResultSource.MANUAL) { "Cards come from a scan or manual entry" }
        val score = scoreRow(cards.countedCards())
        val result = PlayerResult(
            status = if (score.bust) ResultStatus.BUST else ResultStatus.SCORED,
            points = score.total,
            source = source,
            cards = cards,
            updatedAt = now,
        )
        return setResult(playerId, round, result, now)
    }

    /** Sets [points] directly without cards (manual correction). */
    fun scorePoints(playerId: Long, points: Int, now: Long, round: Int = currentRound.number): GameState {
        require(points >= 0) { "Points can't be negative" }
        val result = PlayerResult(ResultStatus.SCORED, points, ResultSource.MANUAL, emptyList(), now)
        return setResult(playerId, round, result, now)
    }

    /** One-tap bust (in-card Bust button, player sheet, or editing a closed round). */
    fun bust(playerId: Long, now: Long, round: Int = currentRound.number): GameState =
        setResult(playerId, round, PlayerResult(ResultStatus.BUST, 0, ResultSource.BUST_TAP, emptyList(), now), now)

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
        seatIndex(playerId)
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
        seatIndex(adjustment.playerId)
        check(!abandoned) { "Game was abandoned" }
        require(adjustment.round in 1..rounds.size) { "No round ${adjustment.round}" }
        val restored = adjustment.copy(id = 0)
        return copy(adjustments = (adjustments + restored).sortedBy { it.createdAt }).settled(now)
    }

    // ---- Round lifecycle -----------------------------------------------------------------

    /** Closes the running round: every open player becomes BUST (source CLOSE_ROUND). */
    fun closeRound(now: Long): GameState {
        val r = currentRound
        check(!r.isClosed) { "Round ${r.number} is already closed" }
        check(!abandoned) { "Game was abandoned" }
        val results = playerIds.associateWith { p ->
            val res = r.result(p)
            if (res.isOpen) PlayerResult(ResultStatus.BUST, 0, ResultSource.CLOSE_ROUND, emptyList(), now) else res
        }
        return copy(rounds = rounds.replace(r.number, r.copy(results = results, closedAt = now))).settled(now)
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

    companion object {
        /**
         * New game with [players] in seating order (avatar colors may repeat).
         * Positions default to an even ring; with [previousPositions] (from the last game with the
         * same player set) those spots are reused, handed out clockwise in the new seating order.
         */
        fun newGame(
            players: List<Player>,
            target: Int = DEFAULT_TARGET,
            startedAt: Long = 0,
            previousPositions: Map<Long, TablePosition>? = null,
            id: Long = 0,
        ): GameState {
            require(players.isNotEmpty()) { "A game needs at least one player" }
            require(players.size <= MAX_PLAYERS) { "At most $MAX_PLAYERS players" }
            val ids = players.map { it.id }
            val reuse = previousPositions != null && previousPositions.keys == ids.toSet()
            val slots = if (reuse) previousPositions.values.toList().sortedClockwise() else defaultPositions(players.size)
            return GameState(
                id = id,
                target = target,
                seats = ids.mapIndexed { i, p -> Seat(p, slots[i]) },
                rounds = listOf(RoundState(1, ids.associateWith { PlayerResult.OPEN })),
                startedAt = startedAt,
            )
        }
    }
}

private fun List<RoundState>.replace(number: Int, round: RoundState): List<RoundState> =
    mapIndexed { i, r -> if (i == number - 1) round else r }
