package com.example

import com.example.util.TimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testTimeFormatterDigitalClock() {
        // 5 seconds
        assertEquals("00:05", TimeFormatter.formatDigitalClock(5000L))
        // 1 minute 15 seconds
        assertEquals("01:15", TimeFormatter.formatDigitalClock(75000L))
        // 2 hours 30 minutes 45 seconds
        assertEquals("02:30:45", TimeFormatter.formatDigitalClock((2 * 3600 + 30 * 60 + 45) * 1000L))
    }

    @Test
    fun testTimeFormatterDurationChinese() {
        val secondsOnly = TimeFormatter.formatDurationChinese(42000L)
        assertTrue(secondsOnly.contains("42秒"))

        val minAndSec = TimeFormatter.formatDurationChinese(85000L)
        assertTrue(minAndSec.contains("1分") && minAndSec.contains("25秒"))

        val hoursMinSec = TimeFormatter.formatDurationChinese((3 * 3600 + 15 * 60 + 20) * 1000L)
        assertTrue(hoursMinSec.contains("3小时") && hoursMinSec.contains("15分") && hoursMinSec.contains("20秒"))
    }

    @Test
    fun testTimeFormatterCompact() {
        assertEquals("45s", TimeFormatter.formatDurationCompact(45000L))
        assertEquals("3m 12s", TimeFormatter.formatDurationCompact((3 * 60 + 12) * 1000L))
        assertEquals("2h 15m", TimeFormatter.formatDurationCompact((2 * 3600 + 15 * 60 + 10) * 1000L))
    }
}

