package com.group12.salonlink.domain.usecase

import com.group12.salonlink.domain.model.AppNotification
import com.group12.salonlink.domain.model.BookingStatus
import com.group12.salonlink.domain.model.DataError
import com.group12.salonlink.domain.model.DataException
import com.group12.salonlink.domain.model.NotificationType
import com.group12.salonlink.domain.repository.BookingRepository
import com.group12.salonlink.domain.repository.NotificationRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Staff move a booking along its life: check in → (waiting) → in the chair → completed,
 * or mark a no-show / cancel (proposal FR-06, FR-08, US-07).
 *
 * After the change the customer (if they have the app) gets a feed entry such as
 * "You're checked in", which their phone also shows as a notification.
 */
class UpdateBookingStatusUseCase @Inject constructor(
    private val bookingRepository: BookingRepository,
    private val notificationRepository: NotificationRepository,
    private val cancelBooking: CancelBookingUseCase,
) {
    /** @throws DataException NOT_ALLOWED if the status table forbids the move. */
    suspend operator fun invoke(salonId: String, bookingId: String, next: BookingStatus) {
        if (next == BookingStatus.CANCELLED) {
            cancelBooking(salonId, bookingId, byStaff = true)
            return
        }
        val booking = bookingRepository.observeBooking(salonId, bookingId).first()
            ?: throw DataException(DataError.NOT_FOUND)
        if (!booking.status.canTransitionTo(next)) throw DataException(DataError.NOT_ALLOWED)

        bookingRepository.updateStatus(salonId, bookingId, next)

        // WAITING is an internal queue detail; the customer already got "checked in".
        val customer = booking.customerId
        if (customer != null && next != BookingStatus.WAITING) {
            notificationRepository.addBestEffort(
                customer,
                AppNotification.about(booking, NotificationType.STATUS_CHANGED, status = next),
            )
        }
    }
}
