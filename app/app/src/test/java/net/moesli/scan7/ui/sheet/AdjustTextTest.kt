package net.moesli.scan7.ui.sheet

import net.moesli.scan7.game.ScoreAdjustment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdjustTextTest {
    @Test
    fun deltaError_onlyForTypedInvalidInput() {
        assertNull(deltaError(""))
        assertNull(deltaError("+10"))
        assertNull(deltaError("-3"))
        assertEquals("Enter a number after the sign", deltaError("-"))
        assertEquals("Enter a number after the sign", deltaError("+"))
        assertEquals("Enter a number other than 0", deltaError("0"))
        assertEquals("Enter a number other than 0", deltaError("-0"))
        assertEquals("Up to 999 points", deltaError("1000"))
    }

    @Test
    fun parseDelta_acceptsSignedNumbers() {
        assertEquals(10, parseDelta("+10"))
        assertEquals(-12, parseDelta("-12"))
        assertEquals(-12, parseDelta("−12"))
        assertEquals(7, parseDelta(" 7 "))
        assertEquals(0, parseDelta("0"))
        assertEquals(999, parseDelta("999"))
    }

    @Test
    fun parseDelta_rejectsInvalid() {
        assertNull(parseDelta(""))
        assertNull(parseDelta("-"))
        assertNull(parseDelta("+"))
        assertNull(parseDelta("1-2"))
        assertNull(parseDelta("abc"))
        assertNull(parseDelta("1000"))
        assertNull(parseDelta("--3"))
    }

    @Test
    fun filterDeltaInput_keepsOneLeadingSignAndThreeDigits() {
        assertEquals("-12", filterDeltaInput("-12"))
        assertEquals("-12", filterDeltaInput("−12"))
        assertEquals("+10", filterDeltaInput("+1a0"))
        assertEquals("123", filterDeltaInput("12345"))
        assertEquals("12", filterDeltaInput("1-2"))
        assertEquals("", filterDeltaInput(""))
    }

    @Test
    fun stepper_andSign() {
        assertEquals("+10", deltaInput(stepDelta(0, 10)))
        assertEquals("-5", deltaInput(stepDelta(5, -10)))
        assertEquals("", deltaInput(stepDelta(1, -1)))
        assertEquals(999, stepDelta(995, 10))
        assertEquals(-999, stepDelta(-995, -10))
        assertEquals("-10", flipSign("+10"))
        assertEquals("-7", flipSign("7"))
        assertEquals("3", flipSign("-3"))
        assertEquals("-", flipSign(""))
        assertEquals("", flipSign("-"))
    }

    @Test
    fun display() {
        assertEquals("+10", formatDelta(10))
        assertEquals("−12", formatDelta(-12))
        assertEquals("Total 123 → 111", adjustPreview(123, -12))
        assertEquals("Add 5", stepDescription(5))
        assertEquals("Subtract 10", stepDescription(-10))
        val a = ScoreAdjustment(1, 1, -12, "penalty", 2)
        assertEquals("penalty · round 2", adjustmentDetail(a))
        assertEquals("round 2", adjustmentDetail(a.copy(note = null)))
        assertEquals("minus 12, penalty, round 2", adjustmentDescription(a))
        assertEquals("plus 3, round 2", adjustmentDescription(a.copy(delta = 3, note = null)))
    }
}
