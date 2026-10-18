package net.moesli.scan7.ui

import net.moesli.scan7.game.Avatar
import net.moesli.scan7.game.AvatarCatalog
import net.moesli.scan7.game.ConfirmedCard
import net.moesli.scan7.game.GameState
import net.moesli.scan7.game.Player
import net.moesli.scan7.game.ResultStatus
import net.moesli.scan7.scoring.Card
import net.moesli.scan7.scoring.scoreRow
import net.moesli.scan7.ui.gameover.joinNames
import net.moesli.scan7.ui.gameover.ordinal
import net.moesli.scan7.ui.gameover.standingsOf
import net.moesli.scan7.ui.gameover.winnerHeadline
import net.moesli.scan7.ui.history.ScoreCell
import net.moesli.scan7.ui.history.scoreTable
import net.moesli.scan7.ui.history.text
import net.moesli.scan7.ui.newgame.MAX_TARGET
import net.moesli.scan7.ui.newgame.MIN_TARGET
import net.moesli.scan7.ui.newgame.canContinue
import net.moesli.scan7.ui.newgame.dragTargetIndex
import net.moesli.scan7.ui.newgame.moved
import net.moesli.scan7.ui.newgame.seatingFor
import net.moesli.scan7.ui.newgame.steppedTarget
import net.moesli.scan7.ui.newgame.toggleSelection
import net.moesli.scan7.ui.players.colorName
import net.moesli.scan7.ui.players.colorOwners
import net.moesli.scan7.ui.players.suggestedAvatar
import net.moesli.scan7.ui.sheet.breakdownLine
import net.moesli.scan7.ui.sheet.chipLabel
import net.moesli.scan7.ui.sheet.duplicatedCards
import net.moesli.scan7.ui.components.unscoredWarning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SetupLogicTest {
    private val anna = Player(1, "Anna")
    private val ben = Player(2, "Ben")
    private val david = Player(3, "David")
    private val mia = Player(4, "Mia")

    // ---- avatar colors --------------------------------------------------------------------

    private fun withColor(p: Player, color: String) = p.copy(avatar = Avatar("happy", "plain", color))

    @Test
    fun colorOwnersGroupsByColorWithoutTheEditedPlayer() {
        val a = withColor(anna, "amber")
        val b = withColor(ben, "violet")
        val d = withColor(david, "amber")
        val owners = colorOwners(listOf(a, b, d), exceptId = 1)
        assertEquals(listOf(d), owners["amber"])
        assertEquals(listOf(b), owners["violet"])
        assertFalse(owners.containsKey("lime"))
        assertTrue(colorOwners(emptyList(), exceptId = null).isEmpty())
    }

    @Test
    fun suggestedAvatarTakesTheFirstFreeColorAndARandomFaceAndPattern() {
        assertEquals("amber", suggestedAvatar(emptyList()).color)
        assertEquals("lime", suggestedAvatar(listOf(withColor(anna, "amber"), withColor(ben, "violet"))).color)
        // Every color taken: falls back to the first one.
        val all = AvatarCatalog.colors.mapIndexed { i, c -> Player(i.toLong(), "P$i", Avatar("happy", "plain", c.id)) }
        assertEquals("amber", suggestedAvatar(all).color)
        val a = suggestedAvatar(emptyList(), Random(3))
        assertTrue(a.face in AvatarCatalog.faces)
        assertTrue(a.pattern in AvatarCatalog.patterns)
        assertEquals(a, suggestedAvatar(emptyList(), Random(3)))
        assertEquals("Amber", colorName("amber"))
        assertEquals("Chartreuse", colorName("chartreuse"))
        assertEquals("Amber", colorName("gone"))
    }

    // ---- selection / seating / target ----------------------------------------------------

    @Test
    fun toggleSelectionKeepsPickOrderAndMax() {
        var s = toggleSelection(emptyList(), 3)
        s = toggleSelection(s, 1)
        assertEquals(listOf(3L, 1L), s)
        assertEquals(listOf(1L), toggleSelection(s, 3))
        assertEquals(listOf(3L, 1L), toggleSelection(s, 7, max = 2))
    }

    @Test
    fun seatingKeepsPreviousOrderAndAppendsNewPicks() {
        assertEquals(listOf(2L, 1L, 4L), seatingFor(selected = listOf(1, 2, 4), previous = listOf(2, 3, 1)))
        assertEquals(listOf(5L, 6L), seatingFor(listOf(5, 6), emptyList()))
    }

    @Test
    fun movedShiftsTheOthers() {
        val l = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), l.moved(0, 2))
        assertEquals(listOf("d", "a", "b", "c"), l.moved(3, 0))
        assertEquals(l, l.moved(1, 1))
    }

    @Test
    fun dragTargetIndexRoundsAndClamps() {
        assertEquals(1, dragTargetIndex(1, 30f, 80f, 4))
        assertEquals(2, dragTargetIndex(1, 41f, 80f, 4))
        assertEquals(0, dragTargetIndex(1, -50f, 80f, 4))
        assertEquals(3, dragTargetIndex(1, 1000f, 80f, 4))
        assertEquals(0, dragTargetIndex(2, -1000f, 80f, 4))
        assertEquals(2, dragTargetIndex(2, 10f, 0f, 4))
    }

    @Test
    fun targetStepperSnapsAndClamps() {
        assertEquals(210, steppedTarget(200, 1))
        assertEquals(190, steppedTarget(200, -1))
        assertEquals(210, steppedTarget(205, 1))
        assertEquals(200, steppedTarget(205, -1))
        assertEquals(MIN_TARGET, steppedTarget(MIN_TARGET, -1))
        assertEquals(MAX_TARGET, steppedTarget(MAX_TARGET, 1))
    }

    @Test
    fun canContinueNeedsTwoToTenPlayers() {
        assertFalse(canContinue(1))
        assertTrue(canContinue(2))
        assertTrue(canContinue(10))
        assertFalse(canContinue(11))
    }

    // ---- texts -----------------------------------------------------------------------------

    @Test
    fun namesAndHeadlines() {
        assertEquals("", joinNames(emptyList()))
        assertEquals("Anna", joinNames(listOf("Anna")))
        assertEquals("Anna and David", joinNames(listOf("Anna", "David")))
        assertEquals("Anna, Ben and David", joinNames(listOf("Anna", "Ben", "David")))
        assertEquals("Ben wins", winnerHeadline(listOf("Ben")))
        assertEquals("Ben and Mia share the win", winnerHeadline(listOf("Ben", "Mia")))
    }

    @Test
    fun unscoredWarningText() {
        assertEquals("Anna and David haven't been scored. They'll be marked BUST.", unscoredWarning(listOf("Anna", "David")))
        assertEquals("Anna hasn't been scored. They'll be marked BUST.", unscoredWarning(listOf("Anna")))
        assertEquals("", unscoredWarning(emptyList()))
        // Everyone still open: asks explicitly (likely an accident).
        assertEquals("Nobody has been scored yet – mark everyone BUST?", unscoredWarning(listOf("Anna", "David"), nobodyScored = true))
        assertEquals("", unscoredWarning(emptyList(), nobodyScored = true))
    }

    @Test
    fun ordinals() {
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "10th", "11th", "12th", "13th", "21st", "22nd", "101st", "111th"),
            listOf(1, 2, 3, 4, 10, 11, 12, 13, 21, 22, 101, 111).map(::ordinal))
    }

    @Test
    fun chipsAndBreakdown() {
        assertEquals("7", chipLabel(Card.N7))
        assertEquals("+4", chipLabel(Card.PLUS_4))
        assertEquals("×2", chipLabel(Card.TIMES_2))
        assertEquals("Freeze", chipLabel(Card.FREEZE))

        val cards = listOf(Card.N7, Card.N12, Card.N3, Card.TIMES_2, Card.PLUS_4)
        assertEquals("7 + 12 + 3 = 22 · ×2 = 44 · +4", breakdownLine(listOf(7, 12, 3), scoreRow(cards)))
        val bust = listOf(Card.N7, Card.N5, Card.N7)
        assertEquals("BUST · two 7s", breakdownLine(listOf(7, 5, 7), scoreRow(bust)))
        assertEquals(setOf(Card.N7), duplicatedCards(bust.map { ConfirmedCard(it) }))
        assertEquals("5", breakdownLine(listOf(5), scoreRow(listOf(Card.N5))))
    }

    // ---- standings / scoreboard ------------------------------------------------------------

    private fun finishedGame(): GameState {
        var g = GameState.newGame(listOf(anna, ben, mia), target = 100)
        g = g.scorePoints(1, 120, now = 1).scorePoints(2, 120, now = 1).bust(4, now = 1).closeRound(now = 2)
        return g
    }

    @Test
    fun standingsShareRanksAndMarkWinners() {
        val s = standingsOf(finishedGame())
        assertEquals(listOf(1L, 2L, 4L), s.map { it.playerId })
        assertEquals(listOf(1, 1, 3), s.map { it.rank })
        assertEquals(listOf(true, true, false), s.map { it.isWinner })
        assertEquals(listOf(120, 120, 0), s.map { it.total })
    }

    @Test
    fun standingsSortByTotal() {
        var g = GameState.newGame(listOf(anna, ben, mia))
        g = g.scorePoints(1, 10, 1).scorePoints(2, 30, 1).scorePoints(4, 20, 1)
        assertEquals(listOf(2L, 4L, 1L), standingsOf(g).map { it.playerId })
        assertTrue(standingsOf(g).none { it.isWinner })
    }

    @Test
    fun scoreTableHasRoundsCellsAndTotals() {
        var g = finishedGame().copy(target = 1000).nextRound()
        g = g.scorePoints(2, 15, 3)
        val t = scoreTable(g)
        assertEquals(listOf(1L, 2L, 4L), t.playerIds)
        assertEquals(2, t.rows.size)
        assertFalse(t.rows[0].isRunning)
        assertTrue(t.rows[1].isRunning)
        assertEquals(ScoreCell(ResultStatus.BUST), t.rows[0].cells[2])
        assertEquals(ScoreCell(ResultStatus.SCORED, 15), t.rows[1].cells[1])
        assertEquals(ResultStatus.OPEN, t.rows[1].cells[0].status)
        assertEquals(listOf(120, 135, 0), t.totals)
        assertEquals(setOf(2L), t.leaders)
        assertEquals("BUST", t.rows[0].cells[2].text())
        assertEquals("–", t.rows[1].cells[0].text())
        assertEquals("15", t.rows[1].cells[1].text())
    }
}
