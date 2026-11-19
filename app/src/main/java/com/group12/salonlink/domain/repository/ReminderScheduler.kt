package com.group12.salonlink.domain.repository

import com.group12.salonlink.domain.reminder.ReminderPlan

/**
 * Schedules local appointment reminders on the phone.
 * The real one uses WorkManager, so reminders still fire after the app is closed or the phone restarts.
 */
interface ReminderScheduler {

    /** Applies [plan]: (re)schedules its reminders and cancels the rest. Safe to call often. */
    fun apply(plan: ReminderPlan)

    /** Removes every reminder (on sign-out, so the next person on this phone doesn't get them). */
    fun cancelAll()
}
