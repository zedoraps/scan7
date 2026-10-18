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
        listOf(Player(1, "Anna"), Player(2, "Ben"), Player(3, "Cleo")),
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
        assertEquals(next(2), next(1).afterSave(s, AfterScan.NEXT))
    }

    @Test
    fun bustTap_skipsPlayersAlreadyDone() {
        val s = start.scan(2, now = 1).bust(1, now = 2)
        assertEquals(next(3), next(1).afterSave(s, AfterScan.NEXT))
    }

    @Test
    fun undoOnTheNextCamera_reopensWithoutMoving_andTheChainComesBack() {
        // Anna busted by tap → Ben's camera; Undo there reopens Anna, Ben stays on screen.
        var s = start.bust(1, now = 1)
        val ben = next(1).afterSave(s, AfterScan.NEXT)!!
        s = s.undoBust(1, now = 2)
        assertEquals(ResultStatus.OPEN, s.result(1).status)
        // Ben and Cleo scanned: the chain wraps around to Anna again, then ends.
        s = s.scan(ben.playerId, now = 3)
        val cleo = ben.afterSave(s, AfterScan.NEXT)!!
        assertEquals(3L, cleo.playerId)
        s = s.scan(3, now = 4)
        val anna = cleo.afterSave(s, AfterScan.NEXT)!!
        assertEquals(1L, anna.playerId)
        s = s.scan(1, now = 5)
        assertNull(anna.afterSave(s, AfterScan.NEXT))
        assertTrue(s.isRoundComplete)
    }

    @Test
    fun bustTapOnTheLastOpenPlayer_returnsToTheRound() {
        val s = start.scan(1, now = 1).scan(2, now = 2).bust(3, now = 3)
        assertNull(next(3).afterSave(s, AfterScan.NEXT))
        assertTrue(s.isRoundComplete)
        // Undo from the round screen's toast reopens the player.
        assertEquals(listOf(3L), s.undoBust(3, now = 4).openPlayers())
    }

    @Test
    fun bustTapFromACardsScan_followsTheChoice() {
        val s = start.bust(1, now = 1)
        val card = Route.Scan(game, 1, ScanStart.CAMERA)
        assertEquals(next(2), card.afterSave(s, AfterScan.NEXT))
        assertNull(card.afterSave(s, AfterScan.TABLE))
        assertNull(next(1).afterSave(s, AfterScan.TABLE))
    }

    // ---- Split OK: "OK · Next: Ben" / "OK · Back to table" ------------------------------------

    @Test
    fun okNext_continuesWithTheNextOpenPlayer_fromEveryStart() {
        val s = start.scan(1, now = 1)
        for (from in listOf(ScanStart.CAMERA, ScanStart.NEXT, ScanStart.MANUAL)) {
            assertEquals(from.name, next(2), Route.Scan(game, 1, from).afterSave(s, AfterScan.NEXT))
        }
    }

    @Test
    fun okBackToTable_returnsToTheRound_fromEveryStart() {
        val s = start.scan(1, now = 1)
        for (from in ScanStart.entries) {
            assertNull(from.name, Route.Scan(game, 1, from).afterSave(s, AfterScan.TABLE))
        }
    }

    @Test
    fun okNext_withNobodyElseOpen_returnsToTheRound() {
        val s = start.scan(2, now = 1).scan(3, now = 2)
        // Before the save there is no next player (the button just says "OK") ...
        assertNull(Route.Scan(game, 1, ScanStart.CAMERA).nextPlayer(s))
        // ... and after it the route goes back to the table.
        assertNull(Route.Scan(game, 1, ScanStart.CAMERA).afterSave(s.scan(1, now = 3), AfterScan.NEXT))
    }

    @Test
    fun nextPlayer_isShownBeforeTheSave_andMatchesTheRouteAfterIt() {
        val card = Route.Scan(game, 2, ScanStart.CAMERA)
        assertEquals(3L, card.nextPlayer(start))
        assertEquals(next(3), card.afterSave(start.scan(2, now = 1), AfterScan.NEXT))
    }

    @Test
    fun edits_alwaysReturn() {
        val s = start.scan(1, now = 1)
        assertNull(Route.Scan(game, 1, ScanStart.EDIT).nextPlayer(s))
        assertNull(Route.Scan(game, 1, ScanStart.EDIT).afterSave(s, AfterScan.NEXT))
        // A closed round edited from the scoreboard.
        assertNull(Route.Scan(game, 1, ScanStart.CAMERA, round = 1).afterSave(s, AfterScan.NEXT))
    }

    @Test
    fun afterScanSetting_defaultsToNext() {
        assertEquals(AfterScan.NEXT, AfterScan.fromSetting(null))
        assertEquals(AfterScan.NEXT, AfterScan.fromSetting("garbage"))
        assertEquals(AfterScan.TABLE, AfterScan.fromSetting(AfterScan.TABLE.name))
        assertEquals(AfterScan.NEXT, AfterScan.fromSetting(AfterScan.NEXT.name))
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
            route = route.afterSave(s, AfterScan.NEXT)
        }
        assertEquals(listOf(1L, 2L, 3L), visited)
        assertTrue(s.playerIds.all { s.result(it).status == ResultStatus.BUST })
    }
}
