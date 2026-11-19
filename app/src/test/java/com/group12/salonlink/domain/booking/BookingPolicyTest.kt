package com.group12.salonlink.domain.booking

import com.group12.salonlink.domain.model.BookedService
import com.group12.salonlink.domain.model.Booking
import com.group12.salonlink.domain.model.BookingStatus
import com.group12.salonlink.domain.model.BookingType
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The 24-hour customer cancellation window (same rule as firestore.rules). */
class BookingPolicyTest {
    private val now = Instant.parse("2026-12-03T04:30:00Z")
    private fun at(hours: Long, status: BookingStatus = BookingStatus.BOOKED): Booking {
        val start = now.plus(Duration.ofHours(hours))
        return Booking("b", "aura", "kavindu", "Kavindu", "kasun", "Kasun", listOf(BookedService("h", "Haircut", 30, 1500)),
            start, start.plusSeconds(1800), 1500, status, BookingType.APPOINTMENT)
    }

    @Test
    fun `cancel allowed more than 24 h ahead`() = assertTrue(BookingPolicy.customerCanCancel(at(25), now))

    @Test
    fun `not inside the 24 h window`() = assertFalse(BookingPolicy.customerCanCancel(at(23), now))

    @Test
    fun `exactly 24 h before is already too late`() = assertFalse(BookingPolicy.customerCanCancel(at(24), now))

    @Test
    fun `not after check-in`() = assertFalse(BookingPolicy.customerCanCancel(at(48, BookingStatus.CHECKED_IN), now))
}
