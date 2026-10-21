package net.moesli.scan7.game

import net.moesli.scan7.detection.DetectorModel
import net.moesli.scan7.scoring.Card
import net.moesli.scan7.scoring.Deck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VengeanceGameTest {
    private val players = listOf(Player(1, "Anna"), Player(2, "Ben"), Player(3, "Cleo"))

    private fun game() = GameState.newGame(players, kind = GameKind.VENGEANCE)

    private fun cards(vararg c: Card) = c.map { ConfirmedCard(it) }

    @Test
    fun rules_raceTo200_highestWins_noDetectorYet() {
        val rules = GameKind.VENGEANCE.rules
        assertEquals(200, rules.defaultTarget)
        assertFalse(rules.lowestWins)
        assertTrue(rules.hasBust)
        assertFalse(rules.hasRoundEnder)
        assertEquals(0, rules.minPoints)
        assertTrue(rules.hasDetector)
        assertEquals("vengeance/model.tflite", DetectorModel.of(Deck.VENGEANCE).modelAsset)
        assertEquals("labels.txt", DetectorModel.of(Deck.FLIP7).labelsAsset)
        assertTrue(GameKind.FLIP7.rules.hasDetector)
        assertEquals(Deck.VENGEANCE, GameKind.VENGEANCE.deck)
        assertEquals(Deck.FLIP7, GameKind.FLIP7.deck)
        assertNull(GameKind.SKYJO.deck)
    }

    @Test
    fun cardFromLabel_onlyTheGamesCards() {
        assertEquals(Card.MINUS_6, VengeanceRules.cardFromLabel("minus6"))
        assertNull(VengeanceRules.cardFromLabel("plus6"))
        assertNull(Flip7Rules.cardFromLabel("minus6"))
        assertEquals(Card.PLUS_6, Flip7Rules.cardFromLabel("plus6"))
    }

    @Test
    fun scoreCards_usesTheVengeanceRules() {
        var g = game().scoreCards(1, cards(Card.N13, Card.N11, Card.N8, Card.DIVIDE_2, Card.MINUS_6), ResultSource.MANUAL, now = 1)
        assertEquals(ResultStatus.SCORED, g.result(1).status)
        assertEquals(10, g.result(1).points)
        g = g.scoreCards(2, cards(Card.LUCKY_13, Card.N13), ResultSource.MANUAL, now = 2)
        assertEquals(26, g.result(2).points)
        g = g.scoreCards(3, cards(Card.N13, Card.N13), ResultSource.MANUAL, now = 3)
        assertEquals(ResultStatus.BUST, g.result(3).status)
        assertEquals(0, g.result(3).points)
        // Action cards are stored as ignored cards and never score.
        assertTrue(ConfirmedCard(Card.STEAL).ignored)
        assertEquals(listOf(10, 26, 0), players.map { g.total(it.id) })
    }
}
