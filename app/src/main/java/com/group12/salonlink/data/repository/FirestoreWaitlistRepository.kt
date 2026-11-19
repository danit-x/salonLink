package com.group12.salonlink.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.group12.salonlink.core.di.DefaultDispatcher
import com.group12.salonlink.data.firestore.FirestoreSchema
import com.group12.salonlink.data.firestore.FirestoreSchema.QueueFields
import com.group12.salonlink.data.firestore.FirestoreSchema.WaitlistFields
import com.group12.salonlink.data.firestore.observe
import com.group12.salonlink.data.firestore.queueBoardFromMap
import com.group12.salonlink.data.firestore.toDataException
import com.group12.salonlink.data.firestore.toFirestoreMap
import com.group12.salonlink.data.firestore.waitlistEntryFromMap
import com.group12.salonlink.domain.model.QueueBoard
import com.group12.salonlink.domain.model.QueueEntry
import com.group12.salonlink.domain.model.WaitlistEntry
import com.group12.salonlink.domain.model.WaitlistOffer
import com.group12.salonlink.domain.model.WaitlistStatus
import com.group12.salonlink.domain.repository.QueueRepository
import com.group12.salonlink.domain.repository.WaitlistRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import com.group12.salonlink.data.firestore.awaitWrite
import kotlinx.coroutines.tasks.await

/** [WaitlistRepository] on `salons/{id}/waitlist`. */
@Singleton
class FirestoreWaitlistRepository @Inject constructor(
    private val db: FirebaseFirestore,
    @param:DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : WaitlistRepository {

    override suspend fun join(entry: WaitlistEntry): String = write {
        val ref = waitlist(entry.salonId).document()
        ref.set(entry.copy(status = WaitlistStatus.WAITING, offer = null).toFirestoreMap()).awaitWrite()
        ref.id
    }

    override fun observeMine(customerId: String): Flow<List<WaitlistEntry>> =
        // Collection-group query (all salons), allowed by the rules only when filtered to the caller.
        db.collectionGroup(FirestoreSchema.WAITLIST)
            .whereEqualTo(WaitlistFields.CUSTOMER_ID, customerId)
            .orderBy(WaitlistFields.CREATED_AT, Query.Direction.DESCENDING)
            .observe()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    val salonId = doc.reference.parent.parent?.id ?: return@mapNotNull null
                    waitlistEntryFromMap(doc.id, salonId, doc.data.orEmpty())
                }
            }
            .flowOn(dispatcher)

    override suspend fun entriesFor(salonId: String, date: LocalDate): List<WaitlistEntry> = write {
        // WHY only filter by date in the query: a day has a handful of entries, and adding
        // status/order would need a composite index. The use case filters and sorts.
        waitlist(salonId).whereEqualTo(WaitlistFields.DATE, date.toString()).get().await()
            .documents.mapNotNull { waitlistEntryFromMap(it.id, salonId, it.data.orEmpty()) }
    }

    override suspend fun offer(salonId: String, entryId: String, offer: WaitlistOffer) = write {
        waitlist(salonId).document(entryId).update(
            mapOf(WaitlistFields.STATUS to WaitlistStatus.OFFERED.name, WaitlistFields.OFFER to offer.toFirestoreMap()),
        ).awaitWrite()
        Unit
    }

    override suspend fun markAccepted(salonId: String, entryId: String) = setStatus(salonId, entryId, WaitlistStatus.ACCEPTED)

    override suspend fun leave(salonId: String, entryId: String) = setStatus(salonId, entryId, WaitlistStatus.LEFT)

    private suspend fun setStatus(salonId: String, entryId: String, status: WaitlistStatus) = write {
        waitlist(salonId).document(entryId).update(WaitlistFields.STATUS, status.name).awaitWrite()
        Unit
    }

    private fun waitlist(salonId: String) =
        db.collection(FirestoreSchema.SALONS).document(salonId).collection(FirestoreSchema.WAITLIST)

    private suspend fun <T> write(block: suspend () -> T): T = try {
        block()
    } catch (e: Exception) {
        throw e.toDataException()
    }
}

/** [QueueRepository] on `salons/{id}/queue/{staffId}`. */
@Singleton
class FirestoreQueueRepository @Inject constructor(
    private val db: FirebaseFirestore,
    @param:DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : QueueRepository {

    override fun observeBoard(salonId: String, staffId: String): Flow<QueueBoard?> =
        board(salonId, staffId).observe()
            .map { doc -> if (doc.exists()) queueBoardFromMap(staffId, doc.data.orEmpty()) else null }
            .flowOn(dispatcher)

    override suspend fun publish(salonId: String, staffId: String, entries: List<QueueEntry>) {
        try {
            board(salonId, staffId).set(
                mapOf(QueueFields.ENTRIES to entries.map { it.toFirestoreMap() }, QueueFields.UPDATED_AT to FieldValue.serverTimestamp()),
            ).awaitWrite()
        } catch (e: Exception) {
            throw e.toDataException()
        }
    }

    private fun board(salonId: String, staffId: String) =
        db.collection(FirestoreSchema.SALONS).document(salonId).collection(FirestoreSchema.QUEUE).document(staffId)
}
