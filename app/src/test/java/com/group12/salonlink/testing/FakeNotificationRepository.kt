package com.group12.salonlink.testing

import com.group12.salonlink.domain.model.AppNotification
import com.group12.salonlink.domain.model.DataError
import com.group12.salonlink.domain.model.DataException
import com.group12.salonlink.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory feed. Refuses a second write of the same id, like the security rules. */
class FakeNotificationRepository : NotificationRepository {
    /** (uid, notification) in the order they were written. */
    val added = mutableListOf<Pair<String, AppNotification>>()
    private val feed = MutableStateFlow<List<Pair<String, AppNotification>>>(emptyList())

    /** When true, the next add() fails with NETWORK. */
    var failNext = false

    override fun observeNotifications(uid: String, limit: Long): Flow<List<AppNotification>> =
        feed.map { all -> all.filter { it.first == uid }.map { it.second }.reversed().take(limit.toInt()) }

    override suspend fun add(uid: String, notification: AppNotification) {
        if (failNext) { failNext = false; throw DataException(DataError.NETWORK) }
        if (feed.value.any { it.first == uid && it.second.id == notification.id }) throw DataException(DataError.PERMISSION_DENIED)
        added += uid to notification
        feed.value = feed.value + (uid to notification)
    }

    override suspend fun markRead(uid: String, ids: Collection<String>) {
        feed.value = feed.value.map { (u, n) -> if (u == uid && n.id in ids) u to n.copy(read = true) else u to n }
    }
}
