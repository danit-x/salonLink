package com.group12.salonlink.feature.customer.booking

import com.group12.salonlink.core.navigation.BookingFlowRoute
import com.group12.salonlink.core.util.SalonTime
import com.group12.salonlink.domain.booking.SlotProblem
import com.group12.salonlink.domain.model.DataError
import com.group12.salonlink.domain.model.DataException
import com.group12.salonlink.domain.model.Role
import com.group12.salonlink.domain.model.Salon
import com.group12.salonlink.domain.model.SalonService
import com.group12.salonlink.domain.model.ServiceCategory
import com.group12.salonlink.domain.model.Staff
import com.group12.salonlink.domain.model.TimeRange
import com.group12.salonlink.domain.model.UserProfile
import com.group12.salonlink.domain.model.WeeklyHours
import com.group12.salonlink.domain.usecase.CreateBookingUseCase
import com.group12.salonlink.domain.usecase.ObserveAvailabilityUseCase
import com.group12.salonlink.testing.FakeAuthRepository
import com.group12.salonlink.testing.FakeBookingRepository
import com.group12.salonlink.testing.FakeNotificationRepository
import com.group12.salonlink.testing.FakeWaitlistRepository
import com.group12.salonlink.testing.FakeSalonRepository
import com.group12.salonlink.testing.MainDispatcherRule
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The booking flow screens' logic (Figma "Service selection" + "Date & time") with in-memory
 * fakes: Kavindu books Haircut + Beard Trim with Kasun on Thursday 3 Dec 2026 (T02, T03, T11).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookingFlowViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val thursday = LocalDate.of(2026, 12, 3)
    private fun at(h: Int, m: Int = 0) = SalonTime.toInstant(thursday, LocalTime.of(h, m))

    // "Now" is Thursday 10:00 in Colombo.
    private val clock = Clock.fixed(at(10), ZoneOffset.UTC)
    private val nineToSeven = TimeRange(LocalTime.of(9, 0), LocalTime.of(19, 0))
    private val everyDay = WeeklyHours(DayOfWeek.entries.associateWith { nineToSeven })
    private val notSunday = WeeklyHours(DayOfWeek.entries.filter { it != DayOfWeek.SUNDAY }.associateWith { nineToSeven })

    private val salons = FakeSalonRepository().apply {
        salons.value = mapOf("aura" to Salon("aura", "Aura Salon", "24 Havelock Road", "Colombo 05", null, everyDay))
        staff.value = listOf(Staff("kasun", "Kasun Perera", "", emptyList(), 4.9, 128, 8, null, emptyList(), notSunday))
        services.value = listOf(
            SalonService("haircut", "Haircut", "", ServiceCategory.HAIRCUT, 30, 1500, listOf("kasun", "nadeesha")),
            SalonService("beard", "Beard Trim", "", ServiceCategory.BEARD, 20, 800, listOf("kasun")),
            SalonService("treatment", "Hair Treatment", "", ServiceCategory.TREATMENT, 60, 4000, listOf("nadeesha")),
        )
    }
    private val bookings = FakeBookingRepository()
    private val notifications = FakeNotificationRepository()
    private val waitlist = FakeWaitlistRepository()
    private val auth = FakeAuthRepository().apply {
        signInAs(UserProfile("kavindu", "Kavindu Perera", "kavindu@example.com", null, Role.CUSTOMER))
    }
    private val availability = ObserveAvailabilityUseCase(salons, bookings, clock)

    private fun viewModel(preselected: List<String> = listOf("haircut", "beard")) = BookingFlowViewModel(
        args = BookingFlowRoute("aura", "kasun", preselected),
        salonRepository = salons,
        authRepository = auth,
        observeAvailability = availability,
        createBooking = CreateBookingUseCase(salons, bookings, availability, notifications, clock),
        waitlistRepository = waitlist,
        clock = clock,
    )

    /** uiState only runs while someone collects it (like a screen would). */
    private fun TestScope.collect(vm: BookingFlowViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
    }

    @Test
    fun `lists only Kasun's services and ticks the preselected ones`() = runTest {
        val vm = viewModel().also { collect(it) }
        val state = vm.uiState.value
        assertFalse(state.loading)
        assertEquals(listOf("haircut", "beard"), state.services.map { it.id })
        assertEquals(2300L, state.totalPrice)   // Figma: Rs. 2,300
        assertEquals(50, state.durationMin)     // "50 minutes of you time"
        assertTrue(state.canChooseTime)
    }

    @Test
    fun `untick everything and the next step is disabled`() = runTest {
        val vm = viewModel().also { collect(it) }
        vm.onEvent(BookingFlowEvent.ToggleService("haircut"))
        vm.onEvent(BookingFlowEvent.ToggleService("beard"))
        assertFalse(vm.uiState.value.canChooseTime)
        assertEquals(0L, vm.uiState.value.totalPrice)
    }

    @Test
    fun `opens on today and greys out days Kasun can't take bookings`() = runTest {
        val state = viewModel().also { collect(it) }.uiState.value
        assertEquals(thursday, state.selectedDate)
        assertTrue(state.isBookableDay(thursday))
        assertFalse(state.isBookableDay(thursday.minusDays(1)))     // past
        assertFalse(state.isBookableDay(LocalDate.of(2026, 12, 6))) // Sunday: Kasun's day off
        assertFalse(state.isBookableDay(thursday.plusDays(61)))     // beyond the 60-day window
    }

    @Test
    fun `month arrows stay inside the booking window`() = runTest {
        val vm = viewModel().also { collect(it) }
        vm.onEvent(BookingFlowEvent.PreviousMonth)
        assertEquals(YearMonth.of(2026, 12), vm.uiState.value.visibleMonth) // can't go before today
        vm.onEvent(BookingFlowEvent.NextMonth)
        vm.onEvent(BookingFlowEvent.NextMonth)
        vm.onEvent(BookingFlowEvent.NextMonth)
        vm.onEvent(BookingFlowEvent.NextMonth)
        // 3 Dec + 60 days = 1 Feb 2027, so February is the last month you can open.
        assertEquals(YearMonth.of(2027, 2), vm.uiState.value.visibleMonth)
        assertFalse(vm.uiState.value.canGoToNextMonth)
    }

    @Test
    fun `picking a time shows when the appointment ends`() = runTest {
        val vm = viewModel().also { collect(it) }
        vm.onEvent(BookingFlowEvent.SelectTime(at(17, 30)))
        assertEquals(at(18, 20), vm.uiState.value.selectedEnd) // "ends at 6:20 pm"
        assertTrue(vm.uiState.value.canConfirm)
    }

    @Test
    fun `a time booked on another phone is struck through and un-selected live`() = runTest {
        val vm = viewModel().also { collect(it) }
        vm.onEvent(BookingFlowEvent.SelectTime(at(17, 30)))

        bookings.lockExternally("kasun", at(17, 30), at(18))

        val state = vm.uiState.value
        assertNull(state.selectedStart)
        val options = (state.slots as SlotsState.Ready).options
        assertEquals(SlotProblem.TAKEN, options.first { it.start == at(17, 30) }.problem)
    }

    @Test
    fun `T02 - confirm saves the booking for the signed-in customer`() = runTest {
        val vm = viewModel().also { collect(it) }
        vm.onEvent(BookingFlowEvent.SelectTime(at(17, 30)))
        vm.onEvent(BookingFlowEvent.Confirm)

        val id = vm.uiState.value.confirmedBookingId
        assertNotNull(id)
        val saved = bookings.bookings.value.getValue(id!!)
        assertEquals("Kavindu Perera", saved.customerName)
        assertEquals("kavindu", saved.customerId)
        assertEquals(2300L, saved.totalPrice)
        assertEquals(at(17, 30), saved.start)

        vm.onEvent(BookingFlowEvent.ConfirmationShown)
        assertNull(vm.uiState.value.confirmedBookingId)
    }

    @Test
    fun `T03 - losing the race explains why and clears the time`() = runTest {
        val vm = viewModel().also { collect(it) }
        vm.onEvent(BookingFlowEvent.SelectTime(at(17, 30)))
        bookings.nextCreateError = DataException(DataError.SLOT_TAKEN)

        vm.onEvent(BookingFlowEvent.Confirm)

        val state = vm.uiState.value
        assertEquals(SubmitError.Unavailable(SlotProblem.TAKEN), state.submitError)
        assertNull(state.selectedStart)
        assertFalse(state.submitting)
        assertNull(state.confirmedBookingId)
    }

    @Test
    fun `T11 - after a network error, retrying makes exactly one booking`() = runTest {
        val vm = viewModel().also { collect(it) }
        vm.onEvent(BookingFlowEvent.SelectTime(at(17, 30)))
        bookings.nextCreateError = DataException(DataError.NETWORK)

        vm.onEvent(BookingFlowEvent.Confirm)
        assertEquals(SubmitError.Failed(DataError.NETWORK), vm.uiState.value.submitError)
        assertEquals(at(17, 30), vm.uiState.value.selectedStart) // kept, so retry is one tap

        vm.onEvent(BookingFlowEvent.Confirm)
        assertNotNull(vm.uiState.value.confirmedBookingId)
        assertEquals(2, bookings.createCalls)
        assertEquals(1, bookings.bookings.value.size)
    }

    @Test
    fun `an unknown barber shows not found`() = runTest {
        val vm = BookingFlowViewModel(
            args = BookingFlowRoute("aura", "nobody"),
            salonRepository = salons,
            authRepository = auth,
            observeAvailability = availability,
            createBooking = CreateBookingUseCase(salons, bookings, availability, notifications, clock),
            waitlistRepository = waitlist,
            clock = clock,
        ).also { collect(it) }
        assertEquals(DataError.NOT_FOUND, vm.uiState.value.loadError)
    }

    @Test
    fun `no time suits - join the waitlist for the day within working hours`() = runTest {
        val vm = viewModel().also { collect(it) }
        vm.onEvent(BookingFlowEvent.JoinWaitlist)
        assertEquals(thursday, vm.uiState.value.waitlistJoinedDate)
        val entry = waitlist.entries.value.values.single()
        assertEquals("kavindu", entry.customerId)
        assertEquals(50, entry.durationMin)
        assertEquals(at(9), entry.windowStart)
        assertEquals(at(19), entry.windowEnd)
    }
}
