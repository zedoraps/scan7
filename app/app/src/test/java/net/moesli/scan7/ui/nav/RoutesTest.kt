package net.moesli.scan7.ui.nav

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutesTest {
    private val all = listOf(
        Route.Home,
        Route.Players,
        Route.NewGame(),
        Route.NewGame(listOf(3, 1, 2), 250),
        Route.NewGame(emptyList()),
        Route.Round(12),
        Route.GamePlayers(12),
        Route.Scan(12, 3, ScanStart.CAMERA),
        Route.Scan(12, 3, ScanStart.NEXT),
        Route.Scan(12, 4, ScanStart.EDIT, round = 2),
        Route.Scan(12, 4, ScanStart.MANUAL),
        Route.Scoreboard(12),
        Route.GameOver(12),
        Route.About(),
        Route.About("apache-2.0"),
        Route.Debug(DebugScreen.PHOTO),
        Route.Join(),
        Route.Join("scan7://join?e=abc:1&n=Anna"),
    )

    /** The saved back stack uses the sealed serializer (rotation / process death). */
    @Test
    fun serializeRoundTrip() {
        all.forEach { assertEquals(it, Json.decodeFromString(Route.serializer(), Json.encodeToString(Route.serializer(), it))) }
    }

    @Test
    fun stateKeysAreUniqueExceptTheScanNextChain() {
        assertEquals(all.size, all.map { it.stateKey }.toSet().size)
        assertEquals(Route.Scan(1, 2, ScanStart.NEXT).stateKey, Route.Scan(1, 3, ScanStart.NEXT).stateKey)
        assertNotEquals(Route.Scan(1, 2, ScanStart.CAMERA).stateKey, Route.Scan(1, 3, ScanStart.CAMERA).stateKey)
        assertNotEquals(Route.Scan(1, 2, ScanStart.NEXT).stateKey, Route.Scan(2, 2, ScanStart.NEXT).stateKey)
    }

    @Test
    fun cameraScreens() {
        assertTrue(Route.Scan(1, 2, ScanStart.CAMERA).hasCamera)
        assertTrue(Route.Scan(1, 2, ScanStart.NEXT).hasCamera)
        assertTrue(Route.Debug(DebugScreen.CAMERA).hasCamera)
        assertFalse(Route.Scan(1, 2, ScanStart.EDIT).hasCamera)
        assertFalse(Route.Round(1).hasCamera)
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

    @Test
    fun stackHoldsEachStateKeyOnce() {
        // A guest rejoins its own joined game from the round: Join is replaced by that same round.
        val joining = listOf(Route.Home, Route.Round(5), Route.Join())
        assertEquals(listOf(Route.Home, Route.Round(5)), Stack.replaceTop(joining, Route.Round(5)))
        // Pushing a route further down goes back to it.
        val deep = listOf(Route.Home, Route.Round(1), Route.Scoreboard(1))
        assertEquals(listOf(Route.Home, Route.Round(1)), Stack.push(deep, Route.Round(1)))
        assertEquals(listOf(Route.Home), Stack.push(deep, Route.Home))
        assertEquals(listOf(Route.Home, Route.Round(1)), Stack.normalize(listOf(Route.Home, Route.Round(1), Route.Round(1))))
    }
}
