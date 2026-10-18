package net.moesli.scan7.scoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardTest {
    private val labels = listOf(
        "00", "01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12",
        "plus2", "plus4", "plus6", "plus8", "plus10", "x2",
        "freeze", "flip3", "second_chance", "back",
    )

    @Test
    fun has23Classes() {
        assertEquals(23, Card.entries.size)
        assertEquals(labels, Card.entries.map { it.label })
    }

    @Test
    fun labelRoundTrip() {
        for (label in labels) {
            assertEquals(label, Card.fromLabel(label)!!.label)
        }
        for (card in Card.entries) {
            assertEquals(card, Card.fromLabel(card.label))
        }
    }

    @Test
    fun unknownLabel_isNull() {
        assertNull(Card.fromLabel("13"))
        assertNull(Card.fromLabel("0"))
        assertNull(Card.fromLabel(""))
        assertNull(Card.fromLabel("X2"))
    }

    @Test
    fun kindsAndValues() {
        for (n in 0..12) {
            val card = Card.fromLabel("%02d".format(n))!!
            assertEquals(CardKind.NUMBER, card.kind)
            assertEquals(n, card.value)
        }
        for (n in listOf(2, 4, 6, 8, 10)) {
            val card = Card.fromLabel("plus$n")!!
            assertEquals(CardKind.MODIFIER_PLUS, card.kind)
            assertEquals(n, card.value)
        }
        assertEquals(CardKind.MODIFIER_TIMES2, Card.TIMES_2.kind)
        assertNull(Card.TIMES_2.value)
        for (card in listOf(Card.FREEZE, Card.FLIP_THREE, Card.SECOND_CHANCE)) {
            assertEquals(CardKind.ACTION, card.kind)
            assertNull(card.value)
        }
        assertEquals(CardKind.BACK, Card.BACK.kind)
        assertNull(Card.BACK.value)
    }
}
