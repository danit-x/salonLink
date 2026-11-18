package com.group12.salonlink.data.firestore

import com.group12.salonlink.domain.model.BookedService
import com.group12.salonlink.domain.model.Booking
import com.group12.salonlink.domain.model.BookingStatus
import com.group12.salonlink.domain.model.BookingType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookingMappersTest {

    /** The booking on the Figma Discovery card: Thu 8 Oct, 5:30 pm – 6:20 pm (Colombo) = 12:00–12:50 UTC. */
    private val figmaBooking = Booking(
        id = "b1",
        salonId = "aura",
        customerId = "kavindu",
        customerName = "Kavindu Perera",
        staffId = "kasun",
        staffName = "Kasun Perera",
        services = listOf(
            BookedService("haircut", "Haircut", 30, 1500),
            BookedService("beard-trim", "Beard Trim", 20, 800),
        ),
        start = Instant.parse("2026-10-08T12:00:00Z"),
        end = Instant.parse("2026-10-08T12:50:00Z"),
        totalPrice = 2300,
        status = BookingStatus.BOOKED,
        type = BookingType.APPOINTMENT,
        note = "Keep the sides short",
    )

    @Test
    fun `a booking survives a round trip`() {
        assertEquals(figmaBooking, bookingFromMap("b1", figmaBooking.toFirestoreMap(), salonIdFallback = "aura"))
    }

    @Test
    fun `server timestamps are not written by the app`() {
        val keys = figmaBooking.toFirestoreMap().keys
        assertEquals(false, "createdAt" in keys)
        assertEquals(false, "updatedAt" in keys)
    }

    @Test
    fun `title and duration come from the booked services`() {
        assertEquals("Haircut & Beard Trim", figmaBooking.title)
        assertEquals(50, figmaBooking.durationMin)
    }

    @Test
    fun `a booking without valid times or staff is dropped`() {
        val data = figmaBooking.toFirestoreMap()
        assertNull(bookingFromMap("b1", data - "start", "aura"))
        assertNull(bookingFromMap("b1", data - "staffId", "aura"))
        assertNull(bookingFromMap("b1", data + ("end" to data["start"]), "aura"))
    }

    @Test
    fun `the salon id falls back to the document path`() {
        val booking = bookingFromMap("b1", figmaBooking.toFirestoreMap() - "salonId", salonIdFallback = "from-path")
        assertEquals("from-path", booking?.salonId)
    }

    @Test
    fun `a missing total is recomputed from the services`() {
        assertEquals(2300L, bookingFromMap("b1", figmaBooking.toFirestoreMap() - "totalPrice", "aura")?.totalPrice)
    }

    @Test
    fun `a cancelled or past booking is not upcoming`() {
        val before = Instant.parse("2026-10-08T11:00:00Z")
        assertEquals(true, figmaBooking.isUpcoming(before))
        assertEquals(false, figmaBooking.copy(status = BookingStatus.CANCELLED).isUpcoming(before))
        assertEquals(false, figmaBooking.isUpcoming(Instant.parse("2026-10-08T13:00:00Z")))
    }
}
