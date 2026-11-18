package com.group12.salonlink.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SalonModelsTest {

    private val nineToSeven = TimeRange(LocalTime.of(9, 0), LocalTime.of(19, 0))

    @Test
    fun `closing time is exclusive`() {
        assertTrue(LocalTime.of(9, 0) in nineToSeven)
        assertTrue(LocalTime.of(18, 59) in nineToSeven)
        assertFalse(LocalTime.of(19, 0) in nineToSeven)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a time range must not be reversed`() {
        TimeRange(LocalTime.of(19, 0), LocalTime.of(9, 0))
    }

    @Test
    fun `days without hours are closed`() {
        val hours = WeeklyHours(mapOf(DayOfWeek.THURSDAY to nineToSeven))
        val thursday = LocalDate.of(2026, 10, 8)
        assertEquals(nineToSeven, hours.hoursOn(thursday))
        assertNull(hours.hoursOn(thursday.plusDays(3))) // Sunday
        assertFalse(WeeklyHours.CLOSED.isOpenOn(thursday))
    }

    @Test
    fun `a service with no staff list can be done by anyone`() {
        val open = SalonService("s", "Haircut", "", ServiceCategory.HAIRCUT, 30, 1500)
        val kasunOnly = open.copy(staffIds = listOf("kasun"))
        assertTrue(open.isOfferedBy("nadeesha"))
        assertTrue(kasunOnly.isOfferedBy("kasun"))
        assertFalse(kasunOnly.isOfferedBy("nadeesha"))
    }

    @Test
    fun `blocks overlap only when the intervals really intersect`() {
        val block = StaffBlock("b", Instant.parse("2026-10-08T07:30:00Z"), Instant.parse("2026-10-08T08:00:00Z"), "Lunch")
        assertTrue(block.overlaps(Instant.parse("2026-10-08T07:50:00Z"), Instant.parse("2026-10-08T08:20:00Z")))
        // Touching edges is not an overlap: a booking may start the moment lunch ends.
        assertFalse(block.overlaps(Instant.parse("2026-10-08T08:00:00Z"), Instant.parse("2026-10-08T08:30:00Z")))
        assertFalse(block.overlaps(Instant.parse("2026-10-08T07:00:00Z"), Instant.parse("2026-10-08T07:30:00Z")))
    }
}
