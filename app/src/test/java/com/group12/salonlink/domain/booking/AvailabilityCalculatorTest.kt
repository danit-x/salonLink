package com.group12.salonlink.domain.booking

import com.group12.salonlink.core.util.SalonTime
import com.group12.salonlink.domain.model.StaffBlock
import com.group12.salonlink.domain.model.TimeRange
import com.group12.salonlink.domain.model.WeeklyHours
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Availability on Thursday 3 Dec 2026 for a barber at a salon open 09:00–19:00.
 * Covers the logic behind proposal T02 (booking) and the P2 "blocked time" feature.
 */
class AvailabilityCalculatorTest {

    private val thursday = LocalDate.of(2026, 12, 3)
    private fun at(h: Int, m: Int = 0) = SalonTime.toInstant(thursday, LocalTime.of(h, m))
    private fun hours(from: Int, to: Int) =
        WeeklyHours(mapOf(DayOfWeek.THURSDAY to TimeRange(LocalTime.of(from, 0), LocalTime.of(to, 0))))

    private val base = DayAvailabilityInput(
        date = thursday,
        salonHours = hours(9, 19),
        staffHours = hours(9, 19),
        blocks = emptyList(),
        takenSlots = emptySet(),
        earliestStart = at(0), // midnight: nothing is "in the past" unless a test says so
    )

    @Test
    fun `an empty day offers every half hour that still fits before closing`() {
        val starts = AvailabilityCalculator.availableStarts(base, durationMin = 30)
        assertEquals(20, starts.size)               // 09:00, 09:30 … 18:30
        assertEquals(at(9), starts.first())
        assertEquals(at(18, 30), starts.last())     // ends exactly at 19:00: allowed
    }

    @Test
    fun `a 50-minute job cannot start at 18_30`() {
        val starts = AvailabilityCalculator.availableStarts(base, durationMin = 50)
        assertEquals(at(18), starts.last())
        assertEquals(SlotProblem.OUTSIDE_HOURS, AvailabilityCalculator.check(base, at(18, 30), 50))
    }

    @Test
    fun `working window is the overlap of salon and barber hours`() {
        val input = base.copy(staffHours = hours(12, 21))
        assertEquals(TimeRange(LocalTime.of(12, 0), LocalTime.of(19, 0)), AvailabilityCalculator.workingWindow(input))
        assertEquals(at(12), AvailabilityCalculator.availableStarts(input, 30).first())
    }

    @Test
    fun `barber's day off means no slots at all`() {
        val input = base.copy(staffHours = WeeklyHours.CLOSED)
        assertTrue(AvailabilityCalculator.availableStarts(input, 30).isEmpty())
        assertEquals(SlotProblem.CLOSED, AvailabilityCalculator.check(input, at(10), 30))
    }

    @Test
    fun `lunch block removes every start that would overlap it`() {
        val lunch = StaffBlock("lunch", at(13), at(14), "Lunch")
        val input = base.copy(blocks = listOf(lunch))
        val starts = AvailabilityCalculator.availableStarts(input, durationMin = 50)
        // 12:00 + 50 min = 12:50, fine. 12:30 + 50 = 13:20 overlaps lunch.
        assertTrue(at(12) in starts)
        assertFalse(at(12, 30) in starts)
        assertFalse(at(13) in starts)
        assertFalse(at(13, 30) in starts)
        assertTrue(at(14) in starts)
        assertEquals(SlotProblem.BLOCKED, AvailabilityCalculator.check(input, at(12, 30), 50))
    }

    @Test
    fun `taken slots from another booking are not offered`() {
        // Someone holds 17:30–18:20 (5 slots).
        val taken = SlotGrid.slotsBetween(at(17, 30), at(18, 20)).toSet()
        val input = base.copy(takenSlots = taken)
        val starts = AvailabilityCalculator.availableStarts(input, durationMin = 30)
        assertTrue(at(17) in starts)        // 17:00–17:30 ends right as theirs begins
        assertFalse(at(17, 30) in starts)
        assertFalse(at(18) in starts)       // 18:00–18:30 overlaps their last slot (18:10)
        assertEquals(SlotProblem.TAKEN, AvailabilityCalculator.check(input, at(18), 30))
        // But a free 10-minute-aligned start right after theirs is accepted when asked directly.
        assertNull(AvailabilityCalculator.check(input, at(18, 20), 30))
    }

    @Test
    fun `times before now are not offered`() {
        val input = base.copy(earliestStart = at(15, 10))
        val starts = AvailabilityCalculator.availableStarts(input, 30)
        assertEquals(at(15, 30), starts.first())
        assertEquals(SlotProblem.IN_PAST, AvailabilityCalculator.check(input, at(15), 30))
    }

    @Test
    fun `a start off the 10-minute grid is rejected`() {
        assertEquals(SlotProblem.NOT_ALIGNED, AvailabilityCalculator.check(base, at(10, 5), 30))
    }

    @Test
    fun `jobs longer than the slot limit are rejected`() {
        assertEquals(SlotProblem.TOO_LONG, AvailabilityCalculator.check(base, at(9), 170))
        assertNull(AvailabilityCalculator.check(base, at(9), 160))
    }

    @Test
    fun `opening hours off the grid start offering from the next boundary`() {
        val odd = WeeklyHours(mapOf(DayOfWeek.THURSDAY to TimeRange(LocalTime.of(9, 5), LocalTime.of(12, 0))))
        val starts = AvailabilityCalculator.availableStarts(base.copy(salonHours = odd), 30)
        assertEquals(at(9, 10), starts.first())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `step must be a multiple of the slot size`() {
        AvailabilityCalculator.availableStarts(base, 30, stepMin = 15)
    }
}
