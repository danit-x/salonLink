package com.group12.salonlink.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.group12.salonlink.core.di.DefaultDispatcher
import com.group12.salonlink.core.util.SalonTime
import com.group12.salonlink.data.firestore.FirestoreSchema
import com.group12.salonlink.data.firestore.FirestoreSchema.BookingFields
import com.group12.salonlink.data.firestore.bookingFromMap
import com.group12.salonlink.data.firestore.observe
import com.group12.salonlink.data.firestore.toTimestamp
import com.group12.salonlink.domain.model.Booking
import com.group12.salonlink.domain.repository.BookingRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * [BookingRepository] backed by Cloud Firestore (`salons/{salonId}/bookings/{id}`).
 *
 * The composite indexes these queries need are declared in `firebase/firestore.indexes.json`.
 */
@Singleton
class FirestoreBookingRepository @Inject constructor(
    private val db: FirebaseFirestore,
    @param:DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : BookingRepository {

    override fun observeBooking(salonId: String, bookingId: String): Flow<Booking?> =
        bookings(salonId).document(bookingId)
            .observe()
            .map { doc -> if (doc.exists()) doc.toBooking() else null }
            .flowOn(dispatcher)

    override fun observeCustomerBookings(customerId: String): Flow<List<Booking>> =
        // WHY a collection-group query: bookings live under each salon, but "My bookings"
        // shows them across all salons. collectionGroup("bookings") searches every
        // `.../bookings` collection at once. The `customerId` filter is REQUIRED: the security
        // rules only allow this query when it can only return the caller's own bookings.
        db.collectionGroup(FirestoreSchema.BOOKINGS)
            .whereEqualTo(BookingFields.CUSTOMER_ID, customerId)
            .orderBy(BookingFields.START, Query.Direction.DESCENDING)
            .observe()
            .map { snapshot -> snapshot.documents.mapNotNull { it.toBooking() } }
            .flowOn(dispatcher)

    override fun observeStaffDay(salonId: String, staffId: String, date: LocalDate): Flow<List<Booking>> =
        dayQuery(bookings(salonId).whereEqualTo(BookingFields.STAFF_ID, staffId), date)

    override fun observeSalonDay(salonId: String, date: LocalDate): Flow<List<Booking>> =
        dayQuery(bookings(salonId), date)

    // ---------------------------------------------------------------------------------

    /** Bookings starting within the salon-local day [date], i.e. [00:00, next 00:00) in Asia/Colombo. */
    private fun dayQuery(base: Query, date: LocalDate): Flow<List<Booking>> {
        val dayStart = SalonTime.startOfDay(date)
        val dayEnd = SalonTime.startOfDay(date.plusDays(1))
        return base
            .whereGreaterThanOrEqualTo(BookingFields.START, dayStart.toTimestamp())
            .whereLessThan(BookingFields.START, dayEnd.toTimestamp())
            .orderBy(BookingFields.START, Query.Direction.ASCENDING)
            .observe()
            .map { snapshot -> snapshot.documents.mapNotNull { it.toBooking() } }
            .flowOn(dispatcher)
    }

    private fun bookings(salonId: String) =
        db.collection(FirestoreSchema.SALONS).document(salonId).collection(FirestoreSchema.BOOKINGS)

    /** The salon id is also in the path (salons/{salonId}/bookings/{id}), used if the field is missing. */
    private fun DocumentSnapshot.toBooking(): Booking? =
        bookingFromMap(id, data.orEmpty(), salonIdFallback = reference.parent.parent?.id.orEmpty())
}
