package com.group12.salonlink.domain.booking

import java.time.Duration
import java.time.Instant

/**
 * The booking calendar is cut into fixed 10-minute slots (PLAN.md §5).
 *
 * A slot is identified by a single number: how many 10-minute steps have passed since
 * 1970-01-01T00:00Z (the Unix epoch). Example: 2026-12-03 12:00 UTC is slot 2_982_528.
 *
 * WHY a number instead of "kasun_20261203_1730":
 *  - the security rules can do arithmetic on it ("the next slot is slot + 1"), which is what
 *    lets them check that a booking holds EVERY one of its slots (see firestore.rules);
 *  - it doesn't depend on any time zone, so it can never drift because of a phone setting.
 *
 * WHY 10 minutes: every service in the Figma (30, 20, 90, 60 min) is a multiple of 10, so no
 * time is wasted, while a 2-hour booking still needs only 12 lock documents.
 */
object SlotGrid {

    const val SLOT_MINUTES = 10L
    private const val SLOT_SECONDS = SLOT_MINUTES * 60

    /**
     * Longest booking allowed: 16 slots = 2 h 40 min (Hair Colour 90 + Haircut 30 + Beard 20 = 140
     * fits). WHY a limit at all: Firestore rules may look at no more than 20 documents per
     * transaction, and a booking touches one lock per slot plus a few other documents.
     * MUST match MAX_SLOTS in firebase/firestore.rules.
     */
    const val MAX_SLOTS = 16

    /** The slot that contains [instant] (rounds down). */
    fun slotOf(instant: Instant): Long = Math.floorDiv(instant.epochSecond, SLOT_SECONDS)

    /** The moment slot [slot] begins. */
    fun startOf(slot: Long): Instant = Instant.ofEpochSecond(slot * SLOT_SECONDS)

    /** True if [instant] is exactly on a slot boundary (bookings must start on one). */
    fun isAligned(instant: Instant): Boolean =
        instant.nano == 0 && Math.floorMod(instant.epochSecond, SLOT_SECONDS) == 0L

    /** Number of slots needed for [durationMin] minutes, rounded up (a 25-min job takes 3 slots). */
    // (Math.ceilDiv would be neater but needs Java 18, which Android 8 doesn't have.)
    fun slotsFor(durationMin: Int): Int = ((durationMin + SLOT_MINUTES - 1) / SLOT_MINUTES).toInt()

    /**
     * Every slot touched by the half-open interval [start, end).
     * Example: 17:30–18:20 → the five slots 17:30, 17:40, 17:50, 18:00, 18:10.
     */
    fun slotsBetween(start: Instant, end: Instant): LongRange {
        require(end > start) { "end ($end) must be after start ($start)" }
        // The last slot is the one containing the final instant before `end`.
        return slotOf(start)..slotOf(end.minusNanos(1))
    }

    /** One slot's length as a [Duration]. */
    val SLOT_DURATION: Duration = Duration.ofMinutes(SLOT_MINUTES)
}
