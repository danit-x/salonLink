package com.group12.salonlink.data.repository

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.group12.salonlink.data.firestore.FirestoreSchema
import com.group12.salonlink.data.firestore.FirestoreSchema.PhotoFields
import com.group12.salonlink.data.firestore.FirestoreSchema.StyleFields
import com.group12.salonlink.data.firestore.awaitWrite
import com.group12.salonlink.data.firestore.observe
import com.group12.salonlink.data.firestore.stylePhotoFromMap
import com.group12.salonlink.data.firestore.styleProfileFromMap
import com.group12.salonlink.data.firestore.toDataException
import com.group12.salonlink.data.firestore.toFirestoreMap
import com.group12.salonlink.domain.model.PhotoLabel
import com.group12.salonlink.domain.model.StylePhoto
import com.group12.salonlink.domain.model.StyleProfile
import com.group12.salonlink.domain.repository.StyleRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** [StyleRepository] on Firestore. The rules make both locations private to the customer. */
@Singleton
class FirestoreStyleRepository @Inject constructor(
    private val db: FirebaseFirestore,
) : StyleRepository {

    private fun user(uid: String) = db.collection(FirestoreSchema.USERS).document(uid)
    private fun profileDoc(uid: String) =
        user(uid).collection(FirestoreSchema.STYLE).document(FirestoreSchema.STYLE_PROFILE_DOC)
    private fun photos(uid: String) = user(uid).collection(FirestoreSchema.PHOTOS)

    override fun observeProfile(uid: String): Flow<StyleProfile> =
        profileDoc(uid).observe().map { styleProfileFromMap(it.data) }

    override suspend fun saveProfile(uid: String, profile: StyleProfile) = write {
        profileDoc(uid)
            .set(profile.toFirestoreMap() + (StyleFields.UPDATED_AT to FieldValue.serverTimestamp()))
            .awaitWrite()
    }

    override fun observePhotos(uid: String): Flow<List<StylePhoto>> =
        photos(uid).orderBy(PhotoFields.CREATED_AT, Query.Direction.DESCENDING).observe()
            .map { snap -> snap.documents.mapNotNull { d -> d.data?.let { stylePhotoFromMap(d.id, it) } } }

    override suspend fun addPhoto(uid: String, jpeg: ByteArray, label: PhotoLabel): String = write {
        val ref = photos(uid).document()
        ref.set(
            mapOf(
                PhotoFields.JPEG to Blob.fromBytes(jpeg),
                PhotoFields.LABEL to label.name,
                PhotoFields.CREATED_AT to FieldValue.serverTimestamp(),
            ),
        ).awaitWrite()
        ref.id
    }

    override suspend fun deletePhoto(uid: String, photoId: String) = write {
        photos(uid).document(photoId).delete().awaitWrite()
    }

    private suspend fun <T> write(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw e.toDataException()
    }
}
