package net.moesli.scan7.scoring

import net.moesli.scan7.scoring.Card.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreRowTest {
    @Test
    fun emptyRow_isZero() {
        val s = scoreRow(emptyList())
        assertEquals(RowScore(0, false, 0, 0, 0, false, emptyList()), s)
    }

    // ---- Second Chance cancels a duplicate ----

    @Test
    fun secondChance_userRow_cancelsDuplicate() {
        val s = scoreRow(listOf(N5, N10, N7, N7, N12, FREEZE, SECOND_CHANCE))
        assertFalse(s.bust)
        assertEquals(34, s.total)
        assertEquals(34, s.numberSum)
        assertEquals(1, s.secondChanceUsed)
        assertEquals(listOf(7), s.cancelledDuplicates)
        assertEquals(emptyList<Int>(), s.duplicatedNumbers)
        assertEquals("Second Chance used · 7 counted once", s.secondChanceText())
    }

    @Test
    fun secondChance_twoDuplicatesOneSecondChance_bust() {
        val s = scoreRow(listOf(N7, N7, N12, N12, SECOND_CHANCE))
        assertTrue(s.bust)
        assertEquals(0, s.total)
        assertEquals(listOf(7, 12), s.duplicatedNumbers)
        assertEquals(0, s.secondChanceUsed)
        assertEquals(null, s.secondChanceText())
    }

    @Test
    fun secondChance_oneDuplicateTwoSecondChances_noBust() {
        val s = scoreRow(listOf(N3, N7, N7, SECOND_CHANCE, SECOND_CHANCE))
        assertFalse(s.bust)
        assertEquals(10, s.total)
        assertEquals(1, s.secondChanceUsed)
    }

    @Test
    fun secondChance_tripleSevenOneSecondChance_bust() {
        val s = scoreRow(listOf(N7, N7, N7, N2, SECOND_CHANCE))
        assertTrue(s.bust)
        assertEquals(listOf(7), s.duplicatedNumbers)
    }

    @Test
    fun secondChance_leftOut_bust() {
        // A left-out (excluded) Second Chance isn't passed to scoreRow.
        val s = scoreRow(listOf(N5, N10, N7, N7, N12, FREEZE))
        assertTrue(s.bust)
    }

    @Test
    fun secondChance_flip7CountsDistinct() {
        val s = scoreRow(listOf(N1, N2, N3, N4, N5, N6, N6, SECOND_CHANCE))
        assertFalse(s.bust)
        assertEquals(0, s.flip7Bonus) // 6 different numbers
        assertEquals(21, s.total)
        assertEquals(15, scoreRow(listOf(N1, N2, N3, N4, N5, N6, N7, N7, SECOND_CHANCE)).flip7Bonus)
    }

    @Test
    fun plainSum() {
        val s = scoreRow(listOf(N3, N7, N12))
        assertEquals(22, s.numberSum)
        assertEquals(22, s.total)
        assertFalse(s.bust)
        assertFalse(s.timesTwo)
        assertEquals(0, s.flip7Bonus)
    }

    @Test
    fun zeroCountsAsNumber_butAddsNothing() {
        assertEquals(5, scoreRow(listOf(N0, N5)).total)
        assertEquals(0, scoreRow(listOf(N0)).total)
    }

    @Test
    fun duplicate_busts() {
        val s = scoreRow(listOf(N4, N9, N4))
        assertTrue(s.bust)
        assertEquals(0, s.total)
        assertEquals(listOf(4), s.duplicatedNumbers)
        assertEquals(17, s.numberSum)
    }

    @Test
    fun multipleDuplicates_reportedSorted() {
        val s = scoreRow(listOf(N11, N2, N11, N2, N2, N5))
        assertTrue(s.bust)
        assertEquals(listOf(2, 11), s.duplicatedNumbers)
    }

    @Test
    fun duplicateZero_busts() {
        val s = scoreRow(listOf(N0, N0))
        assertTrue(s.bust)
        assertEquals(listOf(0), s.duplicatedNumbers)
    }

    @Test
    fun bust_overridesModifiersTimesTwoAndBonus() {
        val s = scoreRow(listOf(N0, N1, N2, N3, N4, N5, N6, N6, TIMES_2, PLUS_10, PLUS_4))
        assertTrue(s.bust)
        assertEquals(0, s.total)
        assertEquals(listOf(6), s.duplicatedNumbers)
    }

    @Test
    fun timesTwo_doublesNumbersOnly() {
        val s = scoreRow(listOf(N5, N8, TIMES_2))
        assertTrue(s.timesTwo)
        assertEquals(13, s.numberSum)
        assertEquals(26, s.total)
    }

    @Test
    fun plusModifiers_addedAfterTimesTwo() {
        val s = scoreRow(listOf(N5, N8, TIMES_2, PLUS_6, PLUS_2))
        assertEquals(8, s.plusSum)
        assertEquals(26 + 8, s.total)
    }

    @Test
    fun allPlusModifiers() {
        val s = scoreRow(listOf(N1, PLUS_2, PLUS_4, PLUS_6, PLUS_8, PLUS_10))
        assertEquals(30, s.plusSum)
        assertEquals(31, s.total)
    }

    @Test
    fun secondTimesTwo_doesNotStack() {
        assertEquals(20, scoreRow(listOf(N10, TIMES_2, TIMES_2)).total)
    }

    @Test
    fun duplicatePlusModifier_countedOnce() {
        val s = scoreRow(listOf(N3, PLUS_4, PLUS_4))
        assertFalse(s.bust)
        assertEquals(4, s.plusSum)
        assertEquals(7, s.total)
    }

    @Test
    fun flip7_exactlySevenDistinctIncludingZero() {
        val s = scoreRow(listOf(N0, N1, N2, N3, N4, N5, N6))
        assertEquals(21, s.numberSum)
        assertEquals(FLIP_7_BONUS, s.flip7Bonus)
        assertEquals(36, s.total)
    }

    @Test
    fun sixDistinct_noBonus() {
        val s = scoreRow(listOf(N1, N2, N3, N4, N5, N6))
        assertEquals(0, s.flip7Bonus)
        assertEquals(21, s.total)
    }

    @Test
    fun moreThanSevenDistinct_stillBonus() {
        val s = scoreRow(listOf(N1, N2, N3, N4, N5, N6, N7, N8))
        assertEquals(15, s.flip7Bonus)
        assertEquals(36 + 15, s.total)
    }

    @Test
    fun flip7_withTimesTwoAndPlus() {
        val s = scoreRow(listOf(N0, N1, N2, N3, N4, N5, N6, TIMES_2, PLUS_4))
        assertEquals(21, s.numberSum)
        assertTrue(s.timesTwo)
        assertEquals(4, s.plusSum)
        assertEquals(15, s.flip7Bonus)
        assertEquals(61, s.total)
    }

    @Test
    fun actionsAndBack_ignored() {
        val base = scoreRow(listOf(N7, N9, PLUS_2))
        val withExtras = scoreRow(listOf(FREEZE, N7, FLIP_THREE, N9, SECOND_CHANCE, PLUS_2, BACK, BACK))
        assertEquals(base, withExtras)
        assertEquals(18, withExtras.total)
    }

    @Test
    fun onlyActionsAndBack_isZero() {
        assertEquals(0, scoreRow(listOf(FREEZE, FLIP_THREE, SECOND_CHANCE, BACK)).total)
    }

    @Test
    fun modifiersOnly_noNumbers() {
        val s = scoreRow(listOf(TIMES_2, PLUS_10, PLUS_2))
        assertEquals(0, s.numberSum)
        assertEquals(12, s.plusSum)
        assertEquals(12, s.total)
        assertFalse(s.bust)
    }

    @Test
    fun orderDoesNotMatter() {
        assertEquals(
            scoreRow(listOf(PLUS_4, N2, TIMES_2, N9)),
            scoreRow(listOf(N9, TIMES_2, N2, PLUS_4)),
        )
    }
}
