package net.moesli.scan7.ui

import net.moesli.scan7.game.Seat
import net.moesli.scan7.game.TablePosition
import net.moesli.scan7.game.defaultPositions
import net.moesli.scan7.ui.newgame.SeatingDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** The new game's seats step: draft seats follow the game's arrange rules and survive saving. */
class SeatingDraftTest {
    private val ring = SeatingDraft.of(listOf(10L, 20L, 30L, 40L))

    @Test
    fun of_usesTheDefaultRingInPickOrder() {
        assertEquals(listOf(10L, 20L, 30L, 40L), ring.ids)
        assertEquals(defaultPositions(4), ring.positions)
    }

    @Test
    fun of_reusesPreviousSpotsForTheSamePlayers() {
        val previous = mapOf(1L to TablePosition(0.2f, 0.9f), 2L to TablePosition(0.5f, 0f), 3L to TablePosition(0.9f, 0.6f))
        val d = SeatingDraft.of(listOf(2, 3, 1), previous)
        assertEquals(listOf(Seat(2, previous.getValue(2)), Seat(3, previous.getValue(3)), Seat(1, previous.getValue(1))), d.seats)
        // A different player set: default ring.
        assertEquals(defaultPositions(2), SeatingDraft.of(listOf(1, 2), previous).positions)
    }

    @Test
    fun moveCard_reordersClockwiseLikeTheGame() {
        // Ring: 10 top, 20 right, 30 bottom, 40 left. Drop 40 just right of the top → seat 2.
        val moved = ring.moveCard(40, TablePosition(0.6f, 0f))
        assertEquals(listOf(10L, 40L, 20L, 30L), moved.ids)
        assertEquals(TablePosition(0.6f, 0f), moved.seats[1].position)
        assertSame(ring, ring.moveCard(99, TablePosition(0f, 0f)))
    }

    @Test
    fun swapRows_swapsPlayersAndKeepsTheSpots() {
        val swapped = ring.swapRows(0, 2)
        assertEquals(listOf(30L, 20L, 10L, 40L), swapped.ids)
        assertEquals(ring.positions, swapped.positions)
        assertSame(ring, ring.swapRows(0, 9))
    }

    @Test
    fun retain_dropsDeletedPlayers() {
        assertSame(ring, ring.retain(setOf(10, 20, 30, 40, 50)))
        val kept = ring.retain(setOf(10, 30))
        assertEquals(listOf(10L, 30L), kept.ids)
        assertEquals(listOf(ring.positions[0], ring.positions[2]), kept.positions)
    }

    @Test
    fun saveRestore_roundTrips() {
        val d = ring.moveCard(30, TablePosition(0.123f, 0.987f))
        assertEquals(d, SeatingDraft.restore(SeatingDraft.save(d)))
        assertEquals(SeatingDraft(emptyList()), SeatingDraft.restore(LongArray(0)))
    }
}
