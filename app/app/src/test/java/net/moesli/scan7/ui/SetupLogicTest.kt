package net.moesli.scan7.ui

import net.moesli.scan7.ui.text.de
import net.moesli.scan7.ui.text.en
import net.moesli.scan7.game.Avatar
import net.moesli.scan7.game.AvatarCatalog
import net.moesli.scan7.game.ConfirmedCard
import net.moesli.scan7.game.GameState
import net.moesli.scan7.game.Player
import net.moesli.scan7.game.ResultStatus
import net.moesli.scan7.scoring.Card
import net.moesli.scan7.scoring.scoreRow
import net.moesli.scan7.ui.text.UiText
import net.moesli.scan7.ui.text.avatarColorName
import net.moesli.scan7.ui.gameover.ordinal
import net.moesli.scan7.ui.gameover.standingsOf
import net.moesli.scan7.ui.gameover.winnerHeadline
import net.moesli.scan7.ui.history.ScoreCell
import net.moesli.scan7.ui.history.scoreTable
import net.moesli.scan7.ui.history.text
import net.moesli.scan7.ui.newgame.MAX_TARGET
import net.moesli.scan7.ui.newgame.MIN_TARGET
import net.moesli.scan7.ui.newgame.canContinue
import net.moesli.scan7.ui.newgame.seatingFor
import net.moesli.scan7.ui.newgame.steppedTarget
import net.moesli.scan7.ui.newgame.toggleSelection
import net.moesli.scan7.ui.players.colorOwners
import net.moesli.scan7.ui.players.suggestedAvatar
import net.moesli.scan7.ui.sheet.breakdownLine
import net.moesli.scan7.ui.text.chipText
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
        assertEquals("Amber", avatarColorName("amber").en)
        assertEquals("Chartreuse", avatarColorName("chartreuse").en)
        assertEquals("Amber", avatarColorName("gone").en)
        assertEquals("Gelbgrün", avatarColorName("chartreuse").de)
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
        assertEquals("", UiText.Names(emptyList()).en)
        assertEquals("Anna", UiText.Names(listOf("Anna")).en)
        assertEquals("Anna and David", UiText.Names(listOf("Anna", "David")).en)
        assertEquals("Anna, Ben and David", UiText.Names(listOf("Anna", "Ben", "David")).en)
        assertEquals("Anna, Ben und David", UiText.Names(listOf("Anna", "Ben", "David")).de)
        assertEquals("Ben wins", winnerHeadline(listOf("Ben")).en)
        assertEquals("Ben and Mia share the win", winnerHeadline(listOf("Ben", "Mia")).en)
        assertEquals("Ben und Mia teilen sich den Sieg", winnerHeadline(listOf("Ben", "Mia")).de)
    }

    @Test
    fun unscoredWarningText() {
        assertEquals("Anna and David haven't been scored. They'll be marked BUST.", unscoredWarning(listOf("Anna", "David"))!!.en)
        assertEquals("Anna hasn't been scored. They'll be marked BUST.", unscoredWarning(listOf("Anna"))!!.en)
        assertEquals("Anna und David sind noch nicht gewertet und gelten dann als RAUS.", unscoredWarning(listOf("Anna", "David"))!!.de)
        assertEquals(null, unscoredWarning(emptyList()))
        // Everyone still open: asks explicitly (likely an accident).
        assertEquals("Nobody has been scored yet – mark everyone BUST?", unscoredWarning(listOf("Anna", "David"), nobodyScored = true)!!.en)
        assertEquals(null, unscoredWarning(emptyList(), nobodyScored = true))
    }

    @Test
    fun ordinals() {
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "10th", "11th", "12th", "13th", "21st", "22nd", "101st", "111th"),
            listOf(1, 2, 3, 4, 10, 11, 12, 13, 21, 22, 101, 111).map { ordinal(it).en })
        assertEquals(listOf("1.", "2.", "11.", "21."), listOf(1, 2, 11, 21).map { ordinal(it).de })
    }

    @Test
    fun chipsAndBreakdown() {
        assertEquals("7", Card.N7.chipText().en)
        assertEquals("+4", Card.PLUS_4.chipText().en)
        assertEquals("×2", Card.TIMES_2.chipText().en)
        assertEquals("Freeze", Card.FREEZE.chipText().en)
        assertEquals("Freeze", Card.FREEZE.chipText().de)

        val cards = listOf(Card.N7, Card.N12, Card.N3, Card.TIMES_2, Card.PLUS_4)
        assertEquals("7 + 12 + 3 = 22 · ×2 = 44 · +4", breakdownLine(listOf(7, 12, 3), scoreRow(cards)).en)
        val bust = listOf(Card.N7, Card.N5, Card.N7)
        assertEquals("BUST · two 7s", breakdownLine(listOf(7, 5, 7), scoreRow(bust)).en)
        assertEquals("RAUS · zwei 7er", breakdownLine(listOf(7, 5, 7), scoreRow(bust)).de)
        assertEquals(setOf(Card.N7), duplicatedCards(bust.map { ConfirmedCard(it) }))
        assertEquals("5", breakdownLine(listOf(5), scoreRow(listOf(Card.N5))).en)
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
        assertEquals("BUST", t.rows[0].cells[2].text().en)
        assertEquals("RAUS", t.rows[0].cells[2].text().de)
        assertEquals("–", t.rows[1].cells[0].text().en)
        assertEquals("15", t.rows[1].cells[1].text().en)
    }
}
