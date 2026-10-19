package net.moesli.scan7.ui.components

import net.moesli.scan7.ui.text.de
import net.moesli.scan7.ui.text.en
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerA11yTest {
    private fun p(status: PlayerStatus, leader: Boolean = false) =
        PlayerUi("Mia", sampleAvatar("Mia"), 96, status, isLeader = leader)

    @Test
    fun cardSentence() {
        assertEquals("Mia, 96 points, not scored yet", p(PlayerStatus.Open).accessibilityLabel().en)
        assertEquals("Mia, 96 points, scored 25 this round, leading", p(PlayerStatus.Scored(25), leader = true).accessibilityLabel().en)
        assertEquals("Mia, 96 Punkte, diese Runde 25 Punkte, führt", p(PlayerStatus.Scored(25), leader = true).accessibilityLabel().de)
        assertEquals("Mia, 96 Punkte, diese Runde raus", p(PlayerStatus.Bust).accessibilityLabel().de)
        assertEquals("Mia, 96 Punkte, diese Runde 1 Punkt", p(PlayerStatus.Scored(1)).accessibilityLabel().de)
    }

    @Test
    fun adjustedTotalIsSpoken() {
        val minus = p(PlayerStatus.Open).copy(adjustment = -5)
        assertEquals("Mia, 96 points, adjusted by minus 5, not scored yet", minus.accessibilityLabel().en)
        assertEquals("Mia, 96 Punkte, korrigiert um minus 5, noch nicht gewertet", minus.accessibilityLabel().de)
        assertEquals("Mia, 96 points, adjusted by plus 10, bust this round", p(PlayerStatus.Bust).copy(adjustment = 10).accessibilityLabel().en)
        // Adjustments that cancel out still say the total was adjusted.
        assertEquals("Mia, 96 points, includes total adjustments, not scored yet", p(PlayerStatus.Open).copy(adjustment = 0).accessibilityLabel().en)
    }

    @Test
    fun statusTagDropsOnlyTheIcon() {
        assertEquals("BUST", "✕ BUST".withoutIcon())
        assertEquals("RAUS", "✕ RAUS".withoutIcon())
        assertEquals("+27", "✓ +27".withoutIcon())
        assertEquals("OPEN", "OPEN".withoutIcon())
        assertEquals("OFFEN", "OFFEN".withoutIcon())
    }
}
