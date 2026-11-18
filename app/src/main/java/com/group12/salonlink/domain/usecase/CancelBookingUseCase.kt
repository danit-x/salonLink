package com.group12.salonlink.domain.usecase

import com.group12.salonlink.domain.repository.BookingRepository
import javax.inject.Inject

/**
 * Cancels a booking and frees its time (FR-05, US-05).
 *
 * Thin today: the repository's transaction does the status check and frees the slot locks.
 * WHY a use case anyway: Phase 7 adds "offer the freed slot to the waitlist" here, and the
 * ViewModels won't need to change when it does.
 */
class CancelBookingUseCase @Inject constructor(
    private val bookingRepository: BookingRepository,
) {
    suspend operator fun invoke(salonId: String, bookingId: String) {
        bookingRepository.cancelBooking(salonId, bookingId)
    }
}
