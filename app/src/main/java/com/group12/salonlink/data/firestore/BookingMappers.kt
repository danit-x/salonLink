package com.group12.salonlink.data.firestore

import com.group12.salonlink.data.firestore.FirestoreSchema.BookedServiceFields
import com.group12.salonlink.data.firestore.FirestoreSchema.BookingFields
import com.group12.salonlink.domain.model.BookedService
import com.group12.salonlink.domain.model.Booking
import com.group12.salonlink.domain.model.BookingStatus
import com.group12.salonlink.domain.model.BookingType

/*
 * Firestore ↔ domain conversions for bookings. Unit-tested in BookingMappersTest.
 */

/**
 * Domain → Firestore.
 *
 * `createdAt` / `updatedAt` are NOT written here: the booking engine (Phase 3) sets them to
 * FieldValue.serverTimestamp() so a phone with a wrong clock can't fake them.
 */
fun Booking.toFirestoreMap(): Map<String, Any?> = mapOf(
    BookingFields.SALON_ID to salonId,
    BookingFields.CUSTOMER_ID to customerId,
    BookingFields.CUSTOMER_NAME to customerName,
    BookingFields.STAFF_ID to staffId,
    BookingFields.STAFF_NAME to staffName,
    BookingFields.SERVICES to services.map { it.toFirestoreMap() },
    BookingFields.START to start.toTimestamp(),
    BookingFields.END to end.toTimestamp(),
    BookingFields.TOTAL_PRICE to totalPrice,
    BookingFields.STATUS to status.name,
    BookingFields.TYPE to type.name,
    BookingFields.NOTE to note,
    BookingFields.CHECKED_IN_AT to checkedInAt?.toTimestamp(),
)

fun BookedService.toFirestoreMap(): Map<String, Any?> = mapOf(
    BookedServiceFields.SERVICE_ID to serviceId,
    BookedServiceFields.NAME to name,
    BookedServiceFields.DURATION_MIN to durationMin.toLong(),
    BookedServiceFields.PRICE to price,
)

/**
 * Firestore → domain.
 *
 * Returns null if the booking has no valid time range or staff member. Such a document is
 * broken, and showing it would mislead the customer more than hiding it.
 *
 * @param salonIdFallback the salon from the document path. Used if the `salonId` field is missing.
 */
fun bookingFromMap(id: String, data: Map<String, Any?>, salonIdFallback: String): Booking? {
    val start = data[BookingFields.START].asInstant() ?: return null
    val end = data[BookingFields.END].asInstant() ?: return null
    if (end <= start) return null
    val staffId = data[BookingFields.STAFF_ID] as? String ?: return null

    val services = (data[BookingFields.SERVICES] as? List<*>).orEmpty()
        .mapNotNull { (it as? Map<*, *>)?.let(::bookedServiceFromMap) }

    return Booking(
        id = id,
        salonId = data[BookingFields.SALON_ID] as? String ?: salonIdFallback,
        customerId = data[BookingFields.CUSTOMER_ID] as? String,
        customerName = data[BookingFields.CUSTOMER_NAME] as? String ?: "",
        staffId = staffId,
        staffName = data[BookingFields.STAFF_NAME] as? String ?: "",
        services = services,
        start = start,
        end = end,
        totalPrice = data[BookingFields.TOTAL_PRICE].asLong() ?: services.sumOf { it.price },
        status = BookingStatus.fromValue(data[BookingFields.STATUS] as? String),
        type = BookingType.fromValue(data[BookingFields.TYPE] as? String),
        note = data[BookingFields.NOTE] as? String,
        createdAt = data[BookingFields.CREATED_AT].asInstant(),
        updatedAt = data[BookingFields.UPDATED_AT].asInstant(),
        checkedInAt = data[BookingFields.CHECKED_IN_AT].asInstant(),
    )
}

private fun bookedServiceFromMap(data: Map<*, *>): BookedService? {
    val serviceId = data[BookedServiceFields.SERVICE_ID] as? String ?: return null
    return BookedService(
        serviceId = serviceId,
        name = data[BookedServiceFields.NAME] as? String ?: "",
        durationMin = data[BookedServiceFields.DURATION_MIN].asInt() ?: 0,
        price = data[BookedServiceFields.PRICE].asLong() ?: 0,
    )
}
