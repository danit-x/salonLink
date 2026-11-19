package com.group12.salonlink.domain.model

import java.time.Instant

/**
 * What an in-app notification is about. The app turns the type plus the booking details into
 * localized text, so a notification reads correctly in every app language (Phase 9).
 */
enum class NotificationType {
    BOOKING_CONFIRMED,   // the customer booked
    BOOKING_CANCELLED,   // the booking was cancelled (by the customer or the salon)
    STATUS_CHANGED,      // staff checked the customer in, started or finished the service (Phase 6)
    WAITLIST_OFFER,      // an earlier slot freed up (Phase 7)
    UNKNOWN;

    companion object {
        fun fromValue(value: String?): NotificationType = entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}

/**
 * One entry in a user's notification feed, stored at `users/{uid}/notifications/{id}`.
 *
 * WHY a snapshot of the booking (title, staff, start) instead of only the booking id: the feed
 * lists many notifications at once and must not need one extra read per row.
 *
 * @property status for [NotificationType.STATUS_CHANGED]: the new status.
 * @property createdAt set by the server; null for a moment after a local write.
 */
data class AppNotification(
    val id: String,
    val type: NotificationType,
    val salonId: String,
    val bookingId: String,
    val title: String,
    val staffName: String,
    val start: Instant,
    val read: Boolean = false,
    val status: BookingStatus? = null,
    val createdAt: Instant? = null,
) {
    companion object {
        /**
         * Same booking + same event → same document id. WHY: retrying a write (or two phones
         * writing the same event) can never create duplicates in the feed.
         */
        fun idFor(type: NotificationType, bookingId: String, status: BookingStatus? = null): String =
            listOfNotNull(type.name.lowercase(), status?.name?.lowercase(), bookingId).joinToString("_")

        /** A notification describing [booking]. */
        fun about(booking: Booking, type: NotificationType, status: BookingStatus? = null) = AppNotification(
            id = idFor(type, booking.id, status),
            type = type,
            salonId = booking.salonId,
            bookingId = booking.id,
            title = booking.title,
            staffName = booking.staffName,
            start = booking.start,
            status = status,
        )
    }
}
