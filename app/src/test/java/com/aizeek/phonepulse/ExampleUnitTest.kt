package com.aizeek.phonepulse

import com.aizeek.phonepulse.util.TimeFormatter
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

    @Test
    fun testTimeFormatterSingleUnit() {
        // Less than 60 seconds
        assertEquals("0秒", TimeFormatter.formatSingleUnit(0L))
        assertEquals("33秒", TimeFormatter.formatSingleUnit(33000L))
        assertEquals("59秒", TimeFormatter.formatSingleUnit(59999L))

        // 60 seconds to 59 minutes
        assertEquals("1分", TimeFormatter.formatSingleUnit(60000L))
        assertEquals("1分", TimeFormatter.formatSingleUnit(75000L))
        assertEquals("2分", TimeFormatter.formatSingleUnit(120000L))
        assertEquals("59分", TimeFormatter.formatSingleUnit(59 * 60 * 1000L))

        // Exact hours
        assertEquals("1小时", TimeFormatter.formatSingleUnit(60 * 60 * 1000L))
        assertEquals("2小时", TimeFormatter.formatSingleUnit(2 * 3600 * 1000L))

        // Under 2 hours with fraction: retains minute accuracy in single unit "分"
        assertEquals("75分", TimeFormatter.formatSingleUnit(75 * 60 * 1000L))
        assertEquals("90分", TimeFormatter.formatSingleUnit(90 * 60 * 1000L))

        // 2 hours and above: "小时"
        assertEquals("8小时", TimeFormatter.formatSingleUnit(8 * 3600 * 1000L))
        assertEquals("8小时", TimeFormatter.formatSingleUnit((8 * 3600 + 15 * 60) * 1000L))
    }
}

