package com.group12.salonlink.domain.usecase

import com.group12.salonlink.core.util.SalonTime
import com.group12.salonlink.domain.booking.AvailabilityCalculator
import com.group12.salonlink.domain.booking.SlotGrid
import com.group12.salonlink.domain.booking.SlotProblem
import com.group12.salonlink.domain.model.BookedService
import com.group12.salonlink.domain.model.Booking
import com.group12.salonlink.domain.model.BookingStatus
import com.group12.salonlink.domain.model.BookingType
import com.group12.salonlink.domain.model.DataError
import com.group12.salonlink.domain.model.DataException
import com.group12.salonlink.domain.model.SlotUnavailableException
import com.group12.salonlink.domain.repository.BookingRepository
import com.group12.salonlink.domain.repository.SalonRepository
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * What the customer (or staff, for a walk-in) asked for on the Confirmation screen.
 *
 * @property bookingId a random UUID created ONCE when the Confirmation screen opens.
 *   WHY the caller makes it: if "Confirm" is tapped twice, or retried after a network error,
 *   the same id is sent again and the engine returns the existing booking instead of
 *   creating a duplicate (T11).
 * @property customerId null only for a walk-in without an app account.
 */
data class BookingRequest(
    val bookingId: String,
    val salonId: String,
    val staffId: String,
    val serviceIds: List<String>,
    val start: Instant,
    val customerId: String?,
    val customerName: String,
    val type: BookingType = BookingType.APPOINTMENT,
    val note: String? = null,
)

/**
 * Turns a [BookingRequest] into a saved [Booking] (FR-04, US-04, proposal T02).
 *
 * Two layers of protection, on purpose:
 *  1. HERE: friendly business checks with up-to-date data (services exist and are offered by
 *     this barber, the time is inside working hours, not on a break, not in the past, free).
 *     These give the user a precise reason ("Kasun is on a break then").
 *  2. IN THE REPOSITORY + RULES: the transaction and slot locks, which are the final word
 *     against two people taking the same slot at the same moment (T03).
 *
 * @throws DataException INVALID_REQUEST / NOT_FOUND / SLOT_TAKEN / NETWORK…
 * @throws SlotUnavailableException with the exact [com.group12.salonlink.domain.booking.SlotProblem].
 */
class CreateBookingUseCase @Inject constructor(
    private val salonRepository: SalonRepository,
    private val bookingRepository: BookingRepository,
    private val observeAvailability: ObserveAvailabilityUseCase,
    private val clock: Clock,
) {

    suspend operator fun invoke(request: BookingRequest): Booking {
        if (request.serviceIds.isEmpty() || request.customerName.isBlank()) {
            throw DataException(DataError.INVALID_REQUEST)
        }

        val staff = salonRepository.observeStaffMember(request.salonId, request.staffId).first()
            ?.takeIf { it.active }
            ?: throw DataException(DataError.NOT_FOUND)

        // Services: must all exist, be active, and be offered by the chosen barber.
        val catalogue = salonRepository.observeServices(request.salonId).first().associateBy { it.id }
        val chosen = request.serviceIds.distinct().map { id ->
            val service = catalogue[id] ?: throw DataException(DataError.INVALID_REQUEST)
            if (!service.isOfferedBy(staff.id)) throw DataException(DataError.INVALID_REQUEST)
            service
        }
        val durationMin = chosen.sumOf { it.durationMin }

        // Same calculation the Date & time screen used, re-run with fresh data right now.
        val date = SalonTime.localDateOf(request.start)
        val input = observeAvailability.observeDayInput(request.salonId, request.staffId, date).first()
            ?: throw DataException(DataError.NOT_FOUND)
        val now = clock.instant()
        // WHY walk-ins may start in the current slot: a walk-in added at 10:04 starts "now",
        // i.e. in the 10:00 slot, which has technically already begun.
        val earliest = if (request.type == BookingType.WALK_IN) SlotGrid.startOf(SlotGrid.slotOf(now)) else now
        val problem = AvailabilityCalculator.check(input.copy(earliestStart = earliest), request.start, durationMin)
        // WHY TAKEN is NOT thrown here: on a retry (T11) the slots are "taken" by this very
        // booking. The local data can't tell whose locks they are, but the transaction can:
        // it returns the existing booking if the id matches, or fails with SLOT_TAKEN if not.
        if (problem != null && problem != SlotProblem.TAKEN) throw SlotUnavailableException(problem)

        val booking = Booking(
            id = request.bookingId,
            salonId = request.salonId,
            customerId = request.customerId,
            customerName = request.customerName.trim(),
            staffId = staff.id,
            staffName = staff.name,
            services = chosen.map { BookedService(it.id, it.name, it.durationMin, it.price) },
            start = request.start,
            end = request.start.plusSeconds(durationMin * 60L),
            totalPrice = chosen.sumOf { it.price },
            status = BookingStatus.BOOKED,
            type = request.type,
            note = request.note?.trim()?.take(MAX_NOTE_LENGTH)?.ifEmpty { null },
        )
        return try {
            bookingRepository.createBooking(booking)
        } catch (e: DataException) {
            // Report a lost race the same way as any other unavailable time, so the UI has one case.
            if (e.error == DataError.SLOT_TAKEN && e !is SlotUnavailableException) {
                throw SlotUnavailableException(SlotProblem.TAKEN)
            }
            throw e
        }
    }

    companion object {
        /** MUST match the note limit in firestore.rules. */
        const val MAX_NOTE_LENGTH = 200
    }
}
