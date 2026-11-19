package com.group12.salonlink.domain.queue

import com.group12.salonlink.domain.model.Booking
import com.group12.salonlink.domain.model.BookingStatus
import com.group12.salonlink.domain.model.QueueEntry
import java.time.Duration
import java.time.Instant

/**
 * Where a customer stands in the live queue.
 *
 * @property ahead the people still before them (in the chair first).
 * @property estimatedStart when their own service is expected to begin.
 */
data class QueueEta(
    val position: Int,
    val ahead: List<QueueEntry>,
    val estimatedStart: Instant,
    val waitMinutes: Long,
    val inService: Boolean,
    val done: Boolean,
)

/**
 * Pure ETA calculation for the live queue (proposal T08): mixes booked customers and walk-ins.
 *
 * Model: one chair, served in order (whoever is in the chair first, then by planned start).
 * Walk it forward from "now": each person starts at the later of "chair is free" and their
 * planned start, and takes their planned duration. Someone in the chair is expected to finish
 * at `since + duration`, but never "before now" (they're clearly still there).
 */
object QueueEtaCalculator {

    /** Statuses that still need the chair. */
    private val ACTIVE = setOf(BookingStatus.BOOKED, BookingStatus.CHECKED_IN, BookingStatus.WAITING, BookingStatus.IN_SERVICE)

    /** Minimum time we assume is left for someone in the chair (avoids "0 min" while they're still there). */
    private val MIN_REMAINING: Duration = Duration.ofMinutes(5)

    fun eta(entries: List<QueueEntry>, myBookingId: String, now: Instant): QueueEta? {
        val me = entries.firstOrNull { it.bookingId == myBookingId } ?: return null
        if (me.status !in ACTIVE) {
            return QueueEta(0, emptyList(), me.start, 0, inService = false, done = true)
        }
        val ordered = entries.filter { it.status in ACTIVE }
            .sortedWith(compareBy<QueueEntry> { it.status != BookingStatus.IN_SERVICE }.thenBy { it.start })

        var chairFree = now
        for (entry in ordered) {
            val begin: Instant
            val end: Instant
            if (entry.status == BookingStatus.IN_SERVICE) {
                begin = entry.since ?: entry.start
                val planned = begin.plus(Duration.ofMinutes(entry.durationMin.toLong()))
                end = maxOf(planned, now.plus(MIN_REMAINING))
            } else {
                begin = maxOf(chairFree, entry.start)
                end = begin.plus(Duration.ofMinutes(entry.durationMin.toLong()))
            }
            if (entry.bookingId == myBookingId) {
                val ahead = ordered.takeWhile { it.bookingId != myBookingId }
                val inService = entry.status == BookingStatus.IN_SERVICE
                val wait = if (inService) 0 else Duration.between(now, begin).toMinutes().coerceAtLeast(0)
                return QueueEta(ahead.size + 1, ahead, if (inService) begin else begin, wait, inService, done = false)
            }
            chairFree = maxOf(chairFree, end)
        }
        return null
    }

    /** Builds the anonymous board entries from today's bookings of one barber. */
    fun boardEntries(bookings: List<Booking>): List<QueueEntry> =
        bookings.filter { it.status in ACTIVE || it.status == BookingStatus.COMPLETED }
            .sortedBy { it.start }
            .map {
                QueueEntry(
                    bookingId = it.id,
                    start = it.start,
                    durationMin = it.durationMin,
                    status = it.status,
                    type = it.type,
                    since = if (it.status == BookingStatus.IN_SERVICE) it.updatedAt else null,
                )
            }
}
