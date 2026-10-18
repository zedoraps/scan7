package net.moesli.scan7.ui.scan

import net.moesli.scan7.scoring.Card
import net.moesli.scan7.scoring.Card.BACK
import net.moesli.scan7.scoring.Card.FREEZE
import net.moesli.scan7.scoring.Card.N0
import net.moesli.scan7.scoring.Card.N1
import net.moesli.scan7.scoring.Card.N10
import net.moesli.scan7.scoring.Card.N11
import net.moesli.scan7.scoring.Card.N12
import net.moesli.scan7.scoring.Card.N2
import net.moesli.scan7.scoring.Card.N3
import net.moesli.scan7.scoring.Card.N4
import net.moesli.scan7.scoring.Card.N5
import net.moesli.scan7.scoring.Card.N6
import net.moesli.scan7.scoring.Card.N7
import net.moesli.scan7.scoring.Card.N8
import net.moesli.scan7.scoring.Card.N9
import net.moesli.scan7.scoring.Card.PLUS_2
import net.moesli.scan7.scoring.Card.PLUS_4
import net.moesli.scan7.scoring.Card.PLUS_6
import net.moesli.scan7.scoring.Card.SECOND_CHANCE
import net.moesli.scan7.scoring.Card.TIMES_2
import net.moesli.scan7.scoring.scoreRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanLogicTest {
    @Test
    fun chipLabels() {
        assertEquals(listOf("0", "7", "12", "+4", "+10", "×2", "Freeze", "Flip 3", "2nd chance", "Back"),
            listOf(N0, N7, N12, PLUS_4, Card.PLUS_10, TIMES_2, FREEZE, Card.FLIP_THREE, SECOND_CHANCE, BACK).map { it.chipLabel() })
        assertEquals("plus 4", PLUS_4.spokenLabel())
        assertEquals("times 2", TIMES_2.spokenLabel())
    }

    @Test
    fun splitSortsAndSeparatesIgnored() {
        val split = splitCards(listOf(PLUS_4, FREEZE, N12, TIMES_2, N3, BACK, N7))
        assertEquals(listOf(N3, N7, N12, TIMES_2, PLUS_4), split.scoring)
        assertEquals(listOf(FREEZE, BACK), split.ignored)
        assertTrue(splitCards(emptyList()).isEmpty)
    }

    @Test
    fun breakdownMatchesHandoffExample() {
        val cards = listOf(N7, N12, N3, TIMES_2, PLUS_4)
        assertEquals("3 + 7 + 12 = 22 · ×2 = 44 · +4", breakdownText(cards))
    }

    @Test
    fun breakdownWithFlip7AndSeveralPlus() {
        val cards = listOf(N0, N1, N2, N3, N4, N5, N6, PLUS_6, PLUS_2, PLUS_2, SECOND_CHANCE)
        assertEquals(listOf("0 + 1 + 2 + 3 + 4 + 5 + 6 = 21", "+2 +6", "Flip 7 +15"), breakdownParts(cards))
        assertEquals(21 + 8 + 15, scoreRow(cards).total)
    }

    @Test
    fun breakdownEdgeCases() {
        assertEquals("7", breakdownText(listOf(N7)))
        assertEquals("7 · ×2 = 14", breakdownText(listOf(TIMES_2, N7)))
        assertEquals("+4", breakdownText(listOf(PLUS_4)))
        assertEquals(NO_SCORING_CARDS, breakdownText(listOf(FREEZE)))
        assertEquals(NO_SCORING_CARDS, breakdownText(emptyList()))
    }

    @Test
    fun duplicatesAndSummary() {
        val cards = listOf(N3, N7, N12, N7, N6, TIMES_2)
        val dups = duplicateCounts(cards)
        assertEquals(mapOf(7 to 2), dups)
        assertEquals("Two 7s", duplicateSummary(dups))
        assertTrue(isDuplicate(N7, dups))
        assertFalse(isDuplicate(N3, dups))
        assertFalse(isDuplicate(TIMES_2, dups))
        assertEquals("Two 3s, three 10s", duplicateSummary(duplicateCounts(listOf(N10, N3, N10, N3, N10))))
        assertEquals("", duplicateSummary(duplicateCounts(listOf(N3, PLUS_4, PLUS_4))))
    }

    @Test
    fun totalIgnoringBust() {
        val score = scoreRow(listOf(N3, N7, N12, N7, N6, TIMES_2))
        assertTrue(score.bust)
        assertEquals(0, score.total)
        assertEquals(70, score.totalIgnoringBust())
    }

    @Test
    fun reviewVariants() {
        assertEquals(ReviewVariant.Empty, reviewVariant(emptyList()))
        assertEquals(ReviewVariant.Scored, reviewVariant(listOf(FREEZE)))
        assertEquals(ReviewVariant.Scored, reviewVariant(listOf(N7, N8)))
        assertEquals(ReviewVariant.Bust, reviewVariant(listOf(N7, N7)))
    }

    @Test
    fun reviewOutcomes() {
        assertEquals(
            ScanOutcome.Scored(listOf(N8, N9, TIMES_2), listOf(BACK), ScanSource.SCAN),
            reviewOutcome(listOf(TIMES_2, BACK, N9, N8)),
        )
        assertEquals(ScanOutcome.Bust(listOf(N7, N7), listOf(FREEZE)), reviewOutcome(listOf(N7, FREEZE, N7)))
    }

    @Test
    fun toggleAddsOnceAndRemoves() {
        var sel = toggleCard(emptyList(), N11)
        sel = toggleCard(sel, PLUS_4)
        sel = toggleCard(sel, N2)
        assertEquals(listOf(N2, N11, PLUS_4), sel)
        sel = toggleCard(sel, N11)
        assertEquals(listOf(N2, PLUS_4), sel)
        // Toggling a duplicated (prefilled) number removes every copy.
        assertEquals(listOf(N3), toggleCard(listOf(N3, N7, N7), N7))
    }

    @Test
    fun removeChipRemovesOnlyThatCopy() {
        assertEquals(listOf(N3, N7), removeChipAt(listOf(N3, N7, N7), 2))
    }

    @Test
    fun manualStartCollapsesModifiersKeepsDuplicateNumbers() {
        val start = manualStart(listOf(PLUS_4, N7, FREEZE, N7, PLUS_4, TIMES_2, TIMES_2))
        assertEquals(listOf(N7, N7, TIMES_2, PLUS_4), start.scoring)
        assertEquals(listOf(FREEZE), start.ignored)
    }

    @Test
    fun manualGrid() {
        assertEquals(13, ManualNumbers.size)
        assertEquals(listOf("+2", "+4", "+6", "+8", "+10", "×2"), ManualModifiers.map { it.chipLabel() })
    }

    @Test
    fun manualOutcomes() {
        assertEquals(
            ScanOutcome.Scored(listOf(N2, PLUS_4), listOf(FREEZE), ScanSource.MANUAL),
            manualOutcome(listOf(PLUS_4, N2), listOf(FREEZE)),
        )
        assertEquals(ScanOutcome.Bust(listOf(N7, N7), emptyList(), ScanSource.MANUAL), manualOutcome(listOf(N7, N7), emptyList()))
        assertEquals(ScanOutcome.Scored(emptyList(), emptyList(), ScanSource.MANUAL), manualOutcome(emptyList(), emptyList()))
    }

    // ---- Leaving cards out on the review ----

    private fun detected(vararg cards: Card) = cards.mapIndexed { i, c -> DetectedCard(i, c) }

    @Test
    fun toggleExcluded_addsAndRemoves() {
        val once = toggleExcluded(emptySet(), 2)
        assertEquals(setOf(2), once)
        assertEquals(emptySet<Int>(), toggleExcluded(once, 2))
    }

    @Test
    fun excludingNeighboursSeven_noBust() {
        val cards = detected(N7, N5, N7, PLUS_4)
        assertEquals(ReviewVariant.Bust, reviewVariant(cards, emptySet()))
        assertEquals(ReviewVariant.Scored, reviewVariant(cards, setOf(2)))
        val outcome = reviewOutcome(cards, setOf(2))
        assertEquals(ScanOutcome.Scored(listOf(N5, N7, PLUS_4), emptyList(), ScanSource.SCAN, excluded = setOf(2)), outcome)
        assertEquals(16, (outcome as ScanOutcome.Scored).score.total)
    }

    @Test
    fun excludedCards_notInScoreOrCards() {
        val cards = detected(N12, TIMES_2, FREEZE, N3)
        assertEquals(listOf(N12, FREEZE, N3), includedCards(cards, setOf(1)))
        val outcome = reviewOutcome(cards, setOf(1, 2)) as ScanOutcome.Scored
        assertEquals(listOf(N3, N12), outcome.cards)
        assertEquals(emptyList<Card>(), outcome.ignored)
        assertEquals(15, outcome.score.total)
        assertEquals(setOf(1, 2), outcome.excluded)
    }

    @Test
    fun allExcluded_scoredZeroNotEmpty() {
        val cards = detected(N7)
        assertEquals(ReviewVariant.Scored, reviewVariant(cards, setOf(0)))
        assertEquals(ReviewVariant.Empty, reviewVariant(emptyList(), emptySet()))
        assertEquals(0, (reviewOutcome(cards, setOf(0)) as ScanOutcome.Scored).score.total)
    }

    @Test
    fun reviewChips_looksAndOrder() {
        val chips = reviewChips(detected(FREEZE, N7, N7, N2), setOf(2))
        assertEquals(listOf(N2, N7, N7, FREEZE), chips.map { it.card })
        assertEquals(listOf(ChipLook.Normal, ChipLook.Normal, ChipLook.Excluded, ChipLook.Ignored), chips.map { it.look })
        val bust = reviewChips(detected(N7, N7), emptySet())
        assertEquals(listOf(ChipLook.Duplicate, ChipLook.Duplicate), bust.map { it.look })
    }

    @Test
    fun withExcluded_keepsCards() {
        val manual = manualOutcome(listOf(N2), emptyList()).withExcluded(setOf(4))
        assertEquals(ScanOutcome.Scored(listOf(N2), emptyList(), ScanSource.MANUAL, excluded = setOf(4)), manual)
    }

    @Test
    fun pickBox_smallestContainingWins() {
        val big = HitBox(0, 0f, 0f, 300f, 400f)
        val small = HitBox(1, 100f, 100f, 200f, 250f)
        assertEquals(1, pickBox(listOf(big, small), 150f, 150f, minSize = 48f))
        assertEquals(0, pickBox(listOf(big, small), 20f, 20f, minSize = 48f))
        assertEquals(null, pickBox(listOf(big, small), 500f, 500f, minSize = 48f))
    }

    @Test
    fun pickBox_inflatesSmallBoxes() {
        val tiny = HitBox(3, 100f, 100f, 110f, 110f) // 10 × 10 → 48 × 48 around its center
        assertEquals(3, pickBox(listOf(tiny), 125f, 125f, minSize = 48f))
        assertEquals(null, pickBox(listOf(tiny), 131f, 105f, minSize = 48f))
    }
}
