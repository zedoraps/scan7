package net.moesli.scan7.skyjo

import net.moesli.scan7.game.ConfirmedCard
import net.moesli.scan7.game.EnderPenalty
import net.moesli.scan7.game.GameKind
import net.moesli.scan7.game.GameState
import net.moesli.scan7.game.Player
import net.moesli.scan7.game.ResultSource
import net.moesli.scan7.game.ResultStatus
import net.moesli.scan7.scoring.Card
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyjoGameTest {
    private val anna = Player(1, "Anna")
    private val ben = Player(2, "Ben")
    private val cleo = Player(3, "Cleo")

    private fun game(penalty: EnderPenalty = EnderPenalty.OFFICIAL, target: Int = SKYJO_TARGET) =
        GameState.newGame(listOf(anna, ben, cleo), target = target, kind = GameKind.SKYJO, enderPenalty = penalty)

    private fun grid(vararg values: Int) = values.mapIndexed { i, v -> ConfirmedCard(SkyjoCard.of(v), slot = i) }

    /** Scores Anna, Ben, Cleo by points, sets the round ender and closes the round. */
    private fun GameState.playRound(a: Int, b: Int, c: Int, endedBy: Long? = null): GameState =
        scorePoints(1, a, 1).scorePoints(2, b, 1).scorePoints(3, c, 1).withRoundEnder(endedBy, 1).closeRound(1)

    @Test
    fun cards_labelsRoundTrip() {
        assertEquals(15, SkyjoCard.entries.size)
        SkyjoCard.entries.forEach { assertEquals(it, SkyjoRules.cardFromLabel(it.label)) }
        assertEquals(SkyjoCard.M2, SkyjoCard.fromLabel("-2"))
        // Flip 7's labels are not Skyjo cards (the sets are looked up per game).
        assertEquals(null, SkyjoRules.cardFromLabel("00"))
    }

    @Test
    fun score_isTheSumOfTheGrid() {
        assertEquals(0, SkyjoRules.score(emptyList()).points)
        assertEquals(13, SkyjoRules.score(grid(-2, -1, 0, 12, 4)).points)
        assertFalse(SkyjoRules.score(grid(12, 12, 12)).bust)
    }

    @Test(expected = IllegalArgumentException::class)
    fun score_atMostTwelveCards() {
        SkyjoRules.score(grid(*IntArray(13) { 1 }))
    }

    @Test
    fun scoreCards_storesTheGridAndItsSum() {
        val g = game().scoreCards(1, grid(5, -2, 3), ResultSource.MANUAL, now = 5)
        val r = g.result(1)
        assertEquals(ResultStatus.SCORED, r.status)
        assertEquals(6, r.points)
        assertEquals(listOf(0, 1, 2), r.cards.map { it.slot })
        assertEquals(null, r.breakdown)
    }

    @Test(expected = IllegalArgumentException::class)
    fun scoreCards_rejectsFlip7Cards() {
        game().scoreCards(1, listOf(ConfirmedCard(Card.N5)), ResultSource.MANUAL, now = 5)
    }

    @Test
    fun scorePoints_negativeAllowed() {
        assertEquals(-5, game().scorePoints(1, -5, 1).total(1))
    }

    @Test(expected = IllegalStateException::class)
    fun bust_notInSkyjo() {
        game().bust(1, 1)
    }

    @Test
    fun lowestWins_leadersWinnersAndRanks() {
        val start = game(target = 50)
        assertEquals(emptyList<Long>(), start.leaders)
        val g = start.playRound(10, 3, 20).nextRound().playRound(30, 4, 31)
        assertEquals(mapOf(1L to 40, 2L to 7, 3L to 51), g.totals)
        assertTrue(g.isOver)
        assertEquals(listOf(2L), g.winners)
        assertEquals(listOf(2L), g.leaders)
        assertEquals(mapOf(1L to 2, 2L to 1, 3L to 3), g.standings())
        assertEquals(mapOf(1L to 2, 2L to 1, 3L to 3), g.roundRanks(2))
    }

    @Test
    fun leaders_lowestWins_onlyByClosedRounds() {
        val g = game().scorePoints(1, 10, 1)
        // Mid round 1 nobody leads (Ben's and Cleo's open 0 is no lead).
        assertEquals(emptyList<Long>(), g.leaders)
        val closed = g.scorePoints(2, 4, 1).scorePoints(3, 6, 1).closeAndStartNext(2)
        assertEquals(listOf(2L), closed.leaders)
        // Points of the running round don't move the lead until it is closed.
        assertEquals(listOf(2L), closed.scorePoints(1, -10, 3).leaders)
    }

    @Test
    fun roundRanks_openPlayersLast() {
        val g = game().scorePoints(1, 10, 1)
        assertEquals(mapOf(1L to 1, 2L to 2, 3L to 2), g.roundRanks())
    }

    @Test
    fun closeRound_openPlayersScoreZero_reopenOpensThem() {
        val g = game().scorePoints(1, 7, 1).closeRound(2)
        val r = g.result(2, round = 1)
        assertEquals(ResultStatus.SCORED, r.status)
        assertEquals(0, r.points)
        assertEquals(ResultSource.CLOSE_ROUND, r.source)
        assertTrue(g.reopenRound(3).result(2).isOpen)
    }

    @Test
    fun official_enderNotLowestIsDoubled() {
        val g = game().playRound(10, 5, 8, endedBy = 1)
        assertTrue(g.isDoubled(g.round(1), 1))
        assertEquals(20, g.total(1))
        assertEquals(20, g.roundPoints(1, 1))
        assertEquals(10, g.round(1).points(1))
    }

    @Test
    fun official_strictlyLowestEnderIsNotDoubled_aTieIs() {
        assertEquals(4, game().playRound(4, 5, 8, endedBy = 1).total(1))
        assertEquals(10, game().playRound(5, 5, 8, endedBy = 1).total(1))
    }

    @Test
    fun official_nonPositiveEnderIsNotDoubled() {
        assertEquals(-3, game().playRound(-3, -5, 8, endedBy = 1).total(1))
        assertEquals(0, game().playRound(0, -5, 8, endedBy = 1).total(1))
    }

    @Test
    fun always_doublesANegativeScoreToo() {
        assertEquals(-6, game(EnderPenalty.ALWAYS).playRound(-3, -5, 8, endedBy = 1).total(1))
        assertEquals(4, game(EnderPenalty.ALWAYS).playRound(4, 5, 8, endedBy = 1).total(1))
    }

    @Test
    fun crazy_enderGetsDoubleTheHighestScore() {
        // Anna ended with 10, Cleo had the round's highest 23: Anna counts 46.
        val g = game(EnderPenalty.CRAZY).playRound(10, 5, 23, endedBy = 1)
        assertTrue(g.isDoubled(g.round(1), 1))
        assertEquals(46, g.total(1))
        assertEquals(23, g.total(3))
        // Highest herself: double her own.
        assertEquals(60, game(EnderPenalty.CRAZY).playRound(30, 5, 23, endedBy = 1).total(1))
        // Strictly lowest: no penalty; also a negative ender who isn't lowest gets double the highest.
        assertEquals(4, game(EnderPenalty.CRAZY).playRound(4, 5, 23, endedBy = 1).total(1))
        assertEquals(46, game(EnderPenalty.CRAZY).playRound(-3, -5, 23, endedBy = 1).total(1))
    }

    @Test
    fun off_neverDoubles() {
        assertEquals(10, game(EnderPenalty.OFF).playRound(10, 5, 8, endedBy = 1).total(1))
    }

    @Test
    fun noEnder_noPenalty() {
        assertEquals(10, game().playRound(10, 5, 8).total(1))
    }

    @Test
    fun ender_changedOnAClosedRound_recomputesTotalsAndGameOver() {
        val g = game(target = 15).playRound(10, 5, 8)
        assertFalse(g.isOver)
        val doubled = g.withRoundEnder(1, now = 9, round = 1)
        assertEquals(20, doubled.total(1))
        assertTrue(doubled.isOver)
        assertEquals(listOf(2L), doubled.winners)
        assertFalse(doubled.withRoundEnder(null, now = 10, round = 1).isOver)
    }

    @Test
    fun ender_droppedWhenTheyLeave() {
        val g = game().scorePoints(1, 3, 1).withRoundEnder(1, 1)
        val left = g.withLineup(g.seats.filter { it.playerId != 1L }, 2)
        assertEquals(null, left.currentRound.endedBy)
    }

    @Test(expected = IllegalStateException::class)
    fun ender_notInFlip7() {
        GameState.newGame(listOf(anna, ben)).withRoundEnder(1, 1)
    }
}
