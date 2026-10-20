package net.moesli.scan7.scoring

import net.moesli.scan7.scoring.Card.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Flip 7: With a Vengeance rows ([scoreRow] with the Vengeance cards). */
class VengeanceScoreTest {
    @Test
    fun divideTwo_thenMinus_roundedDown() {
        // The rule book's example: (13 + 11 + 8) ÷ 2 − 6 = 10.
        val s = scoreRow(listOf(N13, N11, N8, DIVIDE_2, MINUS_6))
        assertEquals(32, s.numberSum)
        assertTrue(s.divideTwo)
        assertEquals(6, s.minusSum)
        assertEquals(10, s.total)
        assertEquals(7, scoreRow(listOf(N5, N10, DIVIDE_2)).total) // 15 ÷ 2 = 7.5 → 7
    }

    @Test
    fun minusCards_neverBelowZero() {
        assertEquals(0, scoreRow(listOf(N3, MINUS_10)).total)
        assertEquals(0, scoreRow(listOf(MINUS_2)).total)
        assertEquals(1, scoreRow(listOf(N3, MINUS_2)).total)
        // Each minus card exists once: a repeated one is a scan artefact.
        assertEquals(10, scoreRow(listOf(N12, MINUS_2, MINUS_2)).total)
    }

    @Test
    fun flip7Bonus_afterTheModifiers_thenTheFloor() {
        val seven = listOf(N1, N2, N3, N4, N5, N6, N13)
        assertEquals(34 + 15, scoreRow(seven).total)
        // (34 ÷ 2) − 10 − 8 + 15 = 14.
        assertEquals(14, scoreRow(seven + listOf(DIVIDE_2, MINUS_10, MINUS_8)).total)
        // 17 − 30 + 15 = 2: the floor of 0 is applied last.
        assertEquals(2, scoreRow(seven + listOf(DIVIDE_2, MINUS_10, MINUS_8, MINUS_6, MINUS_4, MINUS_2)).total)
    }

    @Test
    fun lucky13_allowsASecond13_bothCountForFlip7() {
        val pair = scoreRow(listOf(LUCKY_13, N13, N5))
        assertFalse(pair.bust)
        assertTrue(pair.luckyPair)
        assertEquals(31, pair.total)
        // 6 different values + the second 13 = Flip 7.
        val flip7 = scoreRow(listOf(N1, N2, N3, N4, N5, N13, LUCKY_13))
        assertEquals(15, flip7.flip7Bonus)
        assertEquals(41 + 15, flip7.total)
        // A third 13 busts, and two plain 13s bust.
        assertTrue(scoreRow(listOf(LUCKY_13, N13, N13)).bust)
        assertEquals(listOf(13), scoreRow(listOf(LUCKY_13, N13, N13)).duplicatedNumbers)
        assertTrue(scoreRow(listOf(N13, N13)).bust)
        // Another duplicate still busts; the allowed 13s are not listed.
        val other = scoreRow(listOf(LUCKY_13, N13, N4, N4))
        assertTrue(other.bust)
        assertEquals(listOf(4), other.duplicatedNumbers)
        // A Lucky 13 alone is a 13.
        assertEquals(13, scoreRow(listOf(LUCKY_13)).total)
    }

    @Test
    fun theZero_scoresOnlyTheFlip7Bonus() {
        val zero = scoreRow(listOf(THE_ZERO, N12, N11, DIVIDE_2, MINUS_2))
        assertTrue(zero.zeroed)
        assertEquals(0, zero.total)
        // The Zero counts as a number for the Flip 7 (and as a 0 for duplicates).
        val flip7 = scoreRow(listOf(THE_ZERO, N1, N2, N3, N4, N5, N12, MINUS_10))
        assertEquals(15, flip7.flip7Bonus)
        assertEquals(15, flip7.total)
    }

    @Test
    fun unlucky7_isASeven() {
        assertEquals(7, scoreRow(listOf(UNLUCKY_7)).total)
        assertTrue(scoreRow(listOf(UNLUCKY_7, N7)).bust)
        assertEquals(listOf(7), scoreRow(listOf(UNLUCKY_7, N7)).duplicatedNumbers)
    }

    @Test
    fun actionCards_neverScore() {
        assertEquals(5, scoreRow(listOf(N5, JUST_ONE_MORE, SWAP, STEAL, DISCARD, FLIP_FOUR, BACK)).total)
    }
}
