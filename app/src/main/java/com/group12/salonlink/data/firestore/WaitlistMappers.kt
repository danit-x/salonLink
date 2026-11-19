package com.group12.salonlink.data.firestore

import com.google.firebase.firestore.FieldValue
import com.group12.salonlink.data.firestore.FirestoreSchema.QueueFields
import com.group12.salonlink.data.firestore.FirestoreSchema.WaitlistFields
import com.group12.salonlink.domain.model.BookingStatus
import com.group12.salonlink.domain.model.BookingType
import com.group12.salonlink.domain.model.QueueBoard
import com.group12.salonlink.domain.model.QueueEntry
import com.group12.salonlink.domain.model.WaitlistEntry
import com.group12.salonlink.domain.model.WaitlistOffer
import com.group12.salonlink.domain.model.WaitlistStatus
import java.time.LocalDate

// ----- Waitlist -----

fun WaitlistEntry.toFirestoreMap(): Map<String, Any?> = mapOf(
    WaitlistFields.CUSTOMER_ID to customerId,
    WaitlistFields.STAFF_ID to staffId,
    WaitlistFields.SERVICE_IDS to serviceIds,
    WaitlistFields.DURATION_MIN to durationMin.toLong(),
    WaitlistFields.DATE to date.toString(),
    WaitlistFields.WINDOW_START to windowStart.toTimestamp(),
    WaitlistFields.WINDOW_END to windowEnd.toTimestamp(),
    WaitlistFields.STATUS to status.name,
    WaitlistFields.OFFER to offer?.toFirestoreMap(),
    WaitlistFields.CREATED_AT to FieldValue.serverTimestamp(),
)

fun WaitlistOffer.toFirestoreMap(): Map<String, Any> = mapOf(
    "start" to start.toTimestamp(),
    "end" to end.toTimestamp(),
    "staffId" to staffId,
    "expiresAt" to expiresAt.toTimestamp(),
)

private fun offerFromMap(raw: Any?): WaitlistOffer? {
    val m = raw as? Map<*, *> ?: return null
    return WaitlistOffer(
        start = m["start"].asInstant() ?: return null,
        end = m["end"].asInstant() ?: return null,
        staffId = m["staffId"] as? String ?: return null,
        expiresAt = m["expiresAt"].asInstant() ?: return null,
    )
}

/** Null if the entry is missing its day, window or account (can't be offered anything sensible). */
fun waitlistEntryFromMap(id: String, salonId: String, data: Map<String, Any?>): WaitlistEntry? {
    val date = (data[WaitlistFields.DATE] as? String)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    return WaitlistEntry(
        id = id,
        salonId = salonId,
        customerId = data[WaitlistFields.CUSTOMER_ID] as? String ?: return null,
        staffId = data[WaitlistFields.STAFF_ID] as? String ?: return null,
        serviceIds = data[WaitlistFields.SERVICE_IDS].asStringList(),
        durationMin = data[WaitlistFields.DURATION_MIN].asInt() ?: return null,
        date = date,
        windowStart = data[WaitlistFields.WINDOW_START].asInstant() ?: return null,
        windowEnd = data[WaitlistFields.WINDOW_END].asInstant() ?: return null,
        status = WaitlistStatus.fromValue(data[WaitlistFields.STATUS] as? String),
        offer = offerFromMap(data[WaitlistFields.OFFER]),
        createdAt = data[WaitlistFields.CREATED_AT].asInstant(),
    )
}

// ----- Queue board -----

fun QueueEntry.toFirestoreMap(): Map<String, Any?> = mapOf(
    "bookingId" to bookingId,
    "start" to start.toTimestamp(),
    "durationMin" to durationMin.toLong(),
    "status" to status.name,
    "type" to type.name,
    "since" to since?.toTimestamp(),
)

fun queueBoardFromMap(staffId: String, data: Map<String, Any?>): QueueBoard = QueueBoard(
    staffId = staffId,
    entries = (data[QueueFields.ENTRIES] as? List<*>).orEmpty().mapNotNull { raw ->
        val m = raw as? Map<*, *> ?: return@mapNotNull null
        QueueEntry(
            bookingId = m["bookingId"] as? String ?: return@mapNotNull null,
            start = m["start"].asInstant() ?: return@mapNotNull null,
            durationMin = m["durationMin"].asInt() ?: return@mapNotNull null,
            status = BookingStatus.fromValue(m["status"] as? String),
            type = BookingType.fromValue(m["type"] as? String),
            since = m["since"].asInstant(),
        )
    },
    updatedAt = data[QueueFields.UPDATED_AT].asInstant(),
)
