package com.group12.salonlink.testing

import com.group12.salonlink.domain.model.QueueBoard
import com.group12.salonlink.domain.model.QueueEntry
import com.group12.salonlink.domain.model.WaitlistEntry
import com.group12.salonlink.domain.model.WaitlistOffer
import com.group12.salonlink.domain.model.WaitlistStatus
import com.group12.salonlink.domain.repository.QueueRepository
import com.group12.salonlink.domain.repository.WaitlistRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory waitlist. */
class FakeWaitlistRepository : WaitlistRepository {
    val entries = MutableStateFlow<Map<String, WaitlistEntry>>(emptyMap())
    private var nextId = 1

    override suspend fun join(entry: WaitlistEntry): String {
        val id = "w${nextId++}"
        entries.value = entries.value + (id to entry.copy(id = id, status = WaitlistStatus.WAITING, offer = null))
        return id
    }

    override fun observeMine(customerId: String): Flow<List<WaitlistEntry>> =
        entries.map { all -> all.values.filter { it.customerId == customerId } }

    override suspend fun entriesFor(salonId: String, date: LocalDate): List<WaitlistEntry> =
        entries.value.values.filter { it.salonId == salonId && it.date == date }

    override suspend fun offer(salonId: String, entryId: String, offer: WaitlistOffer) = update(entryId) {
        it.copy(status = WaitlistStatus.OFFERED, offer = offer)
    }

    /** Simulates the rules refusing ACCEPTED (the offer ran out while the booking was saved). */
    var refuseAccept = false

    override suspend fun markAccepted(salonId: String, entryId: String) {
        if (refuseAccept) throw com.group12.salonlink.domain.model.DataException(com.group12.salonlink.domain.model.DataError.PERMISSION_DENIED)
        update(entryId) { it.copy(status = WaitlistStatus.ACCEPTED) }
    }

    override suspend fun leave(salonId: String, entryId: String) = update(entryId) { it.copy(status = WaitlistStatus.LEFT) }

    private fun update(id: String, change: (WaitlistEntry) -> WaitlistEntry) {
        val e = entries.value.getValue(id)
        entries.value = entries.value + (id to change(e))
    }
}

/** In-memory queue boards. */
class FakeQueueRepository : QueueRepository {
    val boards = MutableStateFlow<Map<String, QueueBoard>>(emptyMap())
    var publishCalls = 0
        private set

    override fun observeBoard(salonId: String, staffId: String): Flow<QueueBoard?> = boards.map { it[staffId] }

    override suspend fun publish(salonId: String, staffId: String, entries: List<QueueEntry>) {
        publishCalls++
        boards.value = boards.value + (staffId to QueueBoard(staffId, entries, null))
    }
}
