package com.group12.salonlink.domain.usecase

import com.group12.salonlink.core.util.SalonTime
import com.group12.salonlink.domain.booking.SlotProblem
import com.group12.salonlink.domain.model.BookingStatus
import com.group12.salonlink.domain.model.BookingType
import com.group12.salonlink.domain.model.DataError
import com.group12.salonlink.domain.model.DataException
import com.group12.salonlink.domain.model.Salon
import com.group12.salonlink.domain.model.SalonService
import com.group12.salonlink.domain.model.ServiceCategory
import com.group12.salonlink.domain.model.SlotUnavailableException
import com.group12.salonlink.domain.model.Staff
import com.group12.salonlink.domain.model.StaffBlock
import com.group12.salonlink.domain.model.TimeRange
import com.group12.salonlink.domain.model.WeeklyHours
import com.group12.salonlink.testing.FakeBookingRepository
import com.group12.salonlink.testing.FakeNotificationRepository
import com.group12.salonlink.testing.FakeWaitlistRepository
import com.group12.salonlink.testing.FakeSalonRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The Figma story: Kavindu books Haircut + Beard Trim with Kasun on Thursday 3 Dec 2026 at 5:30 pm
 * (proposal T02), plus the ways that can go wrong (T03, T11).
 */
class CreateBookingUseCaseTest {

    private val thursday = LocalDate.of(2026, 12, 3)
    private fun at(h: Int, m: Int = 0) = SalonTime.toInstant(thursday, LocalTime.of(h, m))

    // "Now" is Thursday 10:00 in Colombo.
    private val clock = Clock.fixed(at(10), ZoneOffset.UTC)
    private val nineToSeven = WeeklyHours(DayOfWeek.entries.associateWith { TimeRange(LocalTime.of(9, 0), LocalTime.of(19, 0)) })

    private val salons = FakeSalonRepository().apply {
        salons.value = mapOf("aura" to Salon("aura", "Aura Salon", "12 Galle Rd", "Colombo 05", null, nineToSeven))
        staff.value = listOf(
            Staff("kasun", "Kasun Perera", "", emptyList(), 4.9, 128, 8, null, emptyList(), nineToSeven),
            Staff("nadeesha", "Nadeesha Fernando", "", emptyList(), 4.8, 64, 5, null, emptyList(), nineToSeven),
        )
        services.value = listOf(
            SalonService("haircut", "Haircut", "", ServiceCategory.HAIRCUT, 30, 1500, listOf("kasun", "nadeesha")),
            SalonService("beard", "Beard Trim", "", ServiceCategory.BEARD, 20, 800, listOf("kasun")),
            SalonService("old", "Old service", "", ServiceCategory.OTHER, 30, 100, active = false),
        )
    }
    private val bookings = FakeBookingRepository()
    private val notifications = FakeNotificationRepository()
    private val availability = ObserveAvailabilityUseCase(salons, bookings, clock)
    private val createBooking = CreateBookingUseCase(salons, bookings, availability, notifications, clock)

    private fun request(
        id: String = "b1",
        staffId: String = "kasun",
        services: List<String> = listOf("haircut", "beard"),
        start: java.time.Instant = at(17, 30),
        type: BookingType = BookingType.APPOINTMENT,
    ) = BookingRequest(id, "aura", staffId, services, start, "kavindu", "Kavindu Perera", type, note = "  Low fade  ")

    private suspend fun expectError(expected: DataError, block: suspend () -> Unit) {
        try {
            block()
            fail("Expected $expected")
        } catch (e: DataException) {
            assertEquals(expected, e.error)
        }
    }

    @Test
    fun `T02 - the Figma booking is created with a price and duration snapshot`() = runTest {
        val booking = createBooking(request())
        assertEquals(at(18, 20), booking.end)                  // 30 + 20 = 50 min
        assertEquals(2300L, booking.totalPrice)                // Rs. 1,500 + Rs. 800
        assertEquals("Haircut & Beard Trim", booking.title)
        assertEquals("Kasun Perera", booking.staffName)
        assertEquals(BookingStatus.BOOKED, booking.status)
        assertEquals("Low fade", booking.note)                 // trimmed
    }

    @Test
    fun `the booked time disappears from availability`() = runTest {
        createBooking(request())
        val starts = availability("aura", "kasun", thursday, durationMin = 30).first()
        assertFalse(at(17, 30) in starts)
        assertFalse(at(18) in starts)
        assertTrue(at(18, 30) in starts)
        // Nadeesha's calendar is untouched.
        assertTrue(at(17, 30) in availability("aura", "nadeesha", thursday, 30).first())
    }

    @Test
    fun `T03 - a slot taken by another phone a moment ago is refused`() = runTest {
        bookings.lockExternally("kasun", at(18), at(18, 30))
        try {
            createBooking(request())
            fail("Expected SlotUnavailableException")
        } catch (e: SlotUnavailableException) {
            assertEquals(SlotProblem.TAKEN, e.problem)
            assertEquals(DataError.SLOT_TAKEN, e.error)
        }
        assertEquals(1, bookings.createCalls)   // the transaction made the final call
        assertTrue(bookings.bookings.value.isEmpty())
    }

    @Test
    fun `T11 - confirming twice with the same id makes only one booking`() = runTest {
        val first = createBooking(request())
        val second = createBooking(request())
        assertEquals(first, second)
        assertEquals(1, bookings.bookings.value.size)
    }

    @Test
    fun `a time during the barber's break is refused with the reason`() = runTest {
        salons.blocks.value = listOf(StaffBlock("lunch", at(13), at(14), "Lunch"))
        try {
            createBooking(request(start = at(13)))
            fail("Expected SlotUnavailableException")
        } catch (e: SlotUnavailableException) {
            assertEquals(SlotProblem.BLOCKED, e.problem)
        }
        assertEquals(0, bookings.createCalls) // nothing was even attempted
    }

    @Test
    fun `a time earlier today is refused`() = runTest {
        try {
            createBooking(request(start = at(9, 30)))
            fail("Expected SlotUnavailableException")
        } catch (e: SlotUnavailableException) {
            assertEquals(SlotProblem.IN_PAST, e.problem)
        }
    }

    @Test
    fun `staff may add a walk-in starting in the current slot`() = runTest {
        val booking = createBooking(request(start = at(10), type = BookingType.WALK_IN, services = listOf("haircut")))
        assertEquals(BookingType.WALK_IN, booking.type)
    }

    @Test
    fun `a service the barber doesn't offer is rejected`() = runTest {
        expectError(DataError.INVALID_REQUEST) { createBooking(request(staffId = "nadeesha")) } // no beard trims
    }

    @Test
    fun `inactive or unknown services are rejected`() = runTest {
        expectError(DataError.INVALID_REQUEST) { createBooking(request(services = listOf("old"))) }
        expectError(DataError.INVALID_REQUEST) { createBooking(request(services = listOf("nope"))) }
        expectError(DataError.INVALID_REQUEST) { createBooking(request(services = emptyList())) }
    }

    @Test
    fun `an unknown barber is NOT_FOUND`() = runTest {
        expectError(DataError.NOT_FOUND) { createBooking(request(staffId = "ghost")) }
    }

    @Test
    fun `cancelling frees the slot for someone else`() = runTest {
        createBooking(request())
        // Same day (less than 24 h ahead), so only the salon may cancel it.
        CancelBookingUseCase(bookings, notifications, OfferFreedSlotUseCase(FakeWaitlistRepository(), clock), clock)("aura", "b1", byStaff = true)
        assertEquals(BookingStatus.CANCELLED, bookings.bookings.value.getValue("b1").status)
        assertTrue(at(17, 30) in availability("aura", "kasun", thursday, 30).first())
        createBooking(request(id = "b2")) // the same time again: now fine
    }

    @Test
    fun `a customer cannot cancel less than 24 h before, the salon can`() = runTest {
        createBooking(request())
        expectError(DataError.TOO_LATE_TO_CANCEL) { CancelBookingUseCase(bookings, notifications, OfferFreedSlotUseCase(FakeWaitlistRepository(), clock), clock)("aura", "b1") }
        assertEquals(BookingStatus.BOOKED, bookings.bookings.value.getValue("b1").status)
    }

    @Test
    fun `a customer cancels a booking two days ahead and gets a feed entry`() = runTest {
        val later = SalonTime.toInstant(thursday.plusDays(2), LocalTime.of(11, 0))
        createBooking(request(start = later))
        CancelBookingUseCase(bookings, notifications, OfferFreedSlotUseCase(FakeWaitlistRepository(), clock), clock)("aura", "b1")
        assertEquals(BookingStatus.CANCELLED, bookings.bookings.value.getValue("b1").status)
        assertEquals(
            listOf("booking_confirmed_b1", "booking_cancelled_b1"),
            notifications.added.filter { it.first == "kavindu" }.map { it.second.id },
        )
    }

    @Test
    fun `booking writes a confirmed entry to the customer's feed, but walk-ins don't`() = runTest {
        createBooking(request())
        createBooking(request(id = "w1", start = at(15), type = BookingType.WALK_IN))
        assertEquals(listOf("booking_confirmed_b1"), notifications.added.map { it.second.id })
    }

    @Test
    fun `a failing feed write does not undo the booking`() = runTest {
        notifications.failNext = true
        val saved = createBooking(request())
        assertEquals("b1", saved.id)
        assertTrue(notifications.added.isEmpty())
    }
}
