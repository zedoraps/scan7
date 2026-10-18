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
}
