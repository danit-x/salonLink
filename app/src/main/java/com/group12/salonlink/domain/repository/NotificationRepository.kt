package com.group12.salonlink.domain.repository

import com.group12.salonlink.domain.model.AppNotification
import kotlinx.coroutines.flow.Flow

/**
 * The in-app notification feed (`users/{uid}/notifications`).
 *
 * WHY Firestore and not push messages: sending FCM pushes needs a server (Cloud Functions,
 * Blaze plan). Instead, whoever causes an event writes a small document into the user's feed,
 * and the user's app listens to it live (PLAN.md §1).
 */
interface NotificationRepository {

    /** The newest notifications first (at most [limit]). */
    fun observeNotifications(uid: String, limit: Long = 50): Flow<List<AppNotification>>

    /** Writes [notification] into [uid]'s feed. Re-writing the same id is refused by the rules (no duplicates). */
    suspend fun add(uid: String, notification: AppNotification)

    /** Marks these notifications as read, in one batch. */
    suspend fun markRead(uid: String, ids: Collection<String>)
}
