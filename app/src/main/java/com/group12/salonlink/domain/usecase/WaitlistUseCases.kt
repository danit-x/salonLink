package com.group12.salonlink.domain.usecase

import com.group12.salonlink.domain.booking.SlotGrid
import com.group12.salonlink.domain.model.Booking
import com.group12.salonlink.domain.model.DataError
import com.group12.salonlink.domain.model.DataException
import com.group12.salonlink.domain.model.WaitlistEntry
import com.group12.salonlink.domain.model.WaitlistOffer
import com.group12.salonlink.domain.repository.WaitlistRepository
import java.time.Clock
import java.time.Duration
import java.util.UUID
import javax.inject.Inject

/** How long a waitlisted customer has to accept a freed slot (proposal: 10-minute expiry). */
val OFFER_VALIDITY: Duration = Duration.ofMinutes(10)

/**
 * Passes the time freed by a cancellation / no-show to the first suitable waiting customer
 * (FIFO by when they joined), proposal FR-11, T07.
 *
 * Suitable = same barber, same day, the wanted start is inside their window and their
 * services fit into the freed time. Only ONE customer gets the offer; if they don't accept
 * within [OFFER_VALIDITY], the next freed slot can go to the next person.
 *
 * WHY the offer is safe without a server: accepting goes through the normal booking engine
 * (transaction + slot locks), which re-checks that the time is still free. If two people
 * somehow accept the same time, only one booking can be written (T03).
 */
class OfferFreedSlotUseCase @Inject constructor(
    private val waitlistRepository: WaitlistRepository,
    private val clock: Clock,
) {
    /** @return the entry that got the offer, or null if nobody was waiting for it. */
    suspend operator fun invoke(freed: Booking): WaitlistEntry? {
        val now = clock.instant()
        if (freed.end <= now) return null // the time has passed: nothing to offer
        // WHY ceil: after a no-show (always after the start) "now" is mid-slot, and bookings
        // must start on a 10-minute boundary or the engine refuses them (NOT_ALIGNED).
        val start = maxOf(freed.start, SlotGrid.ceil(now))
        if (start >= freed.end) return null
        val freedMinutes = Duration.between(start, freed.end).toMinutes()
        val date = com.group12.salonlink.core.util.SalonTime.localDateOf(freed.start)

        val candidate = try {
            waitlistRepository.entriesFor(freed.salonId, date)
        } catch (_: DataException) {
            return null // the cancellation itself succeeded; the offer is a bonus
        }
            .filter { it.isWaiting(now) && it.staffId == freed.staffId }
            // Never hand the time back to the person who just gave it up.
            .filter { it.customerId != freed.customerId }
            .filter { it.durationMin <= freedMinutes && start >= it.windowStart && start < it.windowEnd }
            .sortedBy { it.createdAt }
            .firstOrNull() ?: return null

        val offer = WaitlistOffer(
            start = start,
            end = start.plusSeconds(candidate.durationMin * 60L),
            staffId = freed.staffId,
            expiresAt = now.plus(OFFER_VALIDITY),
        )
        return try {
            waitlistRepository.offer(freed.salonId, candidate.id, offer)
            candidate.copy(offer = offer)
        } catch (_: DataException) {
            null
        }
    }
}

/**
 * Accepts a waitlist offer: books the offered time through the booking engine, then marks the
 * entry ACCEPTED. Throws OFFER_EXPIRED-style errors as [DataException] (NOT_ALLOWED / SLOT_TAKEN).
 */
class AcceptWaitlistOfferUseCase @Inject constructor(
    private val createBooking: CreateBookingUseCase,
    private val waitlistRepository: WaitlistRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(entry: WaitlistEntry, customerName: String): Booking {
        val offer = entry.activeOffer(clock.instant()) ?: throw DataException(DataError.NOT_ALLOWED)
        val booking = createBooking(
            BookingRequest(
                // Derived from the entry, so accepting twice (retry) can't book twice (T11).
                bookingId = UUID.nameUUIDFromBytes("waitlist-${entry.id}".toByteArray()).toString(),
                salonId = entry.salonId,
                staffId = offer.staffId,
                serviceIds = entry.serviceIds,
                start = offer.start,
                customerId = entry.customerId,
                customerName = customerName,
            ),
        )
        try {
            waitlistRepository.markAccepted(entry.salonId, entry.id)
        } catch (_: DataException) {
            // WHY: the booking is already saved. If the offer ran out a moment ago, the rules
            // refuse ACCEPTED; leaving the waitlist instead stops the entry from being offered
            // another time. Best effort: the booking is what matters.
            try {
                waitlistRepository.leave(entry.salonId, entry.id)
            } catch (_: DataException) {
            }
        }
        return booking
    }
}
