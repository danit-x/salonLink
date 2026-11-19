package com.group12.salonlink.domain.model

import java.time.Instant

/**
 * One chair-time on a barber's queue board, WITHOUT any name (customers can read the board).
 *
 * @property since for IN_SERVICE: when the service started (to estimate the time left).
 */
data class QueueEntry(
    val bookingId: String,
    val start: Instant,
    val durationMin: Int,
    val status: BookingStatus,
    val type: BookingType,
    val since: Instant? = null,
)

/**
 * Today's anonymous queue of one barber (`salons/{salonId}/queue/{staffId}`), published by the
 * salon's staff app whenever the day's bookings change (Figma "Live queue").
 *
 * WHY a separate board: customers may not read other people's bookings (T09), but they need to
 * know how many people are ahead of them. The board has exactly that, and nothing more.
 */
data class QueueBoard(
    val staffId: String,
    val entries: List<QueueEntry>,
    val updatedAt: Instant?,
)
