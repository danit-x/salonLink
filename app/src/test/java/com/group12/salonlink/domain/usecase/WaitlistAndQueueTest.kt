package com.group12.salonlink.domain.usecase

import com.group12.salonlink.core.util.SalonTime
import com.group12.salonlink.domain.model.BookedService
import com.group12.salonlink.domain.model.Booking
import com.group12.salonlink.domain.model.BookingStatus
import com.group12.salonlink.domain.model.BookingType
import com.group12.salonlink.domain.model.DataError
import com.group12.salonlink.domain.model.DataException
import com.group12.salonlink.domain.model.QueueEntry
import com.group12.salonlink.domain.model.Salon
import com.group12.salonlink.domain.model.SalonService
import com.group12.salonlink.domain.model.ServiceCategory
import com.group12.salonlink.domain.model.Staff
import com.group12.salonlink.domain.model.TimeRange
import com.group12.salonlink.domain.model.WaitlistEntry
import com.group12.salonlink.domain.model.WaitlistStatus
import com.group12.salonlink.domain.model.WeeklyHours
import com.group12.salonlink.domain.queue.QueueEtaCalculator
import com.group12.salonlink.testing.FakeBookingRepository
import com.group12.salonlink.testing.FakeNotificationRepository
import com.group12.salonlink.testing.FakeSalonRepository
import com.group12.salonlink.testing.FakeWaitlistRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Waitlist offers after a cancellation (T07, FR-11) and the live-queue ETA (T08). */
class WaitlistAndQueueTest {

    private val friday = LocalDate.of(2026, 12, 4)
    private fun at(h: Int, m: Int = 0) = SalonTime.toInstant(friday, LocalTime.of(h, m))
    private var now = SalonTime.toInstant(friday.minusDays(2), LocalTime.of(10, 0)) // Wednesday 10:00
    private val clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant(): Instant = now
    }
    private val hours = WeeklyHours(DayOfWeek.entries.associateWith { TimeRange(LocalTime.of(9, 0), LocalTime.of(19, 0)) })
    private val salons = FakeSalonRepository().apply {
        salons.value = mapOf("aura" to Salon("aura", "Aura Salon", "", "Colombo 05", null, hours))
        staff.value = listOf(Staff("kasun", "Kasun Perera", "", emptyList(), 4.9, 1, 1, null, emptyList(), hours))
        services.value = listOf(SalonService("haircut", "Haircut", "", ServiceCategory.HAIRCUT, 30, 1500))
    }
    private val bookings = FakeBookingRepository()
    private val notifications = FakeNotificationRepository()
    private val waitlist = FakeWaitlistRepository()
    private val availability = ObserveAvailabilityUseCase(salons, bookings, clock)
    private val create = CreateBookingUseCase(salons, bookings, availability, notifications, clock)
    private val offerFreed = OfferFreedSlotUseCase(waitlist, clock)
    private val cancel = CancelBookingUseCase(bookings, notifications, offerFreed, clock)
    private val accept = AcceptWaitlistOfferUseCase(create, waitlist, clock)

    private fun request(id: String, customer: String, start: Instant) =
        BookingRequest(id, "aura", "kasun", listOf("haircut"), start, customer, customer)

    private suspend fun join(customer: String, from: Int = 9, to: Int = 19) = waitlist.join(
        WaitlistEntry("", "aura", customer, "kasun", listOf("haircut"), 30, friday, at(from), at(to), WaitlistStatus.WAITING),
    )

    @Test
    fun `T07 - a cancellation offers the time to the first person waiting, only them`() = runTest {
        create(request("b1", "kavindu", at(17, 30)))
        val first = join("nimal")
        now = now.plusSeconds(60)
        val second = join("amali")

        cancel("aura", "b1")

        val entries = waitlist.entries.value
        assertEquals(WaitlistStatus.OFFERED, entries.getValue(first).status)
        assertEquals(at(17, 30), entries.getValue(first).offer?.start)
        assertEquals(WaitlistStatus.WAITING, entries.getValue(second).status)
    }

    @Test
    fun `the offer respects the customer's time window`() = runTest {
        create(request("b1", "kavindu", at(17, 30)))
        val morningOnly = join("nimal", from = 9, to = 12)
        cancel("aura", "b1")
        assertEquals(WaitlistStatus.WAITING, waitlist.entries.value.getValue(morningOnly).status)
    }

    @Test
    fun `accepting books the freed time through the engine, once`() = runTest {
        create(request("b1", "kavindu", at(17, 30)))
        val id = join("nimal")
        cancel("aura", "b1")
        val entry = waitlist.entries.value.getValue(id)

        val booking = accept(entry, "Nimal")
        accept(entry.copy(status = WaitlistStatus.OFFERED), "Nimal") // retry: same booking id, no duplicate

        assertEquals(at(17, 30), booking.start)
        assertEquals(WaitlistStatus.ACCEPTED, waitlist.entries.value.getValue(id).status)
        assertEquals(1, bookings.bookings.value.values.count { it.customerId == "nimal" })
    }

    @Test
    fun `an expired offer can't be accepted and goes back to the line`() = runTest {
        create(request("b1", "kavindu", at(17, 30)))
        val id = join("nimal")
        cancel("aura", "b1")
        now = now.plus(Duration.ofMinutes(11))
        val entry = waitlist.entries.value.getValue(id)
        try {
            accept(entry, "Nimal")
            fail("expected NOT_ALLOWED")
        } catch (e: DataException) {
            assertEquals(DataError.NOT_ALLOWED, e.error)
        }
        assertTrue(entry.isWaiting(now))
        assertNull(entry.activeOffer(now))
    }

    @Test
    fun `T03 - two offers for the same time still give only one booking`() = runTest {
        create(request("b1", "kavindu", at(17, 30)))
        val a = join("nimal")
        cancel("aura", "b1")
        // Someone books the freed time directly before Nimal accepts.
        create(request("b2", "amali", at(17, 30)))
        try {
            accept(waitlist.entries.value.getValue(a), "Nimal")
            fail("expected SLOT_TAKEN")
        } catch (e: DataException) {
            assertEquals(DataError.SLOT_TAKEN, e.error)
        }
    }

    // ----- Live queue ETA (T08) -----

    private fun q(id: String, h: Int, m: Int, min: Int, status: BookingStatus, type: BookingType = BookingType.APPOINTMENT, since: Instant? = null) =
        QueueEntry(id, at(h, m), min, status, type, since)

    @Test
    fun `T08 - walk-ins and bookings ahead push my start back`() {
        val board = listOf(
            q("chair", 10, 0, 30, BookingStatus.IN_SERVICE, since = at(10, 5)), // ends 10:35
            q("walkin", 10, 10, 20, BookingStatus.CHECKED_IN, BookingType.WALK_IN), // 10:35–10:55
            q("me", 10, 30, 30, BookingStatus.CHECKED_IN), // starts 10:55
            q("later", 11, 30, 30, BookingStatus.BOOKED),
        )
        val eta = QueueEtaCalculator.eta(board, "me", at(10, 20))
        assertNotNull(eta)
        assertEquals(3, eta!!.position)
        assertEquals(at(10, 55), eta.estimatedStart)
        assertEquals(35, eta.waitMinutes)
    }

    @Test
    fun `an empty chair means I start at my booked time, not earlier`() {
        val eta = QueueEtaCalculator.eta(listOf(q("me", 11, 0, 30, BookingStatus.BOOKED)), "me", at(10, 20))!!
        assertEquals(1, eta.position)
        assertEquals(at(11, 0), eta.estimatedStart)
    }

    @Test
    fun `someone overrunning in the chair is assumed to need a few more minutes`() {
        val board = listOf(
            q("chair", 10, 0, 30, BookingStatus.IN_SERVICE, since = at(10, 0)), // planned end 10:30, now 10:40
            q("me", 10, 30, 30, BookingStatus.CHECKED_IN),
        )
        assertEquals(at(10, 45), QueueEtaCalculator.eta(board, "me", at(10, 40))!!.estimatedStart)
    }

    @Test
    fun `finished or cancelled bookings are done`() {
        val eta = QueueEtaCalculator.eta(listOf(q("me", 10, 0, 30, BookingStatus.COMPLETED)), "me", at(11))!!
        assertTrue(eta.done)
        assertFalse(eta.inService)
    }

    @Test
    fun `board entries hide nothing needed and keep no names`() {
        val b = Booking("b", "aura", "kavindu", "Kavindu Perera", "kasun", "Kasun", listOf(BookedService("h", "Haircut", 30, 1500)),
            at(10), at(10, 30), 1500, BookingStatus.CANCELLED, BookingType.APPOINTMENT)
        assertTrue(QueueEtaCalculator.boardEntries(listOf(b)).isEmpty())
        val live = QueueEtaCalculator.boardEntries(listOf(b.copy(status = BookingStatus.BOOKED)))
        assertEquals(listOf("b"), live.map { it.bookingId })
    }
}
