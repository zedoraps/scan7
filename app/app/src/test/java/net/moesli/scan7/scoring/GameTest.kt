package net.moesli.scan7.scoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameTest {
    private val start = Game(listOf("Anna", "Ben", "Cleo"))

    @Test
    fun newGame() {
        assertEquals(listOf(0, 0, 0), start.totals)
        assertFalse(start.isOver)
        assertEquals(emptyList<String>(), start.winners)
    }

    @Test
    fun runningTotals() {
        val g = start.addRound(listOf(30, 0, 12)).addRound(listOf(10, 45, 12))
        assertEquals(2, g.rounds.size)
        assertEquals(listOf(40, 45, 24), g.totals)
        assertEquals(listOf(0, 0, 0), start.totals)
    }

    @Test
    fun notOverBelow200() {
        val g = start.addRound(listOf(199, 150, 0))
        assertFalse(g.isOver)
        assertEquals(emptyList<String>(), g.winners)
    }

    @Test
    fun overAtExactly200() {
        val g = start.addRound(listOf(150, 100, 0)).addRound(listOf(50, 20, 0))
        assertTrue(g.isOver)
        assertEquals(listOf("Anna"), g.winners)
    }

    @Test
    fun winnerIsHighestTotal_notFirstToReach200() {
        val g = start.addRound(listOf(190, 180, 0)).addRound(listOf(15, 40, 0))
        assertTrue(g.isOver)
        assertEquals(listOf(205, 220, 0), g.totals)
        assertEquals(listOf("Ben"), g.winners)
    }

    @Test
    fun tie_returnsAllWinners() {
        val g = start.addRound(listOf(210, 100, 210))
        assertEquals(listOf("Anna", "Cleo"), g.winners)
    }

    @Test(expected = IllegalStateException::class)
    fun addRoundAfterGameOver_throws() {
        start.addRound(listOf(200, 0, 0)).addRound(listOf(0, 0, 0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun wrongScoreCount_throws() {
        start.addRound(listOf(1, 2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun noPlayers_throws() {
        Game(emptyList())
    }
}
