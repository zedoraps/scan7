package net.moesli.scan7.ui.round

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import net.moesli.scan7.game.DEFAULT_ELLIPSE_RX
import net.moesli.scan7.game.TablePosition
import net.moesli.scan7.game.clockwiseAngle
import net.moesli.scan7.game.defaultPositions
import net.moesli.scan7.ui.components.PlayerStatus
import net.moesli.scan7.ui.components.PlayerUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundLayoutTest {
    private val eps = 1e-3f

    // ---- Default ellipse ----

    @Test
    fun defaultPositionsStartAtTopAndGoClockwise() {
        val p = defaultPositions(4)
        assertNear(TablePosition(0.5f, 0f), p[0]) // top
        assertNear(TablePosition(0.5f + DEFAULT_ELLIPSE_RX, 0.5f), p[1]) // right
        assertNear(TablePosition(0.5f, 1f), p[2]) // bottom
        assertNear(TablePosition(0.5f - DEFAULT_ELLIPSE_RX, 0.5f), p[3]) // left
    }

    @Test
    fun defaultPositionsAreEvenlySpacedAndInsideTheArea() {
        for (n in 1..10) {
            val p = defaultPositions(n)
            assertEquals(n, p.size)
            p.forEach { assertTrue(it.x in 0f..1f && it.y in 0f..1f) }
            val angles = p.map { clockwiseAngle(it) }
            assertEquals(angles.sorted(), angles) // clockwise in seating order
        }
    }

    @Test
    fun defaultEllipseMatchesThePrototypeOnTheReferenceFrame() {
        // 390 wide, area 112–704, Large card 112×140: prototype seat 1 of 4 at left 195+RX−W/2.
        val g = TableGeometry(left = 8f, top = 112f, width = 374f, height = 592f, cardWidth = 112f, cardHeight = 140f)
        val p = defaultPositions(4).map { g.topLeftOf(it) }
        val rx = 195f - 112f / 2 - 12f
        assertEquals(195f - 56f, p[0].x, 0.5f)
        assertEquals(112f, p[0].y, eps)
        assertEquals(195f + rx - 56f, p[1].x, 1f)
        assertEquals(408f - 70f, p[1].y, eps)
        assertEquals(704f - 140f, p[2].y, eps)
    }

    // ---- Geometry / clamping ----

    private val geometry = TableGeometry(left = 8f, top = 112f, width = 374f, height = 592f, cardWidth = 72f, cardHeight = 96f)

    @Test
    fun clampKeepsCardsInsideTheArea() {
        assertEquals(Offset(8f, 112f), geometry.clampTopLeft(Offset(-50f, 0f)))
        assertEquals(Offset(8f + 374f - 72f, 112f + 592f - 96f), geometry.clampTopLeft(Offset(999f, 999f)))
        assertEquals(Offset(100f, 300f), geometry.clampTopLeft(Offset(100f, 300f)))
    }

    @Test
    fun positionRoundTripsAndIsClamped() {
        val p = TablePosition(0.25f, 0.8f)
        val back = geometry.positionOf(geometry.topLeftOf(p))
        assertEquals(p.x, back.x, eps)
        assertEquals(p.y, back.y, eps)
        assertEquals(TablePosition(0f, 1f), geometry.positionOf(Offset(-100f, 5000f)))
        assertEquals(geometry.topLeftOf(TablePosition(1f, 0f)), geometry.topLeftOf(TablePosition(3f, -2f)))
    }

    // ---- List reorder ----

    @Test
    fun swappedExchangesTwoItems() {
        val l = listOf("a", "b", "c", "d")
        assertEquals(listOf("c", "b", "a", "d"), l.swapped(0, 2))
        assertEquals(listOf("a", "c", "b", "d"), l.swapped(2, 1))
        assertEquals(l, l.swapped(1, 1))
        assertEquals(listOf("d", "b", "c", "a"), l.swapped(0, 99)) // clamped
    }

    @Test
    fun dropIndexRoundsToNearestSlot() {
        assertEquals(0, dropIndex(-20f, 80f, 4))
        assertEquals(1, dropIndex(41f, 80f, 4))
        assertEquals(0, dropIndex(39f, 80f, 4))
        assertEquals(3, dropIndex(400f, 80f, 4))
        assertEquals(-20f, clampRowDrag(-100f, 80f, 4, 20f), eps)
        assertEquals(260f, clampRowDrag(999f, 80f, 4, 20f), eps)
    }

    @Test
    fun clockwiseSortDerivesSeatingFromPositions() {
        val seats = listOf("left" to TablePosition(0f, 0.5f), "top" to TablePosition(0.5f, 0f), "right" to TablePosition(1f, 0.5f))
        assertEquals(listOf("top", "right", "left"), seats.sortedClockwiseBy { it.second }.map { it.first })
    }

    // ---- Next open / ranks ----

    private fun rp(id: String, status: PlayerStatus) = RoundPlayer(PlayerUi(id, Color.Red, 0, status), TablePosition(0.5f, 0.5f))

    @Test
    fun nextOpen() {
        val players = listOf(rp("a", PlayerStatus.Scored(3)), rp("b", PlayerStatus.Open), rp("c", PlayerStatus.Bust), rp("d", PlayerStatus.Open))
        assertEquals("b", firstOpenId(players))
        assertEquals("d", nextOpenIdAfter(players, "b"))
        assertEquals("b", nextOpenIdAfter(players, "d")) // wraps
        assertEquals("b", nextOpenIdAfter(players, null))
        assertNull(firstOpenId(players.map { rp(it.id, PlayerStatus.Bust) }))
        assertEquals("b", RoundUiState(1, 200, players).nextOpen?.id)
        assertNull(RoundUiState(1, 200, players, nextOpenId = "a").nextOpen) // done players never
    }

    @Test
    fun ranksShareTiesAndPutBustBelowZero() {
        val players = listOf(
            PlayerUi("a", Color.Red, 0, PlayerStatus.Scored(20)),
            PlayerUi("b", Color.Red, 0, PlayerStatus.Bust),
            PlayerUi("c", Color.Red, 0, PlayerStatus.Scored(20)),
            PlayerUi("d", Color.Red, 0, PlayerStatus.Scored(0)),
        )
        assertEquals(mapOf("a" to 1, "b" to 4, "c" to 1, "d" to 3), roundRanks(players))
    }

    private fun assertNear(expected: TablePosition, actual: TablePosition) {
        assertEquals(expected.x, actual.x, eps)
        assertEquals(expected.y, actual.y, eps)
    }
}
