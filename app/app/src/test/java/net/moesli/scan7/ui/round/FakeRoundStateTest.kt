package net.moesli.scan7.ui.round

import kotlin.random.Random
import net.moesli.scan7.game.TablePosition
import net.moesli.scan7.ui.components.PlayerStatus
import net.moesli.scan7.ui.components.RoundView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeRoundStateTest {
    private fun fake(count: Int, preset: FakeRoundState.Preset = FakeRoundState.Preset.Mixed) =
        FakeRoundState(count, preset, Random(1))

    private val FakeRoundState.ids get() = state.players.map { it.id }

    @Test
    fun defaultViewByPlayerCount() {
        assertEquals(RoundView.Table, fake(6).state.view)
        assertEquals(RoundView.List, fake(7).state.view)
    }

    @Test
    fun bustUndoAndScanNext() {
        val f = fake(4, FakeRoundState.Preset.AllOpen)
        f.onBust("Philip")
        assertEquals(PlayerStatus.Bust, f.state.players[0].player.status)
        assertEquals("Anna", f.state.nextOpen?.id)
        f.onUndoBust("Philip")
        assertEquals(PlayerStatus.Open, f.state.players[0].player.status)
        f.onScanNext()
        assertTrue(f.state.players[0].player.status is PlayerStatus.Scored)
        assertEquals("Anna", f.state.nextOpen?.id)
    }

    @Test
    fun tableDropReDerivesSeatingClockwise() {
        val f = fake(4)
        assertEquals(listOf("Philip", "Anna", "Ben", "Mia"), f.ids)
        // Philip moves to the bottom-left, between Ben (bottom) and Mia (left).
        f.onMoveCard("Philip", TablePosition(0.2f, 0.9f))
        assertEquals(listOf("Anna", "Ben", "Philip", "Mia"), f.ids)
    }

    @Test
    fun listSwapKeepsClockwiseTableOrder() {
        val f = fake(4)
        val before = f.state.players.map { it.position }
        f.onMoveRow(0, 2)
        assertEquals(listOf("Ben", "Anna", "Philip", "Mia"), f.ids)
        assertEquals(before, f.state.players.map { it.position }) // seats keep their spots
    }

    @Test
    fun closeRoundBustsOpenPlayersThenNextRound() {
        val f = fake(4)
        f.onCloseRound()
        val s = f.state
        assertTrue(s.roundOver)
        assertTrue(s.allDone)
        assertNull(s.nextOpen)
        assertEquals(1, s.players.first { it.id == "Philip" }.rank) // 46 is the best round
        val total = s.players.first { it.id == "Philip" }.player.total
        f.onNextRound()
        assertEquals(5, f.state.round)
        assertFalse(f.state.roundOver)
        assertTrue(f.state.players.all { it.player.status == PlayerStatus.Open })
        assertEquals(total, f.state.players.first { it.id == "Philip" }.player.total)
    }

    @Test
    fun leaderIsHighestTotal() {
        assertEquals(listOf("Ben"), fake(4).state.leaders.map { it.id })
    }
}
