package com.group12.salonlink.data.firestore

import com.google.firebase.firestore.FieldValue
import com.group12.salonlink.data.firestore.FirestoreSchema.NotificationFields
import com.group12.salonlink.domain.model.AppNotification
import com.group12.salonlink.domain.model.BookingStatus
import com.group12.salonlink.domain.model.NotificationType

/** Domain → Firestore. `createdAt` is the server's clock; a new notification is always unread. */
fun AppNotification.toFirestoreMap(): Map<String, Any?> = mapOf(
    NotificationFields.TYPE to type.name,
    NotificationFields.SALON_ID to salonId,
    NotificationFields.BOOKING_ID to bookingId,
    NotificationFields.TITLE to title,
    NotificationFields.STAFF_NAME to staffName,
    NotificationFields.START to start.toTimestamp(),
    NotificationFields.STATUS to status?.name,
    NotificationFields.READ to false,
    NotificationFields.CREATED_AT to FieldValue.serverTimestamp(),
)

/** Firestore → domain. Null if the document has no usable start time or booking id. */
fun notificationFromMap(id: String, data: Map<String, Any?>): AppNotification? {
    val start = data[NotificationFields.START].asInstant() ?: return null
    val bookingId = data[NotificationFields.BOOKING_ID] as? String ?: return null
    return AppNotification(
        id = id,
        type = NotificationType.fromValue(data[NotificationFields.TYPE] as? String),
        salonId = data[NotificationFields.SALON_ID] as? String ?: "",
        bookingId = bookingId,
        title = data[NotificationFields.TITLE] as? String ?: "",
        staffName = data[NotificationFields.STAFF_NAME] as? String ?: "",
        start = start,
        read = data[NotificationFields.READ] as? Boolean ?: false,
        status = (data[NotificationFields.STATUS] as? String)?.let { BookingStatus.fromValue(it) },
        createdAt = data[NotificationFields.CREATED_AT].asInstant(),
    )
}
