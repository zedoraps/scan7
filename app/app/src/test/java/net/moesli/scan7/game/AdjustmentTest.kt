package net.moesli.scan7.game

import net.moesli.scan7.ui.gameover.standingsOf
import net.moesli.scan7.ui.history.scoreTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AdjustmentTest {
    private val anna = Player(1, "Anna", 0)
    private val ben = Player(2, "Ben", 1)
    private val cleo = Player(3, "Cleo", 2)

    private val start = GameState.newGame(listOf(anna, ben, cleo), target = 100, startedAt = 100)

    private fun GameState.playRound(vararg points: Int, now: Long = 1): GameState {
        var s = this
        playerIds.zip(points.toList()).forEach { (p, pts) -> s = s.scorePoints(p, pts, now) }
        return s.closeRound(now)
    }

    /** Gives every adjustment without an id one, as the repository does when storing. */
    private fun GameState.stored(): GameState {
        var next = (adjustments.maxOfOrNull { it.id } ?: 0) + 1
        return copy(adjustments = adjustments.map { if (it.id == 0L) it.copy(id = next++) else it })
    }

    @Test
    fun totals_includeAdjustments() {
        val s = start.playRound(30, 20, 10).nextRound()
            .adjustTotal(1, 10, "penalty", now = 5)
            .adjustTotal(2, -12, null, now = 6)
        assertEquals(mapOf(1L to 40, 2L to 8, 3L to 10), s.totals)
        assertEquals(10, s.adjustmentTotal(1))
        assertEquals(-12, s.adjustmentTotal(2))
        assertEquals(0, s.adjustmentTotal(3))
        val a = s.adjustmentsOf(1).single()
        assertEquals(2, a.round)
        assertEquals("penalty", a.note)
        assertEquals(5L, a.createdAt)
    }

    @Test
    fun note_isTrimmedAndBlankBecomesNull() {
        val s = start.adjustTotal(1, 3, "  house rule ", 1).adjustTotal(1, 4, "   ", 2)
        assertEquals(listOf("house rule", null), s.adjustmentsOf(1).map { it.note })
    }

    @Test
    fun totalsThrough_countsAdjustmentsUpToThatRound() {
        val s = start.playRound(30, 20, 10).adjustTotal(3, 5, null, 2).nextRound().adjustTotal(1, 7, null, 3)
        assertEquals(mapOf(1L to 30, 2L to 20, 3L to 15), s.totalsThrough(1))
        assertEquals(mapOf(1L to 37, 2L to 20, 3L to 15), s.totalsThrough(2))
        assertEquals(mapOf(1L to 0, 2L to 0, 3L to 0), s.totalsThrough(0))
    }

    @Test
    fun leaders_andTies_followAdjustedTotals() {
        val s = start.playRound(30, 20, 10).nextRound()
        assertEquals(listOf(1L), s.leaders)
        val tie = s.adjustTotal(2, 10, null, 2)
        assertEquals(listOf(1L, 2L), tie.leaders)
        assertEquals(mapOf(1L to 1, 2L to 1, 3L to 3), tie.standings())
        val overtaken = tie.adjustTotal(1, -1, null, 3)
        assertEquals(listOf(2L), overtaken.leaders)
        // Everyone at or below 0: nobody leads.
        val negative = start.adjustTotal(1, -5, null, 1)
        assertEquals(emptyList<Long>(), negative.leaders)
        assertEquals(-5, negative.total(1))
    }

    @Test
    fun remove_andRestore() {
        val s = start.playRound(30, 20, 10).nextRound()
            .adjustTotal(1, 10, "penalty", 5)
            .adjustTotal(1, -12, null, 6)
            .stored()
        assertEquals(28, s.total(1))
        val first = s.adjustmentsOf(1).first()
        val removed = s.removeAdjustment(first.id, 7)
        assertEquals(18, removed.total(1))
        assertEquals(listOf(-12), removed.adjustmentsOf(1).map { it.delta })

        val restored = removed.restoreAdjustment(first, 8)
        assertEquals(28, restored.total(1))
        // Back in its old place (by creation time), as a new entry to store.
        assertEquals(listOf(10, -12), restored.adjustmentsOf(1).map { it.delta })
        assertEquals(0L, restored.adjustmentsOf(1).first().id)
        assertEquals(2, restored.adjustmentsOf(1).first().round)

        assertThrows(IllegalArgumentException::class.java) { s.removeAdjustment(999, 9) }
    }

    @Test
    fun invalidAdjustments_areRejected() {
        assertThrows(IllegalArgumentException::class.java) { start.adjustTotal(1, 0, null, 1) }
        assertThrows(IllegalArgumentException::class.java) { start.adjustTotal(1, MAX_ADJUSTMENT + 1, null, 1) }
        assertThrows(IllegalArgumentException::class.java) { start.adjustTotal(99, 5, null, 1) }
        assertThrows(IllegalStateException::class.java) { start.abandon(2).adjustTotal(1, 5, null, 3) }
    }

    @Test
    fun midRound_adjustmentOverTarget_doesNotEndTheGame_untilTheRoundCloses() {
        val s = start.playRound(90, 20, 10).nextRound()
            .scorePoints(1, 5, 2)
            .adjustTotal(2, 85, "house rule", 3)
        assertEquals(105, s.total(2))
        assertFalse(s.isOver)
        assertNull(s.finishedAt)
        // Only closing the round ends the game: Ben wins with the adjustment.
        val closed = s.scorePoints(2, 0, 4).scorePoints(3, 0, 4).closeRound(5)
        assertTrue(closed.isOver)
        assertEquals(listOf(2L), closed.winners)
        assertEquals(5L, closed.finishedAt)
        assertEquals(listOf(2L, 1L, 3L), standingsOf(closed).map { it.playerId })
    }

    @Test
    fun endOfRound_adjustment_reChecksGameOver_andRemovalUndoesIt() {
        val closed = start.playRound(90, 20, 10)
        assertFalse(closed.isOver)
        // On the end-of-round view the round is closed: like editing a result there, totals are re-checked.
        val over = closed.adjustTotal(1, 10, null, 6).stored()
        assertTrue(over.isOver)
        assertEquals(listOf(1L), over.winners)
        assertNotNull(over.finishedAt)
        val back = over.removeAdjustment(over.adjustments.single().id, 7)
        assertFalse(back.isOver)
        assertNull(back.finishedAt)
        assertEquals(2, back.nextRound().currentRound.number)
    }

    @Test
    fun negativeAdjustment_canPullTheLeaderBelowTheTarget() {
        val closed = start.playRound(100, 20, 10)
        assertTrue(closed.isOver)
        val pulled = closed.adjustTotal(1, -5, "penalty", 6)
        assertFalse(pulled.isOver)
        assertNull(pulled.finishedAt)
    }

    @Test
    fun scoreTable_totalsIncludeAdjustments_andMarkAdjustedPlayers() {
        val s = start.playRound(30, 20, 10).nextRound().adjustTotal(2, -12, null, 3)
        val table = scoreTable(s)
        assertEquals(listOf(30, 8, 10), table.totals)
        assertEquals(setOf(2L), table.adjusted)
        assertEquals(setOf(1L), table.leaders)
        // No separate adjustments row: just the rounds.
        assertEquals(2, table.rows.size)
    }
}
