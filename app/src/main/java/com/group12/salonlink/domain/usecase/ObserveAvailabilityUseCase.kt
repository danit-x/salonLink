package com.group12.salonlink.domain.usecase

import com.group12.salonlink.core.util.SalonTime
import com.group12.salonlink.domain.booking.AvailabilityCalculator
import com.group12.salonlink.domain.booking.DayAvailabilityInput
import com.group12.salonlink.domain.booking.SlotOption
import com.group12.salonlink.domain.repository.BookingRepository
import com.group12.salonlink.domain.repository.SalonRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Live list of start times a customer can pick for one barber on one day
 * (Figma "Date & time", step 2/3). Covers FR-03 / US-03.
 *
 * It listens to four things at once (salon hours, the barber, the barber's blocks and the
 * taken slots) and recalculates whenever any of them changes. So if someone else books 17:30
 * while you are looking at the screen, 17:30 disappears by itself.
 */
class ObserveAvailabilityUseCase @Inject constructor(
    private val salonRepository: SalonRepository,
    private val bookingRepository: BookingRepository,
    private val clock: Clock,
) {

    operator fun invoke(
        salonId: String,
        staffId: String,
        date: LocalDate,
        durationMin: Int,
        stepMin: Long = AvailabilityCalculator.DEFAULT_STEP_MIN,
    ): Flow<List<Instant>> =
        observeDayInput(salonId, staffId, date).map { input ->
            if (input == null || durationMin <= 0) emptyList()
            else AvailabilityCalculator.availableStarts(input, durationMin, stepMin)
        }

    /**
     * Like [invoke], but keeps unavailable times too (marked with their [SlotOption.problem]),
     * so the screen can show them struck through as in the Figma time grid.
     */
    fun dayOptions(
        salonId: String,
        staffId: String,
        date: LocalDate,
        durationMin: Int,
        stepMin: Long = AvailabilityCalculator.DEFAULT_STEP_MIN,
    ): Flow<List<SlotOption>> =
        observeDayInput(salonId, staffId, date).map { input ->
            if (input == null || durationMin <= 0) emptyList()
            else AvailabilityCalculator.dayOptions(input, durationMin, stepMin)
        }

    /**
     * The raw facts of the day for [staffId], or null if the salon or barber doesn't exist
     * (or the barber is inactive). Shared with [CreateBookingUseCase] so the screen and the
     * final check use exactly the same inputs.
     */
    fun observeDayInput(salonId: String, staffId: String, date: LocalDate): Flow<DayAvailabilityInput?> {
        val dayStart = SalonTime.startOfDay(date)
        val dayEnd = SalonTime.startOfDay(date.plusDays(1))
        return combine(
            salonRepository.observeSalon(salonId),
            salonRepository.observeStaffMember(salonId, staffId),
            salonRepository.observeBlocks(salonId, staffId, dayStart, dayEnd),
            bookingRepository.observeTakenSlots(salonId, staffId, date),
        ) { salon, staff, blocks, taken ->
            if (salon == null || staff == null || !staff.active) {
                null
            } else {
                DayAvailabilityInput(
                    date = date,
                    salonHours = salon.openingHours,
                    staffHours = staff.workingHours,
                    blocks = blocks,
                    takenSlots = taken,
                    // Read the clock on every recalculation so past times drop off as time goes on.
                    earliestStart = clock.instant(),
                )
            }
        }
    }
}
