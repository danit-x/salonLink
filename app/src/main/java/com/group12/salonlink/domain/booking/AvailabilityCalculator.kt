package com.group12.salonlink.domain.booking

import com.group12.salonlink.core.util.SalonTime
import com.group12.salonlink.domain.model.StaffBlock
import com.group12.salonlink.domain.model.TimeRange
import com.group12.salonlink.domain.model.WeeklyHours
import java.time.Instant
import java.time.LocalDate

/**
 * Why a requested time can't be booked. Shown to the user in plain words (NFR-10).
 */
enum class SlotProblem {
    CLOSED,         // salon or barber not working that day
    OUTSIDE_HOURS,  // starts before opening or would finish after closing
    BLOCKED,        // overlaps lunch / leave (staff block, P2)
    TAKEN,          // another booking already holds part of this time
    IN_PAST,        // start time has already gone
    TOO_LONG,       // more than SlotGrid.MAX_SLOTS
    NOT_ALIGNED,    // not on a 10-minute boundary (a bug or a tampered request)
}

/**
 * Everything needed to decide availability for ONE barber on ONE day.
 *
 * @property takenSlots slot numbers already locked by other bookings (from the slotLocks
 *   collection). WHY locks and not bookings: customers aren't allowed to read other people's
 *   bookings (T09), but they may read the anonymous locks, which hold no names.
 * @property earliestStart nothing may start before this, normally "now".
 */
data class DayAvailabilityInput(
    val date: LocalDate,
    val salonHours: WeeklyHours,
    val staffHours: WeeklyHours,
    val blocks: List<StaffBlock>,
    val takenSlots: Set<Long>,
    val earliestStart: Instant,
)

/**
 * One button in the time grid.
 *
 * @property problem null = bookable. Otherwise why not (taken, break, past closing…).
 */
data class SlotOption(val start: Instant, val problem: SlotProblem?) {
    val isAvailable: Boolean get() = problem == null
}

/**
 * The heart of the booking engine: a PURE function from "the facts of the day" to "the times
 * a customer may choose". No Firebase, no clock, no threads, so it is fully unit-tested
 * (AvailabilityCalculatorTest) and gives the same answer on every phone.
 *
 * The server never trusts this calculation alone: the transaction + slot locks + security
 * rules make the final decision (see FirestoreBookingRepository.createBooking).
 */
object AvailabilityCalculator {

    /** Default spacing between offered start times. 30 min keeps the Figma time grid readable. */
    const val DEFAULT_STEP_MIN = 30L

    /**
     * Start times on [input].date where a job of [durationMin] minutes fits completely.
     *
     * @param stepMin spacing between candidates, counted from the opening time (09:00, 09:30…).
     *   Must be a multiple of [SlotGrid.SLOT_MINUTES] so every candidate is slot-aligned.
     */
    fun availableStarts(
        input: DayAvailabilityInput,
        durationMin: Int,
        stepMin: Long = DEFAULT_STEP_MIN,
    ): List<Instant> = dayOptions(input, durationMin, stepMin).filter { it.isAvailable }.map { it.start }

    /**
     * Every candidate start time of the day with its verdict, for the Figma time grid, which
     * shows a taken time (e.g. "4:00 pm") struck through instead of hiding it.
     *
     * Times that have already gone ([SlotProblem.IN_PAST]) are left out: showing them crossed
     * out would only clutter the grid.
     */
    fun dayOptions(
        input: DayAvailabilityInput,
        durationMin: Int,
        stepMin: Long = DEFAULT_STEP_MIN,
    ): List<SlotOption> {
        require(durationMin > 0) { "durationMin must be positive" }
        require(stepMin > 0 && stepMin % SlotGrid.SLOT_MINUTES == 0L) {
            "stepMin must be a positive multiple of ${SlotGrid.SLOT_MINUTES}"
        }
        val hours = workingWindow(input) ?: return emptyList()
        val close = SalonTime.toInstant(input.date, hours.end)
        // If hours were typed as e.g. 09:05, start offering from the next slot boundary (09:10).
        val open = SalonTime.toInstant(input.date, hours.start)
        val first = if (SlotGrid.isAligned(open)) open else SlotGrid.startOf(SlotGrid.slotOf(open) + 1)

        return generateSequence(first) { it.plusSeconds(stepMin * 60) }
            .takeWhile { it < close }
            .map { start -> SlotOption(start, check(input, start, durationMin)) }
            .filter { it.problem != SlotProblem.IN_PAST }
            .toList()
    }

    /**
     * Checks one specific start time. Returns null if it can be booked, or the first
     * [SlotProblem] found. Used again right before writing, in CreateBookingUseCase.
     */
    fun check(input: DayAvailabilityInput, start: Instant, durationMin: Int): SlotProblem? {
        if (!SlotGrid.isAligned(start)) return SlotProblem.NOT_ALIGNED
        if (SlotGrid.slotsFor(durationMin) > SlotGrid.MAX_SLOTS) return SlotProblem.TOO_LONG
        val hours = workingWindow(input) ?: return SlotProblem.CLOSED
        if (start < input.earliestStart) return SlotProblem.IN_PAST

        val end = start.plusSeconds(durationMin * 60L)
        val open = SalonTime.toInstant(input.date, hours.start)
        val close = SalonTime.toInstant(input.date, hours.end)
        // WHY end <= close (not <): a 30-min haircut at 18:30 ends exactly at a 19:00 close. Fine.
        if (start < open || end > close) return SlotProblem.OUTSIDE_HOURS

        if (input.blocks.any { it.overlaps(start, end) }) return SlotProblem.BLOCKED
        if (SlotGrid.slotsBetween(start, end).any { it in input.takenSlots }) return SlotProblem.TAKEN
        return null
    }

    /**
     * When the barber can actually take clients that day: the overlap of the salon's opening
     * hours and the barber's own working hours. Null = no overlap (day off / salon closed).
     */
    fun workingWindow(input: DayAvailabilityInput): TimeRange? {
        val salon = input.salonHours.hoursOn(input.date) ?: return null
        val staff = input.staffHours.hoursOn(input.date) ?: return null
        val start = maxOf(salon.start, staff.start)
        val end = minOf(salon.end, staff.end)
        return if (start < end) TimeRange(start, end) else null
    }
}
