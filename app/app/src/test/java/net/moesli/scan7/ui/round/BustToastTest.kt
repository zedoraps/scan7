package net.moesli.scan7.ui.round

import net.moesli.scan7.ui.text.de
import net.moesli.scan7.ui.text.en
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BustToastTest {
    @Test
    fun newToastReplacesTheOldOne_finishedOnlyClearsItsOwn() {
        val t = BustToastState()
        t.show("1", "Anna")
        val anna = checkNotNull(t.current)
        t.show("2", "Ben")
        val ben = checkNotNull(t.current)
        assertTrue(ben is BustToastData && ben.playerId == "2")
        t.finished(anna) // the replaced one ends late: Ben's stays
        assertEquals(ben, t.current)
        t.finished(ben)
        assertNull(t.current)
    }

    @Test
    fun sameBustTwiceIsANewShowing() {
        val t = BustToastState()
        t.show("1", "Anna")
        val first = t.current
        t.show("1", "Anna")
        assertNotEquals(first, t.current)
    }

    @Test
    fun texts() {
        val bust = toastText(BustToastData("1", "Anna", 1))
        assertEquals(listOf("Anna marked bust", "Undo", "Undo bust for Anna"), listOf(bust.message.en, bust.action.en, bust.actionDescription.en))
        assertEquals("Anna ist raus", bust.message.de)
    }
}
