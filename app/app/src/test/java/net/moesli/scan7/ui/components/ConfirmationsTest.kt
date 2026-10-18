package net.moesli.scan7.ui.components

import androidx.compose.ui.graphics.Color
import net.moesli.scan7.game.ScanDirection
import net.moesli.scan7.ui.round.shortLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmationsTest {
    @Test
    fun deletePlayerIsDestructiveAndNamesThePlayer() {
        val spec = Confirmations.deletePlayer("Pingu")
        assertEquals("Delete Pingu?", spec.title)
        assertEquals("Delete", spec.confirmLabel)
        assertEquals("Past games keep showing them.", spec.body)
        assertTrue(spec.destructive)
    }

    @Test
    fun resetToOpenOnlyWhenThereIsAResult() {
        assertNull(Confirmations.resetToOpen("Ben", PlayerStatus.Open))
        val scored = Confirmations.resetToOpen("Ben", PlayerStatus.Scored(46))!!
        assertEquals("Reset Ben to open?", scored.title)
        assertEquals("Removes +46 from this round.", scored.body)
        assertEquals("Reset", scored.confirmLabel)
        assertTrue(scored.destructive)
        assertEquals("Removes 0 from this round.", Confirmations.resetToOpen("Ben", PlayerStatus.Scored(0))!!.body)
        assertEquals("Removes the BUST from this round.", Confirmations.resetToOpen("Ben", PlayerStatus.Bust)!!.body)
    }

    @Test
    fun closeRoundConfirmsOnlyWhileSomeoneIsOpen() {
        assertNull(Confirmations.closeRound(3, emptyList(), 4))
        val some = Confirmations.closeRound(3, listOf("Anna", "David"), 4)!!
        assertEquals("Close round 3?", some.title)
        assertEquals("Close round", some.confirmLabel)
        assertEquals("Anna and David haven't been scored. They'll be marked BUST.", some.body)
        assertFalse(some.destructive)
    }

    @Test
    fun closeRoundWithNobodyScoredIsDestructive() {
        val all = Confirmations.closeRound(1, listOf("Anna", "Ben", "Mia"), 3)!!
        assertEquals("Nobody has been scored yet – mark everyone BUST?", all.body)
        assertTrue(all.destructive)
        // A single player who is open is "nobody scored", too.
        assertTrue(Confirmations.closeRound(1, listOf("Anna"), 1)!!.destructive)
    }

    @Test
    fun endGameIsDestructive() {
        val spec = Confirmations.endGame()
        assertEquals("End game", spec.confirmLabel)
        assertTrue(spec.destructive)
        assertTrue(spec.body!!.contains("BUST"))
    }

    @Test
    fun startGameConfirmsOnlyOverAnUnfinishedGame() {
        assertNull(Confirmations.startGame(null))
        val spec = Confirmations.startGame(3)!!
        assertEquals("Start a new game?", spec.title)
        assertEquals("Start", spec.confirmLabel)
        assertEquals("The unfinished game (round 3) ends and can't be resumed.", spec.body)
        assertTrue(spec.destructive)
    }

    @Test
    fun bustWordIsMagentaEverywhereInTheBody() {
        val magenta = Color(0xFFFF4F9A)
        val text = withBustWord("BUST or not BUST", magenta)
        assertEquals("BUST or not BUST", text.text)
        val spans = text.spanStyles
        assertEquals(listOf(0 until 4, 12 until 16), spans.map { it.start until it.end })
        assertTrue(spans.all { it.item.color == magenta })
        assertTrue(withBustWord("Past games keep showing them.", magenta).spanStyles.isEmpty())
    }

    @Test
    fun armDelayCoversADoubleTap() {
        // Android's double-tap timeout is 300 ms: the second tap must not reach Confirm.
        assertTrue(CONFIRM_ARM_DELAY_MS > 300)
        assertTrue(CONFIRM_ARM_DELAY_MS <= 500)
    }

    @Test
    fun scanDirectionSegmentLabels() {
        assertEquals("↻ Clockwise", ScanDirection.CLOCKWISE.shortLabel())
        assertEquals("↺ Counter", ScanDirection.COUNTER_CLOCKWISE.shortLabel())
    }
}
