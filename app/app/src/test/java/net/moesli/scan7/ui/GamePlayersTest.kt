package net.moesli.scan7.ui

import net.moesli.scan7.game.GameState
import net.moesli.scan7.game.Player
import net.moesli.scan7.game.ResultStatus
import net.moesli.scan7.ui.components.Confirmations
import net.moesli.scan7.ui.components.PlayerStatus
import net.moesli.scan7.ui.gameover.standingsOf
import net.moesli.scan7.ui.history.ScoreCell
import net.moesli.scan7.ui.history.scoreTable
import net.moesli.scan7.ui.history.describe
import net.moesli.scan7.ui.history.text
import net.moesli.scan7.ui.home.toPastGameUi
import net.moesli.scan7.ui.newgame.gamePickList
import net.moesli.scan7.ui.newgame.pickedLineup
import net.moesli.scan7.ui.newgame.removeConfirm
import net.moesli.scan7.ui.text.de
import net.moesli.scan7.ui.text.en
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Round menu → Players: picker logic, remove confirmation, scoreboard / history / standings of a changed lineup. */
class GamePlayersTest {
    private val players = listOf(Player(1, "Anna"), Player(2, "Ben"), Player(3, "Cleo"), Player(4, "Dan"))
    private val byId = players.associateBy { it.id }
    private val start = GameState.newGame(players.take(3), startedAt = 1)

    private fun GameState.playRound(vararg points: Int): GameState {
        var s = this
        playerIds.zip(points.toList()).forEach { (p, pts) -> s = s.scorePoints(p, pts, 1) }
        return s.closeAndStartNext(2)
    }

    @Test
    fun removeConfirm_saysWhatStaysAndWhatIsDropped() {
        val s = start.playRound(10, 20, 30).scorePoints(1, 46, now = 3).bust(2, now = 3)
        val anna = removeConfirm(s, 1, "Anna")!!
        assertEquals("Remove Anna from this game?", anna.title.en)
        assertEquals("Their points from past rounds stay in the scoreboard. Their +46 in this round is dropped.", anna.body!!.en)
        assertEquals("Anna aus dem Spiel nehmen?", anna.title.de)
        assertEquals("Remove", anna.confirmLabel.en)
        assertTrue(anna.destructive)
        assertTrue(removeConfirm(s, 2, "Ben")!!.body!!.en.endsWith("Their BUST in this round is dropped."))
        assertEquals("Die Punkte aus früheren Runden bleiben in der Punktetabelle.", removeConfirm(s, 3, "Cleo")!!.body!!.de)
        // Round 1, still open: nothing to say.
        assertNull(Confirmations.removePlayer("Anna", PlayerStatus.Open, playedBefore = false).body)
        // Not seated (only ticked in this step): no confirmation.
        assertNull(removeConfirm(s, 4, "Dan"))
    }

    @Test
    fun pickedLineup_staysInSeatingOrder_newOnesAfter() {
        val s = start.reorderSeats(listOf(3, 1, 2))
        assertEquals(listOf(3L, 2L, 4L), pickedLineup(s, listOf(4, 2, 3)))
    }

    @Test
    fun gamePickList_keepsADeletedSeatedPlayer() {
        val stored = players.filter { it.id != 2L }
        assertEquals(listOf(1L, 3L, 4L, 2L), gamePickList(stored, start, byId).map { it.id })
    }

    @Test
    fun scoreTable_marksJoinedAndLeftPlayers() {
        val s = start.playRound(10, 20, 30).removePlayer(2, now = 3).addPlayer(4, now = 3).playRound(1, 2, 3)
        val table = scoreTable(s)
        assertEquals(listOf(1L, 3L, 4L, 2L), table.playerIds)
        assertEquals(mapOf(4L to 2), table.joined)
        assertEquals(mapOf(2L to 2), table.left)
        // Round 1: Dan wasn't there; round 2: Ben had left.
        assertEquals(ScoreCell(ResultStatus.OPEN, played = false), table.rows[0].cells[2])
        assertFalse(table.rows[1].cells[3].played)
        assertEquals("–", table.rows[0].cells[2].text().en)
        assertEquals("Round 1, Dan: not playing", table.rows[0].cells[2].describe(1, "Dan").en)
        assertEquals(listOf(11, 32, 3, 20), table.totals)
    }

    @Test
    fun historyAndStandings_showEveryoneWhoPlayed() {
        val s = start.playRound(10, 150, 30).removePlayer(2, now = 3).endGame(4)
        val past = s.toPastGameUi(byId)
        assertEquals(listOf("Anna", "Cleo", "Ben"), past.players.map { it.name })
        assertEquals(listOf("Cleo"), past.winners.map { it.name })
        assertEquals(30, past.points)
        val standings = standingsOf(s)
        assertEquals(listOf(3L, 1L, 2L), standings.map { it.playerId })
        assertTrue(standings.last().left)
        assertEquals(150, standings.last().total)
        assertFalse(standings.last().isWinner)
    }
}
