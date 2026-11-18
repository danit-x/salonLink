package com.group12.salonlink.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [SalonTime]. They run on the JVM in milliseconds: no emulator needed.
 * Test names describe the expected behaviour, so a failing test explains itself.
 */
class SalonTimeTest {

    // The Figma booking: Thursday 8 Oct 2026, 5:30 pm – 6:20 pm in Colombo.
    private val date = LocalDate.of(2026, 10, 8)
    private val start = SalonTime.toInstant(date, LocalTime.of(17, 30))
    private val end = SalonTime.toInstant(date, LocalTime.of(18, 20))

    @Test
    fun `salon local time is stored as UTC minus five and a half hours`() {
        assertEquals(Instant.parse("2026-10-08T12:00:00Z"), start)
    }

    @Test
    fun `times are formatted like the Figma design`() {
        assertEquals("5:30 pm", SalonTime.formatTime(start))
        assertEquals("Thu, 8 Oct", SalonTime.formatShortDate(start))
        assertEquals("Thursday, 8 October 2026", SalonTime.formatLongDate(start))
        assertEquals("5:30 pm – 6:20 pm", SalonTime.formatRange(start, end))
    }

    @Test
    fun `a late-night booking stays on the salon's calendar day, not UTC's`() {
        // 11:30 pm in Colombo is 6:00 pm UTC on the same day...
        val lateNight = SalonTime.toInstant(date, LocalTime.of(23, 30))
        assertEquals(date, SalonTime.localDateOf(lateNight))
        // ...and 1:00 am in Colombo is still the *previous* day in UTC.
        val earlyMorning = SalonTime.toInstant(date, LocalTime.of(1, 0))
        assertEquals(date, SalonTime.localDateOf(earlyMorning))
    }

    @Test
    fun `greeting changes with the time of day`() {
        assertEquals(SalonTime.Greeting.MORNING, SalonTime.greetingFor(LocalTime.of(9, 0)))
        assertEquals(SalonTime.Greeting.AFTERNOON, SalonTime.greetingFor(LocalTime.of(13, 15)))
        assertEquals(SalonTime.Greeting.EVENING, SalonTime.greetingFor(LocalTime.of(19, 45)))
    }
}
