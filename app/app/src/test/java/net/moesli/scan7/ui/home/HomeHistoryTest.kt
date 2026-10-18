package net.moesli.scan7.ui.home

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import net.moesli.scan7.data.GameSnapshot
import net.moesli.scan7.game.GameState
import net.moesli.scan7.game.Player
import net.moesli.scan7.ui.history.subtitle
import net.moesli.scan7.ui.history.toScoreboardUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeHistoryTest {
    private val anna = Player(1, "Anna")
    private val ben = Player(2, "Ben")
    private val cleo = Player(3, "Cleo")
    private val players = listOf(anna, ben, cleo).associateBy { it.id }
    private val start = GameState.newGame(listOf(anna, ben, cleo), startedAt = 100, id = 7)

    private fun GameState.playRound(vararg points: Int, now: Long = 1): GameState {
        var s = this
        playerIds.zip(points.toList()).forEach { (p, pts) -> s = s.scorePoints(p, pts, now) }
        return s.closeRound(now)
    }

    @Test
    fun won_winnerPointsAndRounds() {
        val g = start.playRound(120, 50, 10).nextRound().playRound(90, 40, 0, now = 500)
        val ui = g.toPastGameUi(players)
        assertEquals(7L, ui.gameId)
        assertEquals(PastOutcome.WON, ui.outcome)
        assertEquals(listOf("Anna"), ui.winners.map { it.name })
        assertEquals(210, ui.points)
        assertEquals(2, ui.rounds)
        assertEquals(500L, ui.finishedAt)
        assertEquals(listOf("Anna", "Ben", "Cleo"), ui.players.map { it.name })
        assertEquals("Anna won", ui.headline)
        assertEquals("2 rounds", ui.detail)
    }

    @Test
    fun sharedWin_bothNamed() {
        val g = start.playRound(200, 200, 5)
        val ui = g.toPastGameUi(players)
        assertEquals("Anna and Ben won", ui.headline)
        assertEquals(200, ui.points)
        assertEquals("1 round", ui.detail)
    }

    @Test
    fun endedEarly_leaderWins() {
        val g = start.playRound(30, 50, 10).nextRound().endGame(now = 900)
        val ui = g.toPastGameUi(players)
        assertEquals(PastOutcome.ENDED_EARLY, ui.outcome)
        assertEquals("Ben won", ui.headline)
        assertEquals(50, ui.points)
        assertEquals(2, ui.rounds)
        assertEquals("2 rounds · ended early", ui.detail)
    }

    @Test
    fun abandoned_leaderLed_onlyClosedRoundsCount() {
        val g = start.playRound(30, 50, 10).nextRound().scorePoints(1, 99, 2).abandon(now = 300)
        val ui = g.toPastGameUi(players)
        assertEquals(PastOutcome.ABANDONED, ui.outcome)
        assertEquals("Anna led", ui.headline) // 30 + 99 in the unfinished round
        assertEquals(129, ui.points)
        assertEquals(1, ui.rounds)
        assertEquals("1 round · not finished", ui.detail)
    }

    @Test
    fun abandoned_withoutPoints() {
        val ui = start.abandon(now = 300).toPastGameUi(players)
        assertEquals("Not finished", ui.headline)
        assertEquals(emptyList<String>(), ui.winners.map { it.name })
        assertEquals(0, ui.rounds)
    }

    @Test
    fun scoreboard_abandonedIsReadOnlyAndNotRunning() {
        val g = start.playRound(30, 50, 10).nextRound().abandon(now = 300)
        val ui = GameSnapshot(g, players, null).toScoreboardUi()
        assertEquals("Not finished · 1 round · to 200", ui.subtitle())
        assertFalse(ui.table.rows.any { it.isRunning })
    }

    @Test
    fun scoreboard_subtitles() {
        val over = start.playRound(200, 0, 0)
        assertEquals("Final · 1 round · to 200", GameSnapshot(over, players, null).toScoreboardUi().subtitle())
        val running = start.playRound(10, 0, 0).nextRound()
        assertEquals("Round 2 · to 200 · tap to edit", GameSnapshot(running, players, null).toScoreboardUi().subtitle())
    }

    @Test
    fun historyDate_relative() {
        val zone: ZoneId = ZoneOffset.UTC
        fun at(y: Int, mo: Int, d: Int, h: Int = 20, mi: Int = 15) = LocalDateTime.of(y, mo, d, h, mi).toInstant(ZoneOffset.UTC).toEpochMilli()
        val now = at(2026, 10, 18, 22, 0)
        assertEquals("Today, 20:15", historyDate(at(2026, 10, 18), now, zone, Locale.UK))
        assertEquals("Yesterday, 20:15", historyDate(at(2026, 10, 17), now, zone, Locale.UK))
        assertEquals("Sun 11 Oct", historyDate(at(2026, 10, 11), now, zone, Locale.UK))
        assertEquals("11 Oct 2025", historyDate(at(2025, 10, 11), now, zone, Locale.UK))
    }
}
