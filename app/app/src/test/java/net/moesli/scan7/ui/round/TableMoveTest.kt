package net.moesli.scan7.ui.round

import androidx.compose.ui.geometry.Offset
import net.moesli.scan7.game.TablePosition
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TableMoveTest {
    private val from = TablePosition(0.2f, 0.3f)
    private val to = TablePosition(0.6f, 0.3f)

    @Test
    fun longPressWithoutMoving_isNoMove() {
        assertFalse(isTableMove(Offset.Zero, touchSlop = 8f, from = from, to = from))
        assertFalse(isTableMove(Offset(3f, 4f), touchSlop = 8f, from = from, to = to))
    }

    @Test
    fun backToTheSameSpot_isNoMove() {
        assertFalse(isTableMove(Offset(120f, 0f), touchSlop = 8f, from = from, to = from))
    }

    @Test
    fun realDrag_isAMove() {
        assertTrue(isTableMove(Offset(120f, 0f), touchSlop = 8f, from = from, to = to))
    }
}
