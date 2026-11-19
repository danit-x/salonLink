package com.group12.salonlink.domain.reminder

import com.group12.salonlink.domain.model.Booking
import com.group12.salonlink.domain.model.BookingStatus
import java.time.Duration
import java.time.Instant

/** The two reminders a booking gets. */
enum class ReminderKind(val lead: Duration) {
    DAY_BEFORE(Duration.ofHours(24)),
    SOON(Duration.ofHours(2)),
}

/** One phone notification to show at [fireAt]. The booking snapshot is a fallback if it can't be re-read. */
data class PlannedReminder(
    val booking: Booking,
    val kind: ReminderKind,
    val fireAt: Instant,
) {
    /** Unique per booking + kind, so re-planning replaces instead of duplicating. */
    val workName: String get() = workName(booking.id, kind)

    companion object {
        fun workName(bookingId: String, kind: ReminderKind) = "reminder-$bookingId-${kind.name.lowercase()}"
    }
}

/** What the scheduler should do after the customer's bookings changed. */
data class ReminderPlan(
    val schedule: List<PlannedReminder>,
    /** Work names to cancel: reminders of cancelled, finished or past bookings. */
    val cancel: List<String>,
)

/**
 * Decides which local reminders should exist (proposal FR-07). Pure: no WorkManager, no clock,
 * so the rules are unit-tested (ReminderPlannerTest).
 *
 * Rules: an active (BOOKED) appointment gets a reminder 24 h before and 2 h before its start.
 * A reminder whose time has already passed is skipped (booking made 3 h ahead → only "soon").
 * Everything else in the list has its reminders cancelled.
 */
object ReminderPlanner {

    fun plan(bookings: List<Booking>, now: Instant): ReminderPlan {
        val schedule = mutableListOf<PlannedReminder>()
        val cancel = mutableListOf<String>()
        for (booking in bookings) {
            for (kind in ReminderKind.entries) {
                val fireAt = booking.start.minus(kind.lead)
                if (booking.status == BookingStatus.BOOKED && fireAt > now) {
                    schedule += PlannedReminder(booking, kind, fireAt)
                } else {
                    cancel += PlannedReminder.workName(booking.id, kind)
                }
            }
        }
        return ReminderPlan(schedule, cancel)
    }
}
