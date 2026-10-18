package net.moesli.scan7.game

import net.moesli.scan7.scoring.Card
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameStateTest {
    private val anna = Player(1, "Anna", 0)
    private val ben = Player(2, "Ben", 1)
    private val cleo = Player(3, "Cleo", 2)
    private val dan = Player(4, "Dan", 3)

    private val start = GameState.newGame(listOf(anna, ben, cleo), startedAt = 100)

    private fun cards(vararg c: Card) = c.map { ConfirmedCard(it) }

    /** Scores every listed player by points and closes the round. */
    private fun GameState.playRound(vararg points: Int, now: Long = 1): GameState {
        var s = this
        playerIds.zip(points.toList()).forEach { (p, pts) -> s = s.scorePoints(p, pts, now) }
        return s.closeRound(now)
    }

    // ---- New game ----

    @Test
    fun newGame_roundOneAllOpen() {
        assertEquals(1, start.currentRound.number)
        assertEquals(listOf(1L, 2L, 3L), start.playerIds)
        assertEquals(listOf(1L, 2L, 3L), start.openPlayers())
        assertEquals(mapOf(1L to 0, 2L to 0, 3L to 0), start.totals)
        assertEquals(DEFAULT_TARGET, start.target)
        assertFalse(start.isOver)
        assertEquals(emptyList<Long>(), start.leaders)
        assertNull(start.finishedAt)
    }

    @Test(expected = IllegalArgumentException::class)
    fun newGame_duplicateColors_throws() {
        GameState.newGame(listOf(anna, ben.copy(colorIndex = 0)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun newGame_moreThanTenPlayers_throws() {
        GameState.newGame((1..11).map { Player(it.toLong(), "P$it", it % 10) })
    }

    @Test(expected = IllegalArgumentException::class)
    fun newGame_noPlayers_throws() {
        GameState.newGame(emptyList())
    }

    @Test
    fun newGame_tenPlayersAllowed() {
        val g = GameState.newGame((0 until 10).map { Player(it.toLong(), "P$it", it) })
        assertEquals(10, g.seats.size)
    }

    @Test
    fun defaultPositions_ringClockwiseFromTop() {
        // Prototype ellipse 0.485 × 0.5 (normalized to the card's free range).
        val p = defaultPositions(4)
        assertEquals(0.5f, p[0].x, 1e-4f)
        assertEquals(0f, p[0].y, 1e-4f) // top
        assertEquals(0.985f, p[1].x, 1e-4f) // right (clockwise)
        assertEquals(0.5f, p[1].y, 1e-4f)
        assertEquals(1f, p[2].y, 1e-4f) // bottom
        assertEquals(0.015f, p[3].x, 1e-4f) // left
        assertEquals(DEFAULT_ELLIPSE_RX, p[1].x - 0.5f, 1e-4f)
        assertEquals(DEFAULT_ELLIPSE_RY, p[2].y - 0.5f, 1e-4f)
        assertEquals(p, p.sortedClockwise())
        assertEquals(start.seats.map { it.position }, defaultPositions(3))
    }

    @Test
    fun newGame_reusesPreviousPositionsInNewSeatingOrder() {
        val prev = mapOf(1L to TablePosition(0.2f, 0.2f), 2L to TablePosition(0.8f, 0.3f), 3L to TablePosition(0.5f, 0.9f))
        // Clockwise from top: (0.8,0.3) right-top, (0.5,0.9) bottom, (0.2,0.2) left-top.
        val same = GameState.newGame(listOf(ben, cleo, anna), previousPositions = prev)
        assertEquals(prev, same.seats.associate { it.playerId to it.position })

        val reordered = GameState.newGame(listOf(anna, ben, cleo), previousPositions = prev)
        assertEquals(TablePosition(0.8f, 0.3f), reordered.seats[0].position)
        assertEquals(TablePosition(0.5f, 0.9f), reordered.seats[1].position)
        assertEquals(TablePosition(0.2f, 0.2f), reordered.seats[2].position)
    }

    @Test
    fun newGame_previousPositionsForOtherPlayerSet_ignored() {
        val prev = mapOf(1L to TablePosition(0.2f, 0.2f), 2L to TablePosition(0.8f, 0.3f))
        val g = GameState.newGame(listOf(anna, ben, cleo), previousPositions = prev)
        assertEquals(defaultPositions(3), g.seats.map { it.position })
    }

    // ---- Scoring ----

    @Test
    fun scoreCards_usesScoreRow() {
        val g = start.scoreCards(1, cards(Card.N7, Card.N12, Card.N3, Card.TIMES_2, Card.PLUS_4, Card.FREEZE), ResultSource.SCAN, now = 5)
        val r = g.result(1)
        assertEquals(ResultStatus.SCORED, r.status)
        assertEquals(44 + 4, r.points)
        assertEquals(ResultSource.SCAN, r.source)
        assertEquals(5L, r.updatedAt)
        assertTrue(r.cards.last().ignored) // freeze kept, ignored
        assertEquals(48, g.total(1))
        assertEquals(listOf(1L), g.leaders)
    }

    @Test
    fun scoreCards_duplicateNumbers_bust() {
        val g = start.scoreCards(2, cards(Card.N7, Card.N7, Card.N5), ResultSource.SCAN, now = 1)
        assertEquals(ResultStatus.BUST, g.status(2))
        assertEquals(0, g.result(2).points)
        assertEquals(listOf(7), g.result(2).breakdown!!.duplicatedNumbers)
    }

    @Test
    fun scoreCards_ignoredCardDoesNotCount() {
        val g = start.scoreCards(1, listOf(ConfirmedCard(Card.N9), ConfirmedCard(Card.N9, ignored = true)), ResultSource.MANUAL, 1)
        assertEquals(ResultStatus.SCORED, g.status(1))
        assertEquals(9, g.result(1).points)
    }

    @Test
    fun scorePoints_manual() {
        val g = start.scorePoints(3, 17, now = 2)
        assertEquals(ResultStatus.SCORED, g.status(3))
        assertEquals(ResultSource.MANUAL, g.result(3).source)
        assertEquals(17, g.total(3))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownPlayer_throws() {
        start.scorePoints(99, 1, 0)
    }

    @Test
    fun bustAndUndo() {
        val busted = start.bust(2, now = 3)
        assertEquals(ResultStatus.BUST, busted.status(2))
        assertEquals(ResultSource.BUST_TAP, busted.result(2).source)
        val undone = busted.undoBust(2, now = 4)
        assertEquals(ResultStatus.OPEN, undone.status(2))
        assertEquals(listOf(1L, 2L, 3L), undone.openPlayers())
    }

    @Test(expected = IllegalStateException::class)
    fun undoBust_ofScanBust_throws() {
        start.scoreCards(2, cards(Card.N7, Card.N7), ResultSource.SCAN, 1).undoBust(2, 2)
    }

    @Test
    fun resetToOpen() {
        val g = start.scorePoints(1, 30, 1).resetToOpen(1, 2)
        assertEquals(ResultStatus.OPEN, g.status(1))
        assertEquals(0, g.total(1))
    }

    @Test
    fun rescoreReplacesResult() {
        val g = start.scorePoints(1, 30, 1).scoreCards(1, cards(Card.N5), ResultSource.SCAN, 2)
        assertEquals(5, g.total(1))
    }

    // ---- Next open player ----

    @Test
    fun nextOpenPlayer_seatingOrderWithWraparound() {
        val four = GameState.newGame(listOf(anna, ben, cleo, dan))
        assertEquals(1L, four.nextOpenPlayer())
        assertEquals(2L, four.nextOpenPlayer(after = 1))
        val g = four.scorePoints(1, 5, 1).scorePoints(2, 5, 1) // Cleo, Dan open
        assertEquals(3L, g.nextOpenPlayer())
        assertEquals(3L, g.nextOpenPlayer(after = 2))
        assertEquals(4L, g.nextOpenPlayer(after = 3))
        assertEquals(3L, g.nextOpenPlayer(after = 4)) // wraps around past Anna and Ben
        val onlyDan = g.scorePoints(3, 5, 1)
        assertEquals(4L, onlyDan.nextOpenPlayer(after = 4)) // sole open player
        assertNull(onlyDan.scorePoints(4, 5, 1).nextOpenPlayer(after = 4))
    }

    @Test
    fun nextOpenPlayer_followsReorderedSeats() {
        val g = start.reorderSeats(listOf(3, 1, 2)).scorePoints(3, 1, 1)
        assertEquals(1L, g.nextOpenPlayer())
        assertEquals(1L, g.nextOpenPlayer(after = 2))
    }

    // ---- Close round / game over ----

    @Test
    fun closeRound_openPlayersBecomeBust() {
        val g = start.scorePoints(1, 20, 1).closeRound(now = 9)
        assertTrue(g.currentRound.isClosed)
        assertEquals(9L, g.currentRound.closedAt)
        assertEquals(ResultStatus.SCORED, g.status(1))
        for (p in listOf(2L, 3L)) {
            assertEquals(ResultStatus.BUST, g.status(p))
            assertEquals(ResultSource.CLOSE_ROUND, g.result(p).source)
            assertEquals(0, g.result(p).points)
        }
        assertEquals(emptyList<Long>(), g.openPlayers())
        assertNull(g.nextOpenPlayer())
    }

    @Test(expected = IllegalStateException::class)
    fun closeRoundTwice_throws() {
        start.closeRound(1).closeRound(2)
    }

    @Test(expected = IllegalStateException::class)
    fun scoringOpenAfterClose_resetThrows() {
        start.closeRound(1).resetToOpen(1, 2)
    }

    @Test
    fun runningTotals_overRounds() {
        val g = start.playRound(30, 0, 12).nextRound().playRound(10, 45, 12)
        assertEquals(2, g.rounds.size)
        assertEquals(mapOf(1L to 40, 2L to 45, 3L to 24), g.totals)
        assertEquals(mapOf(1L to 30, 2L to 0, 3L to 12), g.totalsThrough(1))
        assertEquals(listOf(2L), g.leaders)
    }

    @Test
    fun reachingTargetMidRound_doesNotEndGame() {
        val g = start.scorePoints(1, 250, 1)
        assertFalse(g.isOver)
        assertNull(g.finishedAt)
        assertEquals(emptyList<Long>(), g.winners)
        assertEquals(3L, g.scorePoints(2, 1, 1).nextOpenPlayer()) // round continues
    }

    @Test
    fun notOverBelowTarget() {
        val g = start.playRound(199, 150, 0)
        assertFalse(g.isOver)
        g.nextRound() // allowed
    }

    @Test
    fun overAtExactlyTarget_afterClose() {
        val g = start.playRound(150, 100, 0).nextRound().scorePoints(1, 50, 1)
        assertFalse(g.isOver)
        val closed = g.closeRound(now = 77)
        assertTrue(closed.isOver)
        assertEquals(listOf(1L), closed.winners)
        assertEquals(77L, closed.finishedAt)
    }

    @Test
    fun winnerIsHighestTotal_notFirstToReachTarget() {
        val g = start.playRound(190, 180, 0).nextRound().playRound(15, 40, 0)
        assertTrue(g.isOver)
        assertEquals(listOf(2L), g.winners)
    }

    @Test
    fun tie_sharedWin() {
        val g = start.playRound(210, 100, 210)
        assertEquals(listOf(1L, 3L), g.winners)
    }

    @Test
    fun customTarget() {
        val g = GameState.newGame(listOf(anna, ben), target = 50).playRound(50, 10)
        assertTrue(g.isOver)
    }

    @Test(expected = IllegalStateException::class)
    fun nextRoundAfterGameOver_throws() {
        start.playRound(200, 0, 0).nextRound()
    }

    @Test(expected = IllegalStateException::class)
    fun nextRoundBeforeClose_throws() {
        start.nextRound()
    }

    @Test
    fun nextRound_allOpen() {
        val g = start.playRound(1, 2, 3).nextRound()
        assertEquals(2, g.currentRound.number)
        assertEquals(listOf(1L, 2L, 3L), g.openPlayers())
        assertFalse(g.currentRound.isClosed)
    }

    // ---- Reopen / edit closed rounds ----

    @Test
    fun reopenRound_recomputesAndRestoresClosedBusts() {
        val over = start.scorePoints(1, 210, 1).scorePoints(2, 20, 1).bust(3, 1).closeRound(5)
        assertTrue(over.isOver)
        val reopened = over.reopenRound(now = 6)
        assertFalse(reopened.isOver)
        assertNull(reopened.finishedAt)
        assertFalse(reopened.currentRound.isClosed)
        assertEquals(ResultStatus.BUST, reopened.status(3)) // tap bust stays

        val withAutoBust = start.scorePoints(1, 10, 1).closeRound(2).reopenRound(3)
        assertEquals(listOf(2L, 3L), withAutoBust.openPlayers()) // close-round busts are open again
    }

    @Test
    fun editClosedRound_recomputesTotalsAndGameOver() {
        val g = start.playRound(100, 50, 0).nextRound().playRound(90, 60, 0) // 190, 110: not over
        assertFalse(g.isOver)
        val edited = g.scorePoints(1, 110, now = 9, round = 1) // round 1: 100 -> 110 => 200
        assertEquals(200, edited.total(1))
        assertTrue(edited.isOver)
        assertEquals(9L, edited.finishedAt)
        val back = edited.bust(1, now = 10, round = 2) // 110 + 0
        assertFalse(back.isOver)
        assertNull(back.finishedAt)
        back.nextRound()
    }

    @Test
    fun editOldRound_whileNextRunning_neverEndsMidRound() {
        val g = start.playRound(150, 0, 0).nextRound().scorePoints(1, 10, now = 1, round = 1).scorePoints(1, 210, 2, round = 1)
        assertFalse(g.isOver)
        assertTrue(g.closeRound(3).isOver)
    }

    @Test(expected = IllegalStateException::class)
    fun reopenOpenRound_throws() {
        start.reopenRound(1)
    }

    // ---- Ranks ----

    @Test
    fun roundRanks_tiesShareRank_bustBelowZero() {
        val four = GameState.newGame(listOf(anna, ben, cleo, dan))
            .scorePoints(1, 30, 1).scorePoints(2, 30, 1).scorePoints(3, 0, 1).closeRound(2)
        assertEquals(mapOf(1L to 1, 2L to 1, 3L to 3, 4L to 4), four.roundRanks(1))
    }

    @Test
    fun standings_byTotal() {
        val g = start.playRound(30, 50, 30).nextRound().playRound(30, 0, 30)
        assertEquals(mapOf(1L to 2, 2L to 1, 3L to 2), g.standings(1))
        assertEquals(mapOf(1L to 1, 2L to 3, 3L to 1), g.standings())
    }

    // ---- Seating ----

    @Test
    fun reorderSeats_keepsPositionSlotsClockwise() {
        val g = start.reorderSeats(listOf(3, 1, 2))
        assertEquals(listOf(3L, 1L, 2L), g.playerIds)
        assertEquals(defaultPositions(3), g.seats.map { it.position })
        assertEquals(g.playerIds, g.seats.sortedBy { clockwiseAngle(it.position) }.map { it.playerId })
    }

    @Test
    fun moveSeat_shiftsOthers() {
        val four = GameState.newGame(listOf(anna, ben, cleo, dan))
        assertEquals(listOf(2L, 3L, 1L, 4L), four.moveSeat(0, 2).playerIds)
        assertEquals(listOf(4L, 1L, 2L, 3L), four.moveSeat(3, 0).playerIds)
    }

    @Test
    fun swapSeats_swapsOnlyTheTwoPlayers() {
        val four = GameState.newGame(listOf(anna, ben, cleo, dan))
        assertEquals(listOf(3L, 2L, 1L, 4L), four.swapSeats(0, 2).playerIds)
        assertEquals(listOf(1L, 4L, 3L, 2L), four.swapSeats(3, 1).playerIds)
        assertEquals(listOf(2L, 1L, 3L, 4L), four.swapSeats(0, 1).playerIds) // neighbours: same as a move
    }

    @Test
    fun swapSeats_keepsPositionSlotsClockwise() {
        val four = GameState.newGame(listOf(anna, ben, cleo, dan))
        val g = four.swapSeats(0, 3)
        assertEquals(four.seats.map { it.position }, g.seats.map { it.position })
        assertEquals(g.playerIds, g.seats.sortedBy { clockwiseAngle(it.position) }.map { it.playerId })
    }

    @Test
    fun swapSeats_sameSeat_unchanged() {
        assertEquals(start, start.swapSeats(1, 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun swapSeats_outOfRange_throws() {
        start.swapSeats(0, 3)
    }

    @Test(expected = IllegalArgumentException::class)
    fun reorderSeats_notAPermutation_throws() {
        start.reorderSeats(listOf(1, 2, 2))
    }

    @Test
    fun movePosition_rederivesSeatingOrder() {
        // Default: Anna top, Ben lower right, Cleo lower left. Move Anna to the bottom-left-ish
        // (between Cleo and the top going clockwise) -> order becomes Ben, Cleo, Anna.
        val g = start.movePosition(1, TablePosition(0.1f, 0.3f))
        assertEquals(listOf(2L, 3L, 1L), g.playerIds)
        assertEquals(TablePosition(0.1f, 0.3f), g.seats.last().position)
    }

    @Test
    fun movePosition_clamped() {
        val g = start.movePosition(2, TablePosition(1.5f, -0.2f))
        val p = g.seats.first { it.playerId == 2L }.position
        assertEquals(TablePosition(1f, 0f), p)
    }

    @Test
    fun seatingSurvivesResults() {
        val g = start.scorePoints(1, 10, 1).reorderSeats(listOf(2, 3, 1))
        assertEquals(10, g.total(1))
        assertEquals(listOf(2L, 3L, 1L), g.totals.keys.toList())
    }

    // ---- Misc ----

    @Test
    fun abandon_setsFinishedWithoutWinner() {
        val g = start.scorePoints(1, 10, 1).abandon(now = 8)
        assertTrue(g.abandoned)
        assertEquals(8L, g.finishedAt)
        assertEquals(emptyList<Long>(), g.winners)
    }

    @Test
    fun viewModeDefault() {
        assertEquals(ViewMode.TABLE, ViewMode.defaultFor(6))
        assertEquals(ViewMode.LIST, ViewMode.defaultFor(7))
    }

    @Test
    fun firstFreeColor_picksLowestUnused() {
        assertEquals(0, firstFreeColor(emptyList()))
        assertEquals(2, firstFreeColor(listOf(0, 1, 3)))
        assertNull(firstFreeColor((0 until 10).toList()))
    }
}
