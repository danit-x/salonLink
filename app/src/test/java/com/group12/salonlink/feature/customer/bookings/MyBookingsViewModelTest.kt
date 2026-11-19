package com.group12.salonlink.feature.customer.bookings

import com.group12.salonlink.core.util.SalonTime
import com.group12.salonlink.domain.model.BookedService
import com.group12.salonlink.domain.model.Booking
import com.group12.salonlink.domain.model.BookingStatus
import com.group12.salonlink.domain.model.BookingType
import com.group12.salonlink.domain.model.DataError
import com.group12.salonlink.domain.model.Role
import com.group12.salonlink.domain.model.UserProfile
import com.group12.salonlink.domain.usecase.AcceptWaitlistOfferUseCase
import com.group12.salonlink.domain.usecase.CancelBookingUseCase
import com.group12.salonlink.domain.usecase.CreateBookingUseCase
import com.group12.salonlink.domain.usecase.ObserveAvailabilityUseCase
import com.group12.salonlink.domain.usecase.OfferFreedSlotUseCase
import com.group12.salonlink.testing.FakeAuthRepository
import com.group12.salonlink.testing.FakeBookingRepository
import com.group12.salonlink.testing.FakeNotificationRepository
import com.group12.salonlink.testing.FakeWaitlistRepository
import com.group12.salonlink.testing.FakeSalonRepository
import com.group12.salonlink.testing.MainDispatcherRule
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** "Your bookings": tabs, the 24 h cancel rule and the cancel flow (US-05). */
@OptIn(ExperimentalCoroutinesApi::class)
class MyBookingsViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val thursday = LocalDate.of(2026, 12, 3)
    private fun on(date: LocalDate, h: Int) = SalonTime.toInstant(date, LocalTime.of(h, 0))
    private val clock = Clock.fixed(on(thursday, 10), ZoneOffset.UTC)

    private val bookings = FakeBookingRepository()
    private val notifications = FakeNotificationRepository()
    private val waitlist = FakeWaitlistRepository()
    private val auth = FakeAuthRepository().apply {
        signInAs(UserProfile("kavindu", "Kavindu Perera", "k@example.com", null, Role.CUSTOMER))
    }

    private fun booking(id: String, start: Instant, status: BookingStatus = BookingStatus.BOOKED) = Booking(
        id = id, salonId = "aura", customerId = "kavindu", customerName = "Kavindu Perera",
        staffId = "kasun", staffName = "Kasun Perera", services = listOf(BookedService("haircut", "Haircut", 30, 1500)),
        start = start, end = start.plusSeconds(1800), totalPrice = 1500, status = status, type = BookingType.APPOINTMENT,
    )

    private fun TestScope.viewModel() = MyBookingsViewModel(
        auth, bookings, FakeSalonRepository(), waitlist,
        CancelBookingUseCase(bookings, notifications, OfferFreedSlotUseCase(waitlist, clock), clock),
        AcceptWaitlistOfferUseCase(CreateBookingUseCase(FakeSalonRepository(), bookings, ObserveAvailabilityUseCase(FakeSalonRepository(), bookings, clock), notifications, clock), waitlist, clock),
        clock,
    ).also { vm -> backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} } }

    @Test
    fun `upcoming soonest first, history newest first`() = runTest {
        bookings.bookings.value = listOf(
            booking("later", on(thursday.plusDays(5), 11)),
            booking("soon", on(thursday.plusDays(2), 11)),
            booking("old", on(thursday.minusDays(10), 11), BookingStatus.COMPLETED),
            booking("cancelled", on(thursday.plusDays(3), 11), BookingStatus.CANCELLED),
        ).associateBy { it.id }
        val state = viewModel().uiState.value
        assertEquals(listOf("soon", "later"), state.upcoming.map { it.booking.id })
        assertEquals(listOf("cancelled", "old"), state.history.map { it.booking.id })
    }

    @Test
    fun `today's booking can't be cancelled in the app, next week's can`() = runTest {
        bookings.bookings.value = listOf(
            booking("today", on(thursday, 17)),
            booking("next-week", on(thursday.plusDays(7), 11)),
        ).associateBy { it.id }
        val rows = viewModel().uiState.value.upcoming.associateBy { it.booking.id }
        assertFalse(rows.getValue("today").canCancel)
        assertTrue(rows.getValue("today").lateToCancel)
        assertTrue(rows.getValue("next-week").canCancel)
    }

    @Test
    fun `confirming the dialog cancels and moves the booking to history`() = runTest {
        bookings.createBooking(booking("b1", on(thursday.plusDays(7), 11)))
        val vm = viewModel()
        vm.onEvent(MyBookingsEvent.RequestCancel("b1"))
        assertEquals("b1", vm.uiState.value.cancelTarget?.booking?.id)

        vm.onEvent(MyBookingsEvent.ConfirmCancel)

        val state = vm.uiState.value
        assertNull(state.cancelTarget)
        assertEquals(BookingsMessage.Cancelled, state.message)
        assertTrue(state.upcoming.isEmpty())
        assertEquals(BookingStatus.CANCELLED, state.history.single().booking.status)
    }

    @Test
    fun `a failed cancel tells the user why`() = runTest {
        bookings.bookings.value = mapOf("today" to booking("today", on(thursday, 17)))
        val vm = viewModel()
        vm.onEvent(MyBookingsEvent.RequestCancel("today"))
        vm.onEvent(MyBookingsEvent.ConfirmCancel)
        assertEquals(BookingsMessage.Failed(DataError.TOO_LATE_TO_CANCEL), vm.uiState.value.message)
    }
}
