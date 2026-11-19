package com.group12.salonlink.domain.repository

import com.group12.salonlink.domain.model.QueueBoard
import com.group12.salonlink.domain.model.QueueEntry
import com.group12.salonlink.domain.model.WaitlistEntry
import com.group12.salonlink.domain.model.WaitlistOffer
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** The waitlist (`salons/{id}/waitlist`). Functions throw / flows fail with DataException. */
interface WaitlistRepository {

    /** Adds [entry] (status WAITING). Returns its id. */
    suspend fun join(entry: WaitlistEntry): String

    /** The customer's own entries in every salon, newest first. */
    fun observeMine(customerId: String): Flow<List<WaitlistEntry>>

    /** Every entry of the salon for [date] (used to pass on a freed slot). */
    suspend fun entriesFor(salonId: String, date: LocalDate): List<WaitlistEntry>

    /** WAITING (or expired offer) → OFFERED with [offer]. */
    suspend fun offer(salonId: String, entryId: String, offer: WaitlistOffer)

    /** The customer took the offer (after the booking was saved). */
    suspend fun markAccepted(salonId: String, entryId: String)

    /** The customer no longer wants a slot (or declined the offer). */
    suspend fun leave(salonId: String, entryId: String)
}

/** The anonymous live-queue boards (`salons/{id}/queue/{staffId}`). */
interface QueueRepository {

    fun observeBoard(salonId: String, staffId: String): Flow<QueueBoard?>

    /** Replaces the board of [staffId] (staff / owner only). */
    suspend fun publish(salonId: String, staffId: String, entries: List<QueueEntry>)
}
