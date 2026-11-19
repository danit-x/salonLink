package com.group12.salonlink.domain.booking

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
     * Free cancellation up to this many hours before the start (Figma "Barber profile" footer).
     * Shown to the customer now; enforced by the cancel flow in Phase 5.
     */
    const val FREE_CANCELLATION_HOURS = 24L
}
