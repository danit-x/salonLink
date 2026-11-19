package com.group12.salonlink.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.group12.salonlink.data.firestore.FirestoreSchema
import com.group12.salonlink.data.firestore.FirestoreSchema.InviteFields
import com.group12.salonlink.data.firestore.toDataException
import com.group12.salonlink.data.firestore.toFirestoreMap
import com.group12.salonlink.domain.model.Role
import com.group12.salonlink.domain.model.Salon
import com.group12.salonlink.domain.model.SalonService
import com.group12.salonlink.domain.model.Staff
import com.group12.salonlink.domain.model.StaffBlock
import com.group12.salonlink.domain.repository.SalonAdminRepository
import com.group12.salonlink.domain.validation.FormValidators
import javax.inject.Inject
import javax.inject.Singleton
import com.group12.salonlink.data.firestore.awaitWrite

/** [SalonAdminRepository] on Firestore. Every write is checked again by the rules (owner only). */
@Singleton
class FirestoreSalonAdminRepository @Inject constructor(
    private val db: FirebaseFirestore,
) : SalonAdminRepository {

    override suspend fun saveSalon(salon: Salon) = write {
        salonDoc(salon.id).set(salon.toFirestoreMap()).awaitWrite()
        Unit
    }

    override suspend fun saveService(salonId: String, service: SalonService): String = write {
        val collection = salonDoc(salonId).collection(FirestoreSchema.SERVICES)
        // WHY let Firestore pick the id for new services: random ids never collide, even if
        // two owners add a service at the same moment.
        val ref = if (service.id.isBlank()) collection.document() else collection.document(service.id)
        ref.set(service.toFirestoreMap()).awaitWrite()
        ref.id
    }

    override suspend fun saveStaff(salonId: String, staff: Staff): String = write {
        val collection = salonDoc(salonId).collection(FirestoreSchema.STAFF)
        val ref = if (staff.id.isBlank()) collection.document() else collection.document(staff.id)
        ref.set(staff.toFirestoreMap()).awaitWrite()
        ref.id
    }

    override suspend fun addBlock(salonId: String, staffId: String, block: StaffBlock) = write {
        blocks(salonId, staffId).add(block.toFirestoreMap()).awaitWrite()
        Unit
    }

    override suspend fun deleteBlock(salonId: String, staffId: String, blockId: String) = write {
        blocks(salonId, staffId).document(blockId).delete().awaitWrite()
        Unit
    }

    override suspend fun inviteStaff(salonId: String, staffId: String, email: String) = write {
        db.collection(FirestoreSchema.INVITES).document(FormValidators.normaliseEmail(email))
            .set(
                mapOf(
                    InviteFields.ROLE to Role.STAFF.name,
                    InviteFields.SALON_ID to salonId,
                    InviteFields.STAFF_ID to staffId,
                ),
            ).awaitWrite()
        Unit
    }

    private fun salonDoc(salonId: String) = db.collection(FirestoreSchema.SALONS).document(salonId)

    private fun blocks(salonId: String, staffId: String) =
        salonDoc(salonId).collection(FirestoreSchema.STAFF).document(staffId).collection(FirestoreSchema.BLOCKS)

    /** Turns Firebase errors into our DataException. */
    private suspend fun <T> write(block: suspend () -> T): T = try {
        block()
    } catch (e: Exception) {
        throw e.toDataException()
    }
}
