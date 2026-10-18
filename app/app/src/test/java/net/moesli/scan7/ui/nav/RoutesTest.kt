package net.moesli.scan7.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutesTest {
    private val all = listOf(
        Route.Home,
        Route.Players,
        Route.NewGame(),
        Route.NewGame(listOf(3, 1, 2), 250),
        Route.NewGame(emptyList()),
        Route.Round(12),
        Route.Scan(12, 3, ScanStart.CAMERA),
        Route.Scan(12, 3, ScanStart.NEXT),
        Route.Scan(12, 4, ScanStart.EDIT, round = 2),
        Route.Scan(12, 4, ScanStart.MANUAL),
        Route.Scoreboard(12),
        Route.GameOver(12),
        Route.Debug(DebugScreen.PHOTO),
    )

    @Test
    fun encodeDecodeRoundTrip() {
        all.forEach { assertEquals(it, decodeRoute(it.encode())) }
    }

    @Test
    fun garbageDecodesToNull() {
        assertNull(decodeRoute("nope"))
        assertNull(decodeRoute("round:x"))
        assertNull(decodeRoute("scan:1:2:WHAT:"))
    }

    @Test
    fun scanNextChainKeepsOneStateKey() {
        assertEquals(Route.Scan(1, 2, ScanStart.NEXT).stateKey, Route.Scan(1, 3, ScanStart.NEXT).stateKey)
        assert(Route.Scan(1, 2, ScanStart.CAMERA).stateKey != Route.Scan(1, 3, ScanStart.CAMERA).stateKey)
    }

    @Test
    fun stackOperations() {
        val home = listOf(Route.Home)
        assertNull(Stack.pop(home))
        val round = Stack.push(home, Route.Round(1))
        assertEquals(listOf(Route.Home, Route.Round(1)), round)
        assertEquals(round, Stack.push(round, Route.Round(1))) // no duplicate on double tap
        val scan = Stack.push(round, Route.Scan(1, 2, ScanStart.NEXT))
        assertEquals(listOf(Route.Home, Route.Round(1), Route.Scan(1, 3, ScanStart.NEXT)), Stack.replaceTop(scan, Route.Scan(1, 3, ScanStart.NEXT)))
        assertEquals(round, Stack.pop(scan))
        assertEquals(listOf(Route.Home, Route.Round(5)), Stack.replaceTop(listOf(Route.Home, Route.NewGame()), Route.Round(5)))
        assertEquals(listOf(Route.Home, Route.Round(5)), Stack.normalize(listOf(Route.Round(5))))
        assertEquals(home, Stack.normalize(emptyList()))
        assertEquals(home, Stack.replaceTop(home, Route.Home))
    }
}
