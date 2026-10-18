package net.moesli.scan7.ui

import net.moesli.scan7.game.Player
import net.moesli.scan7.ui.newgame.MAX_TARGET
import net.moesli.scan7.ui.newgame.MIN_TARGET
import net.moesli.scan7.ui.newgame.TARGET_STEPS
import net.moesli.scan7.ui.newgame.targetFromProgress
import net.moesli.scan7.ui.players.EditorTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Target stepper accessibility logic and the saveable editor target. */
class SeatingA11yTest {
    @Test
    fun targetProgress_snapsToSteps() {
        assertEquals(94, TARGET_STEPS)
        assertEquals(200, targetFromProgress(200f))
        assertEquals(210, targetFromProgress(206f))
        assertEquals(200, targetFromProgress(204.9f))
        assertEquals(MIN_TARGET, targetFromProgress(0f))
        assertEquals(MAX_TARGET, targetFromProgress(5000f))
        assertEquals(MIN_TARGET, targetFromProgress(Float.NaN))
    }

    @Test
    fun editorTarget_roundTripsThroughItsKey() {
        val anna = Player(2, "Anna")
        val players = listOf(Player(1, "Philip"), anna)
        assertEquals(EditorTarget.New, EditorTarget.fromKey(EditorTarget.New.key, players))
        assertEquals(EditorTarget.Edit(anna), EditorTarget.fromKey(EditorTarget.Edit(anna).key, players))
        assertNull(EditorTarget.fromKey(null, players))
        // The player was deleted meanwhile: the editor closes.
        assertNull(EditorTarget.fromKey(7, players))
    }
}
