package com.group12.salonlink.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.group12.salonlink.core.di.DefaultDispatcher
import com.group12.salonlink.data.firestore.FirestoreSchema
import com.group12.salonlink.data.firestore.FirestoreSchema.NotificationFields
import com.group12.salonlink.data.firestore.notificationFromMap
import com.group12.salonlink.data.firestore.observe
import com.group12.salonlink.data.firestore.toDataException
import com.group12.salonlink.data.firestore.toFirestoreMap
import com.group12.salonlink.domain.model.AppNotification
import com.group12.salonlink.domain.repository.NotificationRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import com.group12.salonlink.data.firestore.awaitWrite

/** [NotificationRepository] backed by `users/{uid}/notifications`. */
@Singleton
class FirestoreNotificationRepository @Inject constructor(
    private val db: FirebaseFirestore,
    @param:DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : NotificationRepository {

    override fun observeNotifications(uid: String, limit: Long): Flow<List<AppNotification>> =
        feed(uid)
            .orderBy(NotificationFields.CREATED_AT, Query.Direction.DESCENDING)
            .limit(limit)
            .observe()
            .map { snapshot -> snapshot.documents.mapNotNull { it.toNotification() } }
            .flowOn(dispatcher)

    override suspend fun add(uid: String, notification: AppNotification) {
        try {
            feed(uid).document(notification.id).set(notification.toFirestoreMap()).awaitWrite()
        } catch (e: Exception) {
            throw e.toDataException()
        }
    }

    override suspend fun markRead(uid: String, ids: Collection<String>) {
        if (ids.isEmpty()) return
        try {
            val batch = db.batch()
            ids.forEach { batch.update(feed(uid).document(it), NotificationFields.READ, true) }
            batch.commit().awaitWrite()
        } catch (e: Exception) {
            throw e.toDataException()
        }
    }

    private fun feed(uid: String) =
        db.collection(FirestoreSchema.USERS).document(uid).collection(FirestoreSchema.NOTIFICATIONS)

    private fun DocumentSnapshot.toNotification(): AppNotification? = data?.let { notificationFromMap(id, it) }
}
