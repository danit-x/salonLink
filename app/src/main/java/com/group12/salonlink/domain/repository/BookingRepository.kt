package com.group12.salonlink.domain.repository

import com.group12.salonlink.domain.model.Booking
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Read access to bookings. Flows fail with [com.group12.salonlink.domain.model.DataException].
 *
 * Creating and cancelling bookings is deliberately NOT here yet: those need the
 * double-booking-safe transaction of the booking engine (Phase 3, PLAN.md §5).
 *
 * Security rules decide who sees what: a customer only their own bookings, staff and owners
 * only their own salon's bookings (proposal T09).
 */
interface BookingRepository {

    /** One booking, or null if it doesn't exist. */
    fun observeBooking(salonId: String, bookingId: String): Flow<Booking?>

    /** All of a customer's bookings in every salon, newest start first (My bookings). */
    fun observeCustomerBookings(customerId: String): Flow<List<Booking>>

    /** One staff member's bookings that start on [date] (salon time), by start time (Today's schedule). */
    fun observeStaffDay(salonId: String, staffId: String, date: LocalDate): Flow<List<Booking>>

    /** Every booking in the salon that starts on [date] (salon time), by start time (Owner dashboard). */
    fun observeSalonDay(salonId: String, date: LocalDate): Flow<List<Booking>>
}
