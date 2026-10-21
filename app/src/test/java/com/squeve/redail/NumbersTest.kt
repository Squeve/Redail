package com.squeve.redail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NumbersTest {
    @Test
    fun validNumbersNeedBetweenThreeAndFifteenDigits() {
        assertFalse(isValidNumber("12"))
        assertTrue(isValidNumber("112"))
        assertTrue(isValidNumber("+254712345678"))
        assertFalse(isValidNumber("1234567890123456"))
    }

    @Test
    fun localNumbersGetTheCountryCode() {
        assertEquals("+254712345678", normalizeNumber("0712345678", "254"))
        assertEquals("0712345678", normalizeNumber("0712345678", ""))
        assertEquals("0044123456", normalizeNumber("0044123456", "254"))
        assertEquals("+254712345678", normalizeNumber("+254712345678", "254"))
    }

    @Test
    fun duplicatesAreDetectedByTheLastNineDigits() {
        assertEquals(digitsKey("+254712345678"), digitsKey("0712345678"))
    }

    @Test
    fun numbersAreExtractedFromFreeText() {
        val found = extractNumbers("Call +254 712 345 678 or 0722-111-222, ref 12345")
        assertEquals(listOf("+254712345678", "0722111222"), found)
    }

    @Test
    fun repeatedNumbersAreListedOnce() {
        assertEquals(listOf("0722111222"), extractNumbers("0722111222 and again 0722-111-222"))
    }
}
