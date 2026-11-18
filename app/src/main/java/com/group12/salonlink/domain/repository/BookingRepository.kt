package com.group12.salonlink.domain.repository

import com.group12.salonlink.domain.model.Booking
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Bookings: live reads plus the double-booking-safe writes of the booking engine (PLAN.md §5).
 * Flows fail, and suspend functions throw, [com.group12.salonlink.domain.model.DataException].
 *
 * ViewModels should go through the use cases in `domain.usecase` rather than calling the
 * write functions directly: the use cases do the business checks (hours, blocks, services).
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

    /**
     * Slot numbers (see [com.group12.salonlink.domain.booking.SlotGrid]) of [staffId] that are
     * already held by some booking on [date] (salon time). Contains no customer data, so any
     * signed-in user may read it to see which times are free.
     */
    fun observeTakenSlots(salonId: String, staffId: String, date: LocalDate): Flow<Set<Long>>

    /**
     * Writes [booking] and locks every slot it covers, all in ONE transaction.
     *
     * - If any slot is already locked by another booking → throws SLOT_TAKEN and writes nothing.
     * - Idempotent: calling it again with the same [Booking.id] (e.g. a retry after the network
     *   dropped) returns the booking that was saved the first time instead of making a second (T11).
     *
     * @return the booking as stored.
     */
    suspend fun createBooking(booking: Booking): Booking

    /**
     * Marks the booking CANCELLED and frees its slots, in one transaction.
     * Throws NOT_ALLOWED if its status can no longer be cancelled (e.g. COMPLETED).
     * Cancelling an already-cancelled booking is a no-op (safe to retry).
     */
    suspend fun cancelBooking(salonId: String, bookingId: String)
}
