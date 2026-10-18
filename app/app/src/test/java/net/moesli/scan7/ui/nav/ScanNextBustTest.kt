package net.moesli.scan7.ui.nav

import net.moesli.scan7.game.ConfirmedCard
import net.moesli.scan7.game.GameState
import net.moesli.scan7.game.Player
import net.moesli.scan7.game.ResultSource
import net.moesli.scan7.game.ResultStatus
import net.moesli.scan7.scoring.Card
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Scan next" sequencing with the camera's Bust tap: the route after a save ([afterSave]) on the
 * state the repository returns (GameState.bust / undoBust / scoreCards, as ScanRoute calls them).
 */
class ScanNextBustTest {
    private val game = 7L
    private val start = GameState.newGame(
        listOf(Player(1, "Anna", 0), Player(2, "Ben", 1), Player(3, "Cleo", 2)),
        startedAt = 100,
    )

    private fun next(id: Long) = Route.Scan(game, id, ScanStart.NEXT)

    private fun GameState.scan(id: Long, now: Long) =
        scoreCards(id, listOf(ConfirmedCard(Card.N5)), ResultSource.SCAN, now)

    @Test
    fun bustTap_continuesWithTheNextOpenPlayer() {
        val s = start.bust(1, now = 1)
        assertEquals(ResultStatus.BUST, s.result(1).status)
        assertEquals(ResultSource.BUST_TAP, s.result(1).source)
        assertTrue(s.result(1).cards.isEmpty())
        assertEquals(next(2), next(1).afterSave(s))
    }

    @Test
    fun bustTap_skipsPlayersAlreadyDone() {
        val s = start.scan(2, now = 1).bust(1, now = 2)
        assertEquals(next(3), next(1).afterSave(s))
    }

    @Test
    fun undoOnTheNextCamera_reopensWithoutMoving_andTheChainComesBack() {
        // Anna busted by tap → Ben's camera; Undo there reopens Anna, Ben stays on screen.
        var s = start.bust(1, now = 1)
        val ben = next(1).afterSave(s)!!
        s = s.undoBust(1, now = 2)
        assertEquals(ResultStatus.OPEN, s.result(1).status)
        // Ben and Cleo scanned: the chain wraps around to Anna again, then ends.
        s = s.scan(ben.playerId, now = 3)
        val cleo = ben.afterSave(s)!!
        assertEquals(3L, cleo.playerId)
        s = s.scan(3, now = 4)
        val anna = cleo.afterSave(s)!!
        assertEquals(1L, anna.playerId)
        s = s.scan(1, now = 5)
        assertNull(anna.afterSave(s))
        assertTrue(s.isRoundComplete)
    }

    @Test
    fun bustTapOnTheLastOpenPlayer_returnsToTheRound() {
        val s = start.scan(1, now = 1).scan(2, now = 2).bust(3, now = 3)
        assertNull(next(3).afterSave(s))
        assertTrue(s.isRoundComplete)
        // Undo from the round screen's toast reopens the player.
        assertEquals(listOf(3L), s.undoBust(3, now = 4).openPlayers())
    }

    @Test
    fun bustTapInNormalMode_returnsToTheRound() {
        val s = start.bust(1, now = 1)
        assertNull(Route.Scan(game, 1, ScanStart.CAMERA).afterSave(s))
    }

    @Test
    fun bustTapsInARow_bustEveryoneAndEnd() {
        var route: Route.Scan? = next(1)
        var s = start
        var now = 1L
        val visited = mutableListOf<Long>()
        while (route != null) {
            visited += route.playerId
            s = s.bust(route.playerId, now++)
            route = route.afterSave(s)
        }
        assertEquals(listOf(1L, 2L, 3L), visited)
        assertTrue(s.playerIds.all { s.result(it).status == ResultStatus.BUST })
    }
}
