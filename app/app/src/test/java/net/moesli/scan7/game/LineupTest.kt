package net.moesli.scan7.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Players joining / leaving a running game (round menu → Players). */
class LineupTest {
    private val anna = Player(1, "Anna")
    private val ben = Player(2, "Ben")
    private val cleo = Player(3, "Cleo")

    private val start = GameState.newGame(listOf(anna, ben, cleo), startedAt = 100)

    /** Scores the seated players by points (in seating order) and starts the next round. */
    private fun GameState.playRound(vararg points: Int, now: Long = 1): GameState {
        var s = this
        playerIds.zip(points.toList()).forEach { (p, pts) -> s = s.scorePoints(p, pts, now) }
        return s.closeAndStartNext(now)
    }

    // ---- Joining ----

    @Test
    fun addPlayer_joinsTheRunningRoundOpen_earlierRoundsShowNothing() {
        val r3 = start.playRound(10, 20, 30).playRound(5, 5, 5)
        val s = r3.addPlayer(4, now = 50)

        assertEquals(listOf(1L, 2L, 3L, 4L), s.playerIds)
        assertEquals(3, s.joinedRound(4))
        assertTrue(s.result(4).isOpen)
        assertEquals(listOf(1L, 2L, 3L, 4L), s.openPlayers())
        assertEquals(0, s.total(4))
        // Rounds 1 and 2 had no player 4: "–", no result to edit.
        assertFalse(s.playedIn(4, 1))
        assertFalse(s.playedIn(4, 2))
        assertTrue(s.playedIn(4, 3))
        assertEquals(1, s.joinedRound(1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun addPlayer_can_tEditARoundBeforeTheyJoined() {
        start.playRound(10, 20, 30).addPlayer(4, now = 50).scorePoints(4, 12, now = 60, round = 1)
    }

    @Test
    fun addPlayer_joinedPlayerCountsForCloseRound_andTheNextRound() {
        val s = start.playRound(10, 20, 30).addPlayer(4, now = 50)
            .scorePoints(1, 1, 60).scorePoints(2, 2, 60).scorePoints(3, 3, 60)
        assertFalse(s.isRoundComplete)
        assertEquals(listOf(4L), s.openPlayers())
        val closed = s.closeRound(70)
        assertEquals(ResultStatus.BUST, closed.result(4, round = 2).status)
        val next = closed.nextRound()
        assertTrue(next.result(4).isOpen)
        assertEquals(listOf(1L, 2L, 3L, 4L), next.openPlayers())
    }

    @Test
    fun addPlayer_onTheDefaultRing_everyoneMovesToTheRingOfTheNewSize() {
        val s = start.addPlayer(4, now = 1)
        assertEquals(defaultPositions(4), s.seats.map { it.position })
        assertEquals(listOf(1L, 2L, 3L, 4L), s.playerIds)
    }

    @Test
    fun addPlayer_onACustomTable_othersKeepTheirSpots_newOneAfterTheLastSeat() {
        val spots = listOf(TablePosition(0.5f, 0f), TablePosition(1f, 0.5f), TablePosition(0.5f, 1f))
        val g = GameState.newGame(listOf(anna, ben, cleo), positions = spots)
        val s = g.addPlayer(4, now = 1)
        assertEquals(listOf(1L, 2L, 3L, 4L), s.playerIds)
        assertEquals(spots, s.seats.take(3).map { it.position })
        // Between the last seat (6 o'clock) and the first (12 o'clock): the left side.
        val p = s.seats[3].position
        assertTrue(p.x < 0.1f)
        assertEquals(0.5f, p.y, 0.01f)
    }

    @Test(expected = IllegalStateException::class)
    fun addPlayer_atMostTenPlayers() {
        val ten = GameState.newGame((1L..10L).map { Player(it, "P$it") })
        ten.addPlayer(11, now = 1)
    }

    // ---- Leaving ----

    @Test
    fun removePlayer_leavesTheRunningRound_pastResultsStay() {
        val r2 = start.playRound(10, 20, 30).scorePoints(2, 46, now = 40)
        val s = r2.removePlayer(2, now = 50)

        assertEquals(listOf(1L, 3L), s.playerIds)
        assertEquals(listOf(1L, 3L, 2L), s.everyoneIds)
        assertEquals(DepartedPlayer(2, joinedRound = 1, leftRound = 2), s.departed.single())
        assertEquals(2, s.leftRound(2))
        // The +46 of the running round is dropped; round 1 stays.
        assertEquals(20, s.total(2))
        assertTrue(s.playedIn(2, 1))
        assertFalse(s.playedIn(2, 2))
        assertEquals(listOf(1L, 3L), s.openPlayers())
        assertEquals(0, s.doneCount())
    }

    @Test
    fun removePlayer_noLongerCountsForAllDone_andScanNext() {
        val s = start.scorePoints(1, 10, now = 1).scorePoints(3, 5, now = 1)
        assertEquals(2L, s.nextOpenPlayer())
        val removed = s.removePlayer(2, now = 2)
        assertTrue(removed.isRoundComplete)
        assertNull(removed.nextOpenPlayer())
        // Closing doesn't bust the player who left.
        val closed = removed.closeRound(3)
        assertEquals(setOf(1L, 3L), closed.round(1).results.keys)
    }

    @Test
    fun removePlayer_scanNextSkipsTheSeatInBothDirections() {
        val four = GameState.newGame(listOf(anna, ben, cleo, Player(4, "Dan"))).removePlayer(2, now = 1)
        assertEquals(3L, four.nextOpenPlayer(after = 1))
        assertEquals(1L, four.nextOpenPlayer(after = 4))
        assertEquals(4L, four.nextOpenPlayer(after = 1, direction = ScanDirection.COUNTER_CLOCKWISE))
    }

    @Test
    fun removePlayer_whoJustJoined_goesWithoutATrace() {
        val s = start.playRound(10, 20, 30).addPlayer(4, now = 1).removePlayer(4, now = 2)
        assertEquals(listOf(1L, 2L, 3L), s.everyoneIds)
        assertEquals(emptyList<DepartedPlayer>(), s.departed)
        assertEquals(emptyMap<Long, Int>(), s.joinedRounds)
    }

    @Test
    fun removePlayer_withAnAdjustment_staysAsDeparted() {
        val s = start.adjustTotal(3, 5, "house", now = 1).removePlayer(3, now = 2)
        assertEquals(listOf(3L), s.departed.map { it.playerId })
        assertEquals(5, s.total(3))
    }

    @Test(expected = IllegalArgumentException::class)
    fun removePlayer_keepsAtLeastTwoPlayers() {
        GameState.newGame(listOf(anna, ben)).removePlayer(2, now = 1)
    }

    @Test(expected = IllegalStateException::class)
    fun lineup_onlyWhileARoundIsRunning() {
        start.scorePoints(1, 10, 1).scorePoints(2, 10, 1).scorePoints(3, 10, 1).closeRound(2).removePlayer(1, now = 3)
    }

    @Test
    fun rejoin_comesBackWithTheOldResults_andAGap() {
        val s = start.playRound(10, 20, 30) // round 1
            .removePlayer(2, now = 1).playRound(1, 1) // round 2 without Ben
            .addPlayer(2, now = 2)
        assertEquals(emptyList<DepartedPlayer>(), s.departed)
        assertEquals(3, s.joinedRound(2))
        assertTrue(s.playedIn(2, 1))
        assertFalse(s.playedIn(2, 2))
        assertTrue(s.playedIn(2, 3))
        assertEquals(20, s.total(2))
    }

    // ---- Game over / winner ----

    @Test
    fun gameOver_aRemovedLeaderCanTWin() {
        // Ben leads with 150, then leaves; Anna reaches the target.
        val s = start.playRound(60, 150, 10).removePlayer(2, now = 1)
        assertEquals(listOf(1L), s.leaders)
        val over = s.scorePoints(1, 150, 2).scorePoints(3, 0, 2).closeAndStartNext(3)
        assertTrue(over.isOver)
        assertEquals(listOf(1L), over.winners)
        // Ben's total is still there (scoreboard, history).
        assertEquals(150, over.total(2))
    }

    @Test
    fun gameOver_aDepartedPlayerOverTheTargetDoesNotEndTheGame() {
        val s = start.copy(target = 100).scorePoints(1, 10, 1).scorePoints(2, 99, 1).scorePoints(3, 5, 1)
            .closeAndStartNext(2)
            .adjustTotal(2, 5, null, now = 3) // Ben: 104 mid-round
            .removePlayer(2, now = 4)
        val next = s.scorePoints(1, 1, 5).scorePoints(3, 1, 5).closeAndStartNext(6)
        assertFalse(next.isOver)
        assertEquals(3, next.currentRound.number)
    }

    @Test
    fun endGame_winnersAreSeatedPlayersOnly() {
        val s = start.playRound(10, 90, 30).removePlayer(2, now = 1).endGame(2)
        assertTrue(s.isOver)
        assertEquals(listOf(3L), s.winners)
    }

    @Test
    fun withLineup_swapsPlayersInOneStep() {
        val s = GameState.newGame(listOf(anna, ben)).playRound(5, 6)
        val lineup = s.seatsFor(listOf(1L, 3L))
        val changed = s.withLineup(lineup, now = 9)
        assertEquals(listOf(1L, 3L), changed.playerIds)
        assertEquals(listOf(2L), changed.departed.map { it.playerId })
        assertEquals(2, changed.joinedRound(3))
    }

    @Test
    fun seatsFor_keepsClockwiseOrderEqualToSeatingOrder() {
        val spots = listOf(TablePosition(0.5f, 0f), TablePosition(0.9f, 0.3f), TablePosition(0.6f, 1f))
        val g = GameState.newGame(listOf(anna, ben, cleo), positions = spots)
        val seats = g.seatsFor(listOf(1L, 3L, 4L, 5L))
        assertEquals(listOf(1L, 3L, 4L, 5L), seats.map { it.playerId })
        val angles = seats.map { clockwiseAngle(it.position) }
        assertEquals(angles.sorted(), angles)
    }
}
