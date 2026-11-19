package com.group12.salonlink.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.group12.salonlink.core.di.DefaultDispatcher
import com.group12.salonlink.data.firestore.FirestoreSchema
import com.group12.salonlink.data.firestore.FirestoreSchema.BlockFields
import com.group12.salonlink.data.firestore.FirestoreSchema.SalonFields
import com.group12.salonlink.data.firestore.FirestoreSchema.StaffFields
import com.group12.salonlink.data.firestore.observe
import com.group12.salonlink.data.firestore.salonFromMap
import com.group12.salonlink.data.firestore.salonServiceFromMap
import com.group12.salonlink.data.firestore.staffBlockFromMap
import com.group12.salonlink.data.firestore.staffFromMap
import com.group12.salonlink.data.firestore.toTimestamp
import com.group12.salonlink.domain.model.Salon
import com.group12.salonlink.domain.model.SalonService
import com.group12.salonlink.domain.model.Staff
import com.group12.salonlink.domain.model.StaffBlock
import com.group12.salonlink.domain.repository.SalonRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * [SalonRepository] backed by Cloud Firestore (`salons/{salonId}/...`).
 *
 * Mapping runs on the [DefaultDispatcher] (`flowOn`) so parsing a long list never blocks
 * the main thread, while Firestore itself already does its network work in the background.
 */
@Singleton
class FirestoreSalonRepository @Inject constructor(
    private val db: FirebaseFirestore,
    @param:DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : SalonRepository {

    override fun observeSalons(): Flow<List<Salon>> =
        db.collection(FirestoreSchema.SALONS)
            .orderBy(SalonFields.NAME)
            .observe()
            .map { snapshot -> snapshot.documents.map { salonFromMap(it.id, it.data.orEmpty()) } }
            .flowOn(dispatcher)

    override fun observeSalon(salonId: String): Flow<Salon?> =
        salonDoc(salonId)
            .observe()
            .map { doc -> if (doc.exists()) salonFromMap(doc.id, doc.data.orEmpty()) else null }
            .flowOn(dispatcher)

    override fun observeServices(salonId: String, includeInactive: Boolean): Flow<List<SalonService>> =
        salonDoc(salonId).collection(FirestoreSchema.SERVICES)
            .observe()
            .map { snapshot ->
                // WHY filter and sort here instead of in the query: a salon has a handful of
                // services, and `where(active) + orderBy(name)` would need a composite index.
                snapshot.documents
                    .mapNotNull { salonServiceFromMap(it.id, it.data.orEmpty()) }
                    .filter { includeInactive || it.active }
                    .sortedBy { it.name.lowercase() }
            }
            .flowOn(dispatcher)

    override fun observeStaff(salonId: String, includeInactive: Boolean): Flow<List<Staff>> =
        salonDoc(salonId).collection(FirestoreSchema.STAFF)
            .orderBy(StaffFields.NAME)
            .observe()
            .map { snapshot -> snapshot.documents.map { staffFromMap(it.id, it.data.orEmpty()) }.filter { includeInactive || it.active } }
            .flowOn(dispatcher)

    override fun observeStaffMember(salonId: String, staffId: String): Flow<Staff?> =
        salonDoc(salonId).collection(FirestoreSchema.STAFF).document(staffId)
            .observe()
            .map { doc -> if (doc.exists()) staffFromMap(doc.id, doc.data.orEmpty()) else null }
            .flowOn(dispatcher)

    override fun observeBlocks(salonId: String, staffId: String, from: Instant, to: Instant): Flow<List<StaffBlock>> =
        salonDoc(salonId).collection(FirestoreSchema.STAFF).document(staffId).collection(FirestoreSchema.BLOCKS)
            // WHY query on `end` only: "ends after `from`" uses Firestore's automatic single-field
            // index. Adding "starts before `to`" would be a second range filter needing a
            // composite index, and a staff member only has a few future blocks anyway.
            .whereGreaterThan(BlockFields.END, from.toTimestamp())
            .orderBy(BlockFields.END, Query.Direction.ASCENDING)
            .observe()
            .map { snapshot ->
                snapshot.documents
                    .mapNotNull { staffBlockFromMap(it.id, it.data.orEmpty()) }
                    .filter { it.overlaps(from, to) }
                    .sortedBy { it.start }
            }
            .flowOn(dispatcher)

    private fun salonDoc(salonId: String) = db.collection(FirestoreSchema.SALONS).document(salonId)
}
