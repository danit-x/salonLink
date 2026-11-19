package com.group12.salonlink.domain.reminder

import com.group12.salonlink.domain.model.BookedService
import com.group12.salonlink.domain.model.Booking
import com.group12.salonlink.domain.model.BookingStatus
import com.group12.salonlink.domain.model.BookingType
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which local reminders exist for which bookings (FR-07). */
class ReminderPlannerTest {

    private val now = Instant.parse("2026-12-03T04:30:00Z") // Thu 10:00 Colombo

    private fun booking(id: String, hoursAhead: Long, status: BookingStatus = BookingStatus.BOOKED): Booking {
        val start = now.plus(Duration.ofHours(hoursAhead))
        return Booking(
            id = id, salonId = "aura", customerId = "kavindu", customerName = "Kavindu Perera",
            staffId = "kasun", staffName = "Kasun Perera",
            services = listOf(BookedService("haircut", "Haircut", 30, 1500)),
            start = start, end = start.plusSeconds(1800), totalPrice = 1500,
            status = status, type = BookingType.APPOINTMENT,
        )
    }

    @Test
    fun `a booking in three days gets a day-before and a two-hours-before reminder`() {
        val plan = ReminderPlanner.plan(listOf(booking("b1", 72)), now)
        assertEquals(listOf(ReminderKind.DAY_BEFORE, ReminderKind.SOON), plan.schedule.map { it.kind })
        assertEquals(now.plus(Duration.ofHours(48)), plan.schedule.first().fireAt)
        assertTrue(plan.cancel.isEmpty())
    }

    @Test
    fun `a booking in five hours only gets the two-hour reminder`() {
        val plan = ReminderPlanner.plan(listOf(booking("b1", 5)), now)
        assertEquals(listOf(ReminderKind.SOON), plan.schedule.map { it.kind })
        assertEquals(listOf("reminder-b1-day_before"), plan.cancel)
    }

    @Test
    fun `cancelled and finished bookings lose their reminders`() {
        val plan = ReminderPlanner.plan(
            listOf(booking("c", 72, BookingStatus.CANCELLED), booking("d", -3, BookingStatus.COMPLETED)),
            now,
        )
        assertTrue(plan.schedule.isEmpty())
        assertEquals(4, plan.cancel.size)
    }
}
