package com.group12.salonlink.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * Life of a waitlist entry (proposal P2, FR-11):
 * ```
 * WAITING ──(a slot frees up)──► OFFERED ──(customer accepts in 10 min)──► ACCEPTED
 *    ▲                              │
 *    └──── offer expired ◄──────────┘          (customer can LEAVE at any time)
 * ```
 */
enum class WaitlistStatus {
    WAITING, OFFERED, ACCEPTED, LEFT;

    companion object {
        fun fromValue(value: String?): WaitlistStatus = entries.firstOrNull { it.name == value } ?: WAITING
    }
}

/** A freed time offered to one waiting customer, valid until [expiresAt]. */
data class WaitlistOffer(
    val start: Instant,
    val end: Instant,
    val staffId: String,
    val expiresAt: Instant,
)

/**
 * "Tell me if a time frees up on [date]" (`salons/{salonId}/waitlist/{id}`).
 *
 * WHY no customer name in here: any signed-in app must be able to read waiting entries to
 * pass on a freed slot after a cancellation (no server on Spark). Like slot locks, the entry
 * only holds an account id, the wanted services and times.
 *
 * @property staffId the wanted barber.
 * @property windowStart / [windowEnd] acceptable start times (UTC instants on [date]).
 */
data class WaitlistEntry(
    val id: String,
    val salonId: String,
    val customerId: String,
    val staffId: String,
    val serviceIds: List<String>,
    val durationMin: Int,
    val date: LocalDate,
    val windowStart: Instant,
    val windowEnd: Instant,
    val status: WaitlistStatus,
    val offer: WaitlistOffer? = null,
    val createdAt: Instant? = null,
) {
    /** An offer the customer can still accept at [now]. */
    fun activeOffer(now: Instant): WaitlistOffer? =
        offer?.takeIf { status == WaitlistStatus.OFFERED && now < it.expiresAt }

    /**
     * Still hoping for a slot: WAITING, or OFFERED but the offer ran out (then the next freed
     * slot may be offered again; there is no server timer on Spark to reset it).
     */
    fun isWaiting(now: Instant): Boolean =
        status == WaitlistStatus.WAITING || (status == WaitlistStatus.OFFERED && activeOffer(now) == null)
}
