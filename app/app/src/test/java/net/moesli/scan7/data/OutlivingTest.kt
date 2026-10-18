package net.moesli.scan7.data

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutlivingTest {
    private val app = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Test
    fun returnsTheResult() = runBlocking {
        assertEquals(42, outliving(app) { 42 })
    }

    @Test
    fun finishesWhenTheCallerIsCancelled() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val done = CompletableDeferred<Unit>()
        val wrote = AtomicBoolean(false)
        val ui = launch(Dispatchers.Default) {
            outliving(app) {
                started.complete(Unit)
                release.await()
                wrote.set(true)
                done.complete(Unit)
            }
        }
        started.await()
        ui.cancelAndJoin()
        release.complete(Unit)
        withTimeout(5_000) { done.await() }
        assertTrue(wrote.get())
    }

    @Test
    fun failuresReachTheCallerNotTheScope() = runBlocking {
        val result = runCatching { outliving(app) { error("boom") } }
        assertEquals("boom", result.exceptionOrNull()?.message)
        // The app scope survives a failed write.
        assertEquals(1, outliving(app) { 1 })
    }
}
