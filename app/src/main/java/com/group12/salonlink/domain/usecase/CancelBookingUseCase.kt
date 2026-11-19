package com.group12.salonlink.domain.usecase

import com.group12.salonlink.domain.booking.BookingPolicy
import com.group12.salonlink.domain.model.AppNotification
import com.group12.salonlink.domain.model.BookingStatus
import com.group12.salonlink.domain.model.DataError
import com.group12.salonlink.domain.model.DataException
import com.group12.salonlink.domain.model.NotificationType
import com.group12.salonlink.domain.repository.BookingRepository
import com.group12.salonlink.domain.repository.NotificationRepository
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Cancels a booking and frees its time (FR-05, US-05).
 *
 * Steps:
 *  1. Customers may cancel only up to 24 h before the start ([BookingPolicy]); staff any time.
 *  2. The repository's transaction marks it CANCELLED and deletes its slot locks.
 *  3. The customer gets a "Booking cancelled" entry in their notification feed.
 *  4. The freed time is offered to the first waiting customer ([OfferFreedSlotUseCase]).
 *
 * @throws DataException TOO_LATE_TO_CANCEL / NOT_ALLOWED / NOT_FOUND / NETWORK…
 */
class CancelBookingUseCase @Inject constructor(
    private val bookingRepository: BookingRepository,
    private val notificationRepository: NotificationRepository,
    private val offerFreedSlot: OfferFreedSlotUseCase,
    private val clock: Clock,
) {
    /** @param byStaff true when salon staff cancel (no 24 h limit). */
    suspend operator fun invoke(salonId: String, bookingId: String, byStaff: Boolean = false) {
        val booking = bookingRepository.observeBooking(salonId, bookingId).first()
            ?: throw DataException(DataError.NOT_FOUND)
        if (!byStaff && !BookingPolicy.customerCanCancel(booking, clock.instant())) {
            throw DataException(
                if (booking.status.canTransitionTo(BookingStatus.CANCELLED)) DataError.TOO_LATE_TO_CANCEL
                else DataError.NOT_ALLOWED,
            )
        }
        bookingRepository.cancelBooking(salonId, bookingId)

        booking.customerId?.let { customer ->
            notificationRepository.addBestEffort(customer, AppNotification.about(booking, NotificationType.BOOKING_CANCELLED))
        }
        offerFreedSlot(booking)
    }
}

/**
 * Adds a feed entry, but never lets a failure undo the real action.
 *
 * WHY swallow the error here (and only here): the booking or cancellation has already been
 * saved, which is what matters. The feed is a convenience copy; if writing it fails (offline,
 * or the rule refuses a duplicate after a retry) the booking screens still show the truth.
 */
suspend fun NotificationRepository.addBestEffort(uid: String, notification: AppNotification) {
    try {
        add(uid, notification)
    } catch (_: DataException) {
        // Intentionally ignored, see KDoc.
    }
}
