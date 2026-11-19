package com.group12.salonlink.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.group12.salonlink.core.di.DefaultDispatcher
import com.group12.salonlink.core.util.SalonTime
import com.group12.salonlink.data.firestore.FirestoreSchema
import com.group12.salonlink.data.firestore.FirestoreSchema.BookingFields
import com.group12.salonlink.data.firestore.FirestoreSchema.SlotLockFields
import com.group12.salonlink.data.firestore.asLong
import com.group12.salonlink.data.firestore.bookingFromMap
import com.group12.salonlink.data.firestore.lockData
import com.group12.salonlink.data.firestore.observe
import com.group12.salonlink.data.firestore.slots
import com.group12.salonlink.data.firestore.toDataException
import com.group12.salonlink.data.firestore.toFirestoreMap
import com.group12.salonlink.data.firestore.toTimestamp
import com.group12.salonlink.domain.booking.SlotGrid
import com.group12.salonlink.domain.model.Booking
import com.group12.salonlink.domain.model.BookingStatus
import com.group12.salonlink.domain.model.DataError
import com.group12.salonlink.domain.model.DataException
import com.group12.salonlink.domain.repository.BookingRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

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

    override fun observeTakenSlots(salonId: String, staffId: String, date: LocalDate): Flow<Set<Long>> {
        val from = SlotGrid.slotOf(SalonTime.startOfDay(date))
        val to = SlotGrid.slotOf(SalonTime.startOfDay(date.plusDays(1)))
        return locks(salonId)
            .whereEqualTo(SlotLockFields.STAFF_ID, staffId)
            .whereGreaterThanOrEqualTo(SlotLockFields.SLOT, from)
            .whereLessThan(SlotLockFields.SLOT, to)
            .observe()
            .map { snapshot -> snapshot.documents.mapNotNull { it.get(SlotLockFields.SLOT).asLong() }.toSet() }
            .flowOn(dispatcher)
    }

    // ---------------------------------------------------------------------------------
    // Booking engine writes (PLAN.md §5). Both run as Firestore TRANSACTIONS.
    //
    // WHY a transaction: Firestore runs the function, then commits only if none of the
    // documents it read changed meanwhile. If two phones try to take 17:30 at the same moment,
    // one commit wins, the other is retried, re-reads the lock the winner just wrote, and
    // fails with SLOT_TAKEN. So at most one booking can ever hold a slot (T03, NFR-03).
    //
    // The security rules enforce the same thing on the server, so even a hacked app that skips
    // this code cannot double-book (see firebase/firestore.rules, "Booking engine").
    //
    // Note: transactions need a connection. Offline they fail fast with NETWORK.
    // ---------------------------------------------------------------------------------

    override suspend fun createBooking(booking: Booking): Booking = runEngine {
        val bookingRef = bookings(booking.salonId).document(booking.id)
        val lockRefs = booking.slots.map { slot -> slot to lockRef(booking.salonId, booking.staffId, slot) }

        db.runTransaction { tx ->
            // 1) Idempotency (T11): if this id was already saved (e.g. the first attempt's
            //    reply was lost on a bad network), return it instead of booking twice.
            //    Firestore requires ALL reads before ANY write, so we read everything first.
            val existing = tx.get(bookingRef)
            val lockSnaps = lockRefs.map { (_, ref) -> tx.get(ref) }
            if (existing.exists()) {
                return@runTransaction existing.toBooking()
                    ?: throw DataException(DataError.UNKNOWN)
            }

            // 2) Is every slot still free? A lock with OUR booking id can't exist without the
            //    booking (they're written together), so any existing lock means "taken".
            if (lockSnaps.any { it.exists() }) throw DataException(DataError.SLOT_TAKEN)

            // 3) Write the booking and all its locks together: all or nothing.
            tx.set(bookingRef, booking.toFirestoreMap() + serverTimes(created = true))
            lockRefs.forEach { (slot, ref) -> tx.set(ref, booking.lockData(slot)) }
            booking
        }.await()
    }

    override suspend fun cancelBooking(salonId: String, bookingId: String) = runEngine {
        val bookingRef = bookings(salonId).document(bookingId)

        db.runTransaction { tx ->
            val booking = tx.get(bookingRef).toBooking() ?: throw DataException(DataError.NOT_FOUND)
            if (booking.status == BookingStatus.CANCELLED) return@runTransaction  // already done: retry-safe
            if (!booking.status.canTransitionTo(BookingStatus.CANCELLED)) {
                throw DataException(DataError.NOT_ALLOWED)
            }

            // Read the locks first (reads before writes), then free only the ones that are ours.
            val lockRefs = booking.slots.map { lockRef(salonId, booking.staffId, it) }
            val ours = lockRefs.filter { ref ->
                tx.get(ref).getString(SlotLockFields.BOOKING_ID) == bookingId
            }

            tx.update(
                bookingRef,
                mapOf(BookingFields.STATUS to BookingStatus.CANCELLED.name) + serverTimes(created = false),
            )
            ours.forEach(tx::delete)
            // The waitlist offer for the freed time (OfferFreedSlotUseCase) arrives in Phase 7.
        }.await()
        Unit
    }

    override suspend fun updateStatus(salonId: String, bookingId: String, status: BookingStatus) = runEngine {
        require(status != BookingStatus.CANCELLED) { "Use cancelBooking() to cancel" }
        val bookingRef = bookings(salonId).document(bookingId)
        db.runTransaction { tx ->
            val booking = tx.get(bookingRef).toBooking() ?: throw DataException(DataError.NOT_FOUND)
            if (booking.status == status) return@runTransaction // already done: retry-safe
            if (!booking.status.canTransitionTo(status)) throw DataException(DataError.NOT_ALLOWED)

            // A no-show frees the rest of its time (e.g. for a walk-in), like a cancellation.
            val freedLocks = if (status == BookingStatus.MISSED) {
                booking.slots.map { lockRef(salonId, booking.staffId, it) }
                    .filter { ref -> tx.get(ref).getString(SlotLockFields.BOOKING_ID) == bookingId }
            } else {
                emptyList()
            }
            val changes = buildMap<String, Any> {
                put(BookingFields.STATUS, status.name)
                if (status == BookingStatus.CHECKED_IN) put(BookingFields.CHECKED_IN_AT, FieldValue.serverTimestamp())
                putAll(serverTimes(created = false))
            }
            tx.update(bookingRef, changes)
            freedLocks.forEach(tx::delete)
        }.await()
        Unit
    }

    // ---------------------------------------------------------------------------------

    /**
     * Runs [block] and turns any failure into our [DataException].
     * WHY unwrap causes: an exception thrown inside `runTransaction { }` can reach us wrapped
     * in a Firebase exception, and we want the original SLOT_TAKEN / NOT_ALLOWED back.
     */
    private suspend fun <T> runEngine(block: suspend () -> T): T = try {
        block()
    } catch (e: Exception) {
        val ours = generateSequence<Throwable>(e) { it.cause }.filterIsInstance<DataException>().firstOrNull()
        throw ours ?: e.toDataException()
    }

    /** createdAt / updatedAt come from Google's clock, not the phone's (which may be wrong). */
    private fun serverTimes(created: Boolean): Map<String, Any> = buildMap {
        put(BookingFields.UPDATED_AT, FieldValue.serverTimestamp())
        if (created) put(BookingFields.CREATED_AT, FieldValue.serverTimestamp())
    }

    private fun locks(salonId: String) =
        db.collection(FirestoreSchema.SALONS).document(salonId).collection(FirestoreSchema.SLOT_LOCKS)

    private fun lockRef(salonId: String, staffId: String, slot: Long) =
        locks(salonId).document(FirestoreSchema.slotLockId(staffId, slot))

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
