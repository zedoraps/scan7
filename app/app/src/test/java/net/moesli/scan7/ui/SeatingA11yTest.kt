package net.moesli.scan7.ui

import net.moesli.scan7.game.Player
import net.moesli.scan7.ui.newgame.MAX_TARGET
import net.moesli.scan7.ui.newgame.MIN_TARGET
import net.moesli.scan7.ui.newgame.SeatMove
import net.moesli.scan7.ui.newgame.TARGET_STEPS
import net.moesli.scan7.ui.newgame.autoScrollStep
import net.moesli.scan7.ui.newgame.seatMoves
import net.moesli.scan7.ui.newgame.targetFromProgress
import net.moesli.scan7.ui.players.EditorTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Seating reorder / target stepper accessibility logic and the saveable editor target. */
class SeatingA11yTest {
    @Test
    fun seatMoves_byPosition() {
        assertEquals(listOf(SeatMove("Move down", 1), SeatMove("Move to last seat", 3)), seatMoves(0, 4))
        assertEquals(listOf(SeatMove("Move up", 0), SeatMove("Move down", 2), SeatMove("Move to last seat", 3)), seatMoves(1, 4))
        assertEquals(listOf(SeatMove("Move up", 1), SeatMove("Move down", 3), SeatMove("Move to first seat", 0)), seatMoves(2, 4))
        assertEquals(listOf(SeatMove("Move up", 2), SeatMove("Move to first seat", 0)), seatMoves(3, 4))
        assertEquals(listOf(SeatMove("Move down", 1)), seatMoves(0, 2))
        assertTrue(seatMoves(0, 1).isEmpty())
        assertTrue(seatMoves(5, 4).isEmpty())
    }

    @Test
    fun autoScroll_onlyInTheEdgeZones() {
        // Viewport 0..1000, edge 100, max 10 px per frame.
        assertEquals(0f, autoScrollStep(400f, 480f, 0f, 1000f, 100f, 10f))
        assertEquals(-5f, autoScrollStep(50f, 50f, 0f, 1000f, 100f, 10f))
        assertEquals(-10f, autoScrollStep(-30f, -30f, 0f, 1000f, 100f, 10f))
        assertEquals(5f, autoScrollStep(950f, 950f, 0f, 1000f, 100f, 10f))
        assertEquals(10f, autoScrollStep(1200f, 1200f, 0f, 1000f, 100f, 10f))
        // A viewport too small for two edge zones: no auto-scroll.
        assertEquals(0f, autoScrollStep(50f, 50f, 0f, 150f, 100f, 10f))
    }

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
