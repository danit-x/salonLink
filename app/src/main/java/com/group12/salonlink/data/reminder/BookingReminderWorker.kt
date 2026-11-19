package com.group12.salonlink.data.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.group12.salonlink.R
import com.group12.salonlink.core.notifications.SystemNotifier
import com.group12.salonlink.core.util.SalonTime
import com.group12.salonlink.domain.model.BookingStatus
import com.group12.salonlink.domain.model.DataException
import com.group12.salonlink.domain.reminder.ReminderKind
import com.group12.salonlink.domain.repository.BookingRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Fires one appointment reminder ("Haircut with Kasun at 5:30 pm, in 2 hours").
 *
 * WHY it re-reads the booking first: the booking may have been cancelled on another phone, or
 * by the salon, after the reminder was planned. Then no reminder is shown. If the booking can't
 * be read (offline), the reminder is still shown from the saved snapshot: a reminder for a
 * real appointment matters more than a rare stale one.
 */
@HiltWorker
class BookingReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val bookingRepository: BookingRepository,
    private val notifier: SystemNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val salonId = inputData.getString(KEY_SALON_ID) ?: return Result.success()
        val bookingId = inputData.getString(KEY_BOOKING_ID) ?: return Result.success()
        val kind = ReminderKind.entries.firstOrNull { it.name == inputData.getString(KEY_KIND) } ?: ReminderKind.SOON

        val fresh = try {
            withTimeoutOrNull(READ_TIMEOUT_MS) { bookingRepository.observeBooking(salonId, bookingId).first() }
        } catch (_: DataException) {
            null // offline or no access: fall back to the snapshot below
        }
        if (fresh != null && fresh.status != BookingStatus.BOOKED) return Result.success()

        val title = fresh?.title ?: inputData.getString(KEY_TITLE).orEmpty()
        val staff = fresh?.staffName ?: inputData.getString(KEY_STAFF).orEmpty()
        val start = fresh?.start ?: Instant.ofEpochMilli(inputData.getLong(KEY_START_MILLIS, 0))

        val context = applicationContext
        val headline = context.getString(
            if (kind == ReminderKind.DAY_BEFORE) R.string.reminder_title_tomorrow else R.string.reminder_title_soon,
        )
        val text = context.getString(
            R.string.reminder_text, title, staff, SalonTime.formatShortDate(start), SalonTime.formatTime(start),
        )
        notifier.show(key = "reminder-$bookingId-${kind.name}", title = headline, text = text)
        return Result.success()
    }

    companion object {
        const val KEY_SALON_ID = "salonId"
        const val KEY_BOOKING_ID = "bookingId"
        const val KEY_KIND = "kind"
        const val KEY_TITLE = "title"
        const val KEY_STAFF = "staff"
        const val KEY_START_MILLIS = "startMillis"
        private const val READ_TIMEOUT_MS = 15_000L
    }
}
