package net.moesli.scan7.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BufferRingTest {
    private class Buf(val size: Int) {
        var freed = false
    }

    private val freed = mutableListOf<Buf>()
    private val ring = BufferRing<Buf>(3) { it.freed = true; freed += it }

    private fun fill(size: Int): Buf = ring.obtain(ring.advance(), fits = { it.size == size }, create = { Buf(size) })

    @Test
    fun `allocates lazily and cycles through the slots`() {
        assertEquals(0, ring.allocated)
        val a = fill(10)
        assertEquals(1, ring.allocated)
        val b = fill(10)
        val c = fill(10)
        assertEquals(3, ring.allocated)
        // Fourth frame wraps around and reuses the first buffer.
        assertSame(a, fill(10))
        assertSame(b, fill(10))
        assertSame(c, fill(10))
        assertTrue(freed.isEmpty())
    }

    @Test
    fun `replaces and frees a buffer that no longer fits`() {
        val a = fill(10)
        fill(10)
        fill(10)
        val bigger = fill(20)
        assertTrue(bigger !== a)
        assertEquals(listOf(a), freed)
        assertSame(bigger, ring[0])
    }

    @Test
    fun `put frees the replaced buffer but not the same one`() {
        val a = Buf(1)
        ring.put(1, a)
        ring.put(1, a)
        assertTrue(freed.isEmpty())
        ring.put(1, Buf(2))
        assertEquals(listOf(a), freed)
    }

    @Test
    fun `release frees everything and reallocates from slot 0`() {
        val a = fill(10)
        val b = fill(10)
        ring.release()
        assertEquals(0, ring.allocated)
        assertEquals(listOf(a, b), freed)
        assertTrue(a.freed && b.freed)
        assertNull(ring[0])
        assertNull(ring[1])
        val again = fill(10)
        assertTrue(again !== a && !again.freed)
        assertSame(again, ring[0])
        // A second release only frees what was allocated since.
        ring.release()
        assertEquals(listOf(a, b, again), freed)
    }
}
