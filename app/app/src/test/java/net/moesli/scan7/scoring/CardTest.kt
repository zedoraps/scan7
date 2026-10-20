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

    private val vengeanceLabels = listOf(
        "13", "lucky13", "unlucky7", "zero", "div2", "minus2", "minus4", "minus6", "minus8", "minus10",
        "just_one_more", "swap", "steal", "discard", "flip4",
    )

    @Test
    fun has23Flip7Classes_thenTheVengeanceCards() {
        // The first 23 are the Flip 7 detector's class ids (ml/config/cards.yaml): never reorder.
        assertEquals(labels + vengeanceLabels, Card.entries.map { it.label })
        assertEquals(labels, Deck.FLIP7.cards.map { it.label })
    }

    @Test
    fun decks_eachGameHasItsCards() {
        assertEquals(23, Deck.FLIP7.cards.size)
        // 1–12, 13, Lucky 13, Unlucky 7, The Zero, ÷2, five minus cards, five actions, back.
        assertEquals(28, Deck.VENGEANCE.cards.size)
        assertNull(Deck.VENGEANCE.cardFromLabel("00"))
        assertNull(Deck.VENGEANCE.cardFromLabel("plus2"))
        assertNull(Deck.VENGEANCE.cardFromLabel("second_chance"))
        assertEquals(Card.N12, Deck.VENGEANCE.cardFromLabel("12"))
        assertEquals(Card.BACK, Deck.VENGEANCE.cardFromLabel("back"))
        assertNull(Deck.FLIP7.cardFromLabel("13"))
        assertNull(Deck.FLIP7.cardFromLabel("minus2"))
        assertEquals(Card.LUCKY_13, Deck.VENGEANCE.cardFromLabel("lucky13"))
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
        assertNull(Card.fromLabel("14"))
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
