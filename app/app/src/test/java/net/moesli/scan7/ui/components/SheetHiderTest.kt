package net.moesli.scan7.ui.components

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SheetHiderTest {
    private val events = mutableListOf<String>()
    private val hideDone = CompletableDeferred<Unit>()
    private val hider = SheetHider {
        events += "hide"
        hideDone.await()
    }

    @Test
    fun `action and dismiss run after the hide, in that order`() {
        val scope = TestScope()
        hider.hideThen(scope, { events += "action" }, { events += "dismiss" })
        scope.runCurrent()
        assertEquals(listOf("hide"), events)
        assertTrue(hider.hiding)
        hideDone.complete(Unit)
        scope.runCurrent()
        assertEquals(listOf("hide", "action", "dismiss"), events)
        assertFalse(hider.hiding)
    }

    @Test
    fun `a second call while hiding is ignored`() {
        val scope = TestScope()
        hider.hideThen(scope, { events += "first" }, null)
        hider.hideThen(scope, { events += "second" }, null)
        hideDone.complete(Unit)
        scope.runCurrent()
        assertEquals(listOf("hide", "first"), events)
    }

    @Test
    fun `without dismiss only the action runs`() {
        val scope = TestScope()
        hideDone.complete(Unit)
        hider.hideThen(scope, { events += "action" }, null)
        scope.runCurrent()
        assertEquals(listOf("hide", "action"), events)
    }

    @Test
    fun `nothing runs when the sheet leaves composition while hiding`() {
        val scope = TestScope()
        hider.hideThen(scope, { events += "action" }, { events += "dismiss" })
        scope.runCurrent()
        scope.cancel()
        scope.runCurrent()
        assertEquals(listOf("hide"), events)
        assertFalse(hider.hiding)
    }
}
