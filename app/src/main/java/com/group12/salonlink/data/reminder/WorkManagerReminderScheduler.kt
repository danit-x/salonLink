package com.group12.salonlink.data.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.group12.salonlink.domain.reminder.ReminderPlan
import com.group12.salonlink.domain.repository.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ReminderScheduler] on WorkManager.
 *
 * WHY WorkManager: it persists scheduled work in its own database, so a reminder set today
 * still fires tomorrow even if the app was swiped away or the phone rebooted.
 * Each reminder is "unique work" named after booking + kind; REPLACE means re-planning after a
 * change simply overwrites it.
 */
@Singleton
class WorkManagerReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: Clock,
) : ReminderScheduler {

    private val workManager get() = WorkManager.getInstance(context)

    override fun apply(plan: ReminderPlan) {
        val now = clock.instant()
        plan.schedule.forEach { reminder ->
            val b = reminder.booking
            val request = OneTimeWorkRequestBuilder<BookingReminderWorker>()
                .setInitialDelay(Duration.between(now, reminder.fireAt).coerceAtLeast(Duration.ZERO))
                .addTag(TAG)
                .setInputData(
                    workDataOf(
                        BookingReminderWorker.KEY_SALON_ID to b.salonId,
                        BookingReminderWorker.KEY_BOOKING_ID to b.id,
                        BookingReminderWorker.KEY_KIND to reminder.kind.name,
                        BookingReminderWorker.KEY_TITLE to b.title,
                        BookingReminderWorker.KEY_STAFF to b.staffName,
                        BookingReminderWorker.KEY_START_MILLIS to b.start.toEpochMilli(),
                    ),
                )
                .build()
            workManager.enqueueUniqueWork(reminder.workName, ExistingWorkPolicy.REPLACE, request)
        }
        plan.cancel.forEach { workManager.cancelUniqueWork(it) }
    }

    override fun cancelAll() {
        workManager.cancelAllWorkByTag(TAG)
    }

    private companion object {
        const val TAG = "booking-reminder"
    }
}
