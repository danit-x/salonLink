package com.group12.salonlink.domain.model

import java.time.Instant

/**
 * Where a booking is in its life (proposal FR-06, FR-08).
 *
 * ```
 * BOOKED ──► CHECKED_IN ──► WAITING ──► IN_SERVICE ──► COMPLETED
 *   │             │            │
 *   ├─► MISSED    └────────────┴──► (straight to IN_SERVICE if the chair is free)
 *   └─► CANCELLED  (also allowed from CHECKED_IN / WAITING)
 * ```
 *
 * WHY the transition table lives here: staff screens, the queue and (later) the security
 * rules all need the same answer to "may X become Y?". One function, unit-tested in
 * BookingStatusTest, means they can never disagree.
 */
enum class BookingStatus {
    BOOKED,
    CHECKED_IN,
    WAITING,
    IN_SERVICE,
    COMPLETED,
    CANCELLED,
    MISSED;

    /** Statuses this one may move to next. Terminal statuses return an empty set. */
    val nextStatuses: Set<BookingStatus>
        get() = when (this) {
            BOOKED -> setOf(CHECKED_IN, IN_SERVICE, CANCELLED, MISSED)
            CHECKED_IN -> setOf(WAITING, IN_SERVICE, CANCELLED)
            WAITING -> setOf(IN_SERVICE, CANCELLED)
            IN_SERVICE -> setOf(COMPLETED)
            COMPLETED, CANCELLED, MISSED -> emptySet()
        }

    fun canTransitionTo(next: BookingStatus): Boolean = next in nextStatuses

    /** Finished one way or another: nothing more can happen to it. */
    val isTerminal: Boolean get() = nextStatuses.isEmpty()

    /**
     * True while the booking still holds its time slot. A cancelled or missed booking frees
     * the slot. A completed one is in the past anyway.
     */
    val holdsSlot: Boolean get() = this in setOf(BOOKED, CHECKED_IN, WAITING, IN_SERVICE)

    companion object {
        /** Unknown values are treated as BOOKED so the slot stays protected (safer than freeing it). */
        fun fromValue(value: String?): BookingStatus = entries.firstOrNull { it.name == value } ?: BOOKED
    }
}

/** How the booking was made. Walk-ins are added by staff at the counter (FR-09). */
enum class BookingType {
    APPOINTMENT,
    WALK_IN;

    companion object {
        fun fromValue(value: String?): BookingType = entries.firstOrNull { it.name == value } ?: APPOINTMENT
    }
}

/**
 * A copy of a service *as it was when booked*.
 *
 * WHY a snapshot instead of just the service id: if the owner later changes the price
 * from Rs. 1,500 to Rs. 1,800, the customer's existing booking and receipt must still
 * say Rs. 1,500.
 */
data class BookedService(
    val serviceId: String,
    val name: String,
    val durationMin: Int,
    val price: Long,
)

/**
 * One appointment or walk-in, stored at `salons/{salonId}/bookings/{id}`.
 *
 * Names ([customerName], [staffName]) are copied in so lists can show them without extra
 * reads, and so a customer never needs permission to read staff profiles (or vice versa).
 *
 * @property start / [end] UTC instants. Display them with SalonTime (Asia/Colombo).
 * @property totalPrice whole rupees, the sum of [services] prices at booking time.
 * @property note optional message to the barber (Figma "Style profile" → note for barber).
 * @property createdAt / [updatedAt] set by the server. Null for a moment after a local write.
 */
data class Booking(
    val id: String,
    val salonId: String,
    val customerId: String?,
    val customerName: String,
    val staffId: String,
    val staffName: String,
    val services: List<BookedService>,
    val start: Instant,
    val end: Instant,
    val totalPrice: Long,
    val status: BookingStatus,
    val type: BookingType,
    val note: String? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
    val checkedInAt: Instant? = null,
) {
    /** Total length in minutes, e.g. Haircut 30 + Beard Trim 20 = 50. */
    val durationMin: Int get() = services.sumOf { it.durationMin }

    /** "Haircut & Beard Trim" as on the Figma Discovery card. */
    val title: String get() = services.joinToString(" & ") { it.name }

    /** True if this booking is still upcoming or in progress at [now]. */
    fun isUpcoming(now: Instant): Boolean = status.holdsSlot && end > now
}
