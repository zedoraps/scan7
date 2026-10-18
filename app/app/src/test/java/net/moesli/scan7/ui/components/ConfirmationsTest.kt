package net.moesli.scan7.ui.components

import net.moesli.scan7.ui.text.de
import net.moesli.scan7.ui.text.en
import androidx.compose.ui.graphics.Color
import net.moesli.scan7.game.ScanDirection
import net.moesli.scan7.ui.nav.AfterScan
import net.moesli.scan7.ui.nav.EntryMode
import net.moesli.scan7.ui.round.segmentLabel
import net.moesli.scan7.ui.round.shortLabel
import net.moesli.scan7.ui.round.spoken
import net.moesli.scan7.ui.round.spokenLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmationsTest {
    @Test
    fun deletePlayerIsDestructiveAndNamesThePlayer() {
        val spec = Confirmations.deletePlayer("Pingu")
        assertEquals("Delete Pingu?", spec.title.en)
        assertEquals("Delete", spec.confirmLabel.en)
        assertEquals("Past games keep showing them.", spec.body!!.en)
        assertEquals("Pingu löschen?", spec.title.de)
        assertTrue(spec.destructive)
    }

    @Test
    fun resetToOpenOnlyWhenThereIsAResult() {
        assertNull(Confirmations.resetToOpen("Ben", PlayerStatus.Open))
        val scored = Confirmations.resetToOpen("Ben", PlayerStatus.Scored(46))!!
        assertEquals("Reset the last entry for Ben?", scored.title.en)
        assertEquals("Removes +46 from this round.", scored.body!!.en)
        assertEquals("Reset", scored.confirmLabel.en)
        assertTrue(scored.destructive)
        assertEquals("Removes 0 from this round.", Confirmations.resetToOpen("Ben", PlayerStatus.Scored(0))!!.body!!.en)
        assertEquals("Removes the BUST from this round.", Confirmations.resetToOpen("Ben", PlayerStatus.Bust)!!.body!!.en)
        assertEquals("Entfernt das RAUS aus dieser Runde.", Confirmations.resetToOpen("Ben", PlayerStatus.Bust)!!.body!!.de)
    }

    @Test
    fun lowerTargetAsksOnlyWhenTheGameWouldEnd() {
        assertNull(Confirmations.lowerTarget(150, reachesIt = false))
        val spec = Confirmations.lowerTarget(150, reachesIt = true)!!
        assertEquals("Lower the target to 150?", spec.title.en)
        assertEquals("The game ends after this round.", spec.body!!.en)
        assertEquals("Ziel auf 150 senken?", spec.title.de)
        assertTrue(spec.destructive)
    }

    @Test
    fun closeRoundConfirmsOnlyWhileSomeoneIsOpen() {
        assertNull(Confirmations.closeRound(3, emptyList(), 4))
        val some = Confirmations.closeRound(3, listOf("Anna", "David"), 4)!!
        assertEquals("Close round 3?", some.title.en)
        assertEquals("Close round", some.confirmLabel.en)
        assertEquals("Anna and David haven't been scored. They'll be marked BUST.", some.body!!.en)
        assertEquals("Runde 3 beenden?", some.title.de)
        assertFalse(some.destructive)
    }

    @Test
    fun closeRoundWithNobodyScoredIsDestructive() {
        val all = Confirmations.closeRound(1, listOf("Anna", "Ben", "Mia"), 3)!!
        assertEquals("Nobody has been scored yet – mark everyone BUST?", all.body!!.en)
        assertTrue(all.destructive)
        // A single player who is open is "nobody scored", too.
        assertTrue(Confirmations.closeRound(1, listOf("Anna"), 1)!!.destructive)
    }

    @Test
    fun endGameIsDestructive() {
        val spec = Confirmations.endGame()
        assertEquals("End game", spec.confirmLabel.en)
        assertTrue(spec.destructive)
        assertTrue(spec.body!!.en.contains("BUST"))
        assertTrue(spec.body!!.de.contains("RAUS"))
    }

    @Test
    fun startGameConfirmsOnlyOverAnUnfinishedGame() {
        assertNull(Confirmations.startGame(null))
        val spec = Confirmations.startGame(3)!!
        assertEquals("Start a new game?", spec.title.en)
        assertEquals("Start", spec.confirmLabel.en)
        assertEquals("The unfinished game (round 3) ends and can't be resumed.", spec.body!!.en)
        assertTrue(spec.destructive)
    }

    @Test
    fun bustWordIsMagentaEverywhereInTheBody() {
        val magenta = Color(0xFFFF4F9A)
        val text = withBustWord("BUST or not BUST", magenta, "BUST")
        assertEquals("BUST or not BUST", text.text)
        val spans = text.spanStyles
        assertEquals(listOf(0 until 4, 12 until 16), spans.map { it.start until it.end })
        assertTrue(spans.all { it.item.color == magenta })
        assertTrue(withBustWord("Past games keep showing them.", magenta, "BUST").spanStyles.isEmpty())
        // German: the word is "RAUS" (R.string.bust_word).
        val de = withBustWord(Confirmations.endGame().body!!.de, magenta, "RAUS")
        assertEquals(1, de.spanStyles.size)
    }

    @Test
    fun armDelayCoversADoubleTap() {
        // Android's double-tap timeout is 300 ms: the second tap must not reach Confirm.
        assertTrue(CONFIRM_ARM_DELAY_MS > 300)
        assertTrue(CONFIRM_ARM_DELAY_MS <= 500)
    }

    @Test
    fun scanDirectionSegmentLabels() {
        assertEquals("↻ Clockwise", ScanDirection.CLOCKWISE.shortLabel().en)
        assertEquals("↺ Counter", ScanDirection.COUNTER_CLOCKWISE.shortLabel().en)
        assertEquals("↻ Uhrzeigersinn", ScanDirection.CLOCKWISE.shortLabel().de)
        assertEquals("Scan clockwise", ScanDirection.CLOCKWISE.spoken().en)
    }

    @Test
    fun roundMenuSettingLabels() {
        assertEquals(listOf("Next player", "Back to table"), AfterScan.entries.map { it.segmentLabel().en })
        assertEquals(listOf("Nächster Spieler", "Zum Tisch"), AfterScan.entries.map { it.segmentLabel().de })
        assertEquals(listOf("Scan", "Type"), EntryMode.entries.map { it.segmentLabel().en })
        assertEquals(listOf("Scannen", "Eingeben"), EntryMode.entries.map { it.segmentLabel().de })
        assertEquals("Enter results by typing", EntryMode.TYPE.spokenLabel().en)
    }
}
