package com.example

import com.example.calendar.LunarCalendarHelper
import org.junit.Assert.assertEquals
import org.junit.Test

class LunarCalendarUnitTest {

    @Test
    fun testLunarConversion() {
        // Test July 31, 2026 -> 18th day of 6th Lunar month
        val lunar = LunarCalendarHelper.convertSolarToLunar(31, 7, 2026)
        assertEquals(18, lunar.day)
        assertEquals(6, lunar.month)
        assertEquals(2026, lunar.year)
        assertEquals("Bính Ngọ", lunar.yearCanChi)
    }
}
