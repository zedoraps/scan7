package net.moesli.scan7.ui.scan

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BustOverlayTest {
    private var undos = 0
    private var toasts = 0
    private val undo = CompletableDeferred<Unit>()

    private suspend fun tap(bust: suspend () -> String = { "saved" }): String? = bustWithOverlay(
        bust = bust,
        undoBust = { undos++ },
        undo = undo,
        overlayMs = { 1_000L },
        onLeftBusted = { toasts++ },
    )

    @Test
    fun overlayRunsOut_bustStays() = runTest {
        assertEquals("saved", tap())
        assertEquals(0, undos)
        assertEquals(0, toasts)
    }

    @Test
    fun undoDuringOverlay_undoesTheBust() = runTest {
        var result: String? = "pending"
        val job = launch { result = tap() }
        advanceTimeBy(300)
        undo.complete(Unit)
        job.join()
        assertNull(result)
        assertEquals(1, undos)
        assertEquals(0, toasts)
    }

    @Test
    fun backDuringOverlay_showsTheToast() = runTest {
        val job = launch { tap() }
        advanceTimeBy(300)
        job.cancel()
        job.join()
        assertEquals(0, undos)
        assertEquals(1, toasts)
    }

    @Test
    fun backDuringTheWrite_showsTheToast() = runTest {
        val written = CompletableDeferred<String>()
        val job = launch { tap { written.await() } }
        runCurrent()
        job.cancel()
        job.join()
        assertEquals(1, toasts)
    }

    @Test
    fun backRightAfterUndo_stillUndoes() = runTest {
        val job = launch { tap() }
        advanceTimeBy(300)
        undo.complete(Unit)
        job.cancel()
        job.join()
        assertEquals(1, undos)
        assertEquals(0, toasts)
    }
}
