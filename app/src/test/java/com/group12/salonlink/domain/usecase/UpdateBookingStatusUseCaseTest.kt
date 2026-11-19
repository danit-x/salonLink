package com.group12.salonlink.domain.usecase

import com.group12.salonlink.core.util.SalonTime
import com.group12.salonlink.domain.model.BookedService
import com.group12.salonlink.domain.model.Booking
import com.group12.salonlink.domain.model.BookingStatus
import com.group12.salonlink.domain.model.BookingType
import com.group12.salonlink.domain.model.DataError
import com.group12.salonlink.domain.model.DataException
import com.group12.salonlink.domain.model.NotificationType
import com.group12.salonlink.feature.staff.schedule.DayStats
import com.group12.salonlink.feature.staff.schedule.staffActions
import com.group12.salonlink.testing.FakeBookingRepository
import com.group12.salonlink.testing.FakeNotificationRepository
import com.group12.salonlink.testing.FakeWaitlistRepository
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/** Staff status updates (FR-06/08, US-07), the buttons they see, and the owner's day numbers. */
class UpdateBookingStatusUseCaseTest {

    private val thursday = LocalDate.of(2026, 12, 3)
    private fun at(h: Int, m: Int = 0) = SalonTime.toInstant(thursday, LocalTime.of(h, m))
    private val clock = Clock.fixed(at(10), ZoneOffset.UTC)
    private val bookings = FakeBookingRepository()
    private val notifications = FakeNotificationRepository()
    private val update = UpdateBookingStatusUseCase(bookings, notifications, CancelBookingUseCase(bookings, notifications, OfferFreedSlotUseCase(FakeWaitlistRepository(), clock), clock), OfferFreedSlotUseCase(FakeWaitlistRepository(), clock))

    private fun booking(id: String, status: BookingStatus = BookingStatus.BOOKED, customer: String? = "kavindu", price: Long = 1500, type: BookingType = BookingType.APPOINTMENT) = Booking(
        id = id, salonId = "aura", customerId = customer, customerName = "Kavindu", staffId = "kasun", staffName = "Kasun",
        services = listOf(BookedService("haircut", "Haircut", 30, price)), start = at(11), end = at(11, 30),
        totalPrice = price, status = status, type = type,
    )

    @Test
    fun `check in, start and complete notify the customer each time`() = runTest {
        bookings.createBooking(booking("b1"))
        update("aura", "b1", BookingStatus.CHECKED_IN)
        update("aura", "b1", BookingStatus.IN_SERVICE)
        update("aura", "b1", BookingStatus.COMPLETED)
        assertEquals(BookingStatus.COMPLETED, bookings.bookings.value.getValue("b1").status)
        assertEquals(
            listOf(BookingStatus.CHECKED_IN, BookingStatus.IN_SERVICE, BookingStatus.COMPLETED),
            notifications.added.map { it.second }.filter { it.type == NotificationType.STATUS_CHANGED }.map { it.status },
        )
    }

    @Test
    fun `a move the status table forbids is refused`() = runTest {
        bookings.createBooking(booking("b1"))
        try {
            update("aura", "b1", BookingStatus.COMPLETED) // BOOKED → COMPLETED skips the chair
            fail("expected NOT_ALLOWED")
        } catch (e: DataException) {
            assertEquals(DataError.NOT_ALLOWED, e.error)
        }
    }

    @Test
    fun `staff may cancel on the day, a walk-in without account gets no notification`() = runTest {
        bookings.createBooking(booking("w1", customer = null, type = BookingType.WALK_IN))
        update("aura", "w1", BookingStatus.CANCELLED)
        assertEquals(BookingStatus.CANCELLED, bookings.bookings.value.getValue("w1").status)
        assertEquals(0, notifications.added.size)
    }

    @Test
    fun `buttons follow the status table, no-show only after the start`() {
        assertEquals(listOf(BookingStatus.CHECKED_IN, BookingStatus.CANCELLED), booking("b").staffActions(at(10)))
        assertEquals(listOf(BookingStatus.CHECKED_IN, BookingStatus.MISSED, BookingStatus.CANCELLED), booking("b").staffActions(at(11, 5)))
        assertEquals(listOf(BookingStatus.COMPLETED), booking("b", BookingStatus.IN_SERVICE).staffActions(at(11, 5)))
        assertEquals(emptyList<BookingStatus>(), booking("b", BookingStatus.COMPLETED).staffActions(at(12)))
    }

    @Test
    fun `day numbers ignore cancelled and missed bookings`() {
        val stats = DayStats.of(
            listOf(
                booking("a", BookingStatus.COMPLETED, price = 1500),
                booking("b", BookingStatus.BOOKED, price = 800),
                booking("c", BookingStatus.CANCELLED, price = 4500),
                booking("d", BookingStatus.MISSED, price = 4000),
                booking("e", BookingStatus.IN_SERVICE, price = 1000, type = BookingType.WALK_IN),
            ),
        )
        assertEquals(DayStats(bookings = 3, completed = 1, walkIns = 1, revenue = 1500, expectedRevenue = 3300), stats)
    }
}
