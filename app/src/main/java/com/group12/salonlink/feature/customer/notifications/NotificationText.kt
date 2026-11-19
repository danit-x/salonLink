package com.group12.salonlink.feature.customer.notifications

import android.content.Context
import androidx.annotation.StringRes
import com.group12.salonlink.R
import com.group12.salonlink.core.util.SalonTime
import com.group12.salonlink.domain.model.AppNotification
import com.group12.salonlink.domain.model.BookingStatus
import com.group12.salonlink.domain.model.NotificationType

/**
 * Turns a stored [AppNotification] into (title, body) text in the phone's language.
 * Used by the feed screen and by the phone notifications, so both always say the same thing.
 */
object NotificationText {

    @StringRes
    fun titleRes(n: AppNotification): Int = when (n.type) {
        NotificationType.BOOKING_CONFIRMED -> R.string.notification_booking_confirmed
        NotificationType.BOOKING_CANCELLED -> R.string.notification_booking_cancelled
        NotificationType.WAITLIST_OFFER -> R.string.notification_waitlist_offer
        NotificationType.STATUS_CHANGED -> when (n.status) {
            BookingStatus.CHECKED_IN, BookingStatus.WAITING -> R.string.notification_checked_in
            BookingStatus.IN_SERVICE -> R.string.notification_in_service
            BookingStatus.COMPLETED -> R.string.notification_completed
            BookingStatus.MISSED -> R.string.notification_missed
            else -> R.string.notification_status_other
        }
        NotificationType.UNKNOWN -> R.string.notification_status_other
    }

    fun of(context: Context, n: AppNotification): Pair<String, String> {
        val time = "${SalonTime.formatShortDate(n.start)}, ${SalonTime.formatTime(n.start)}"
        val body = if (n.type == NotificationType.WAITLIST_OFFER) {
            context.getString(R.string.waitlist_offer_body, time)
        } else {
            context.getString(R.string.notification_body, n.title, n.staffName, SalonTime.formatShortDate(n.start), SalonTime.formatTime(n.start))
        }
        return context.getString(titleRes(n)) to body
    }
}
