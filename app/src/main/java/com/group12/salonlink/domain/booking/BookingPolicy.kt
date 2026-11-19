package com.group12.salonlink.domain.booking

import com.group12.salonlink.domain.model.Booking
import com.group12.salonlink.domain.model.BookingStatus
import java.time.Duration
import java.time.Instant

/**
 * Customer-facing booking rules, kept in one place so every screen quotes the same numbers.
 */
object BookingPolicy {

    /**
     * How far ahead a customer may book. WHY a limit: barbers' hours and blocks are only
     * planned a few weeks out, and the Date & time calendar stays short and readable.
     */
    const val BOOKING_WINDOW_DAYS = 60L

    /**
     * Customers can cancel in the app up to this many hours before the start. Later than that
     * they call the salon (staff can still cancel). MUST match the 24 h in firestore.rules.
     */
    const val FREE_CANCELLATION_HOURS = 24L

    /** The last moment the customer can cancel in the app. */
    fun cancelDeadline(booking: Booking): Instant = booking.start.minus(Duration.ofHours(FREE_CANCELLATION_HOURS))

    /** True if the customer may cancel [booking] themselves at [now]. */
    fun customerCanCancel(booking: Booking, now: Instant): Boolean =
        booking.status == BookingStatus.BOOKED && now < cancelDeadline(booking)
}
