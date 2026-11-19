package com.group12.salonlink.feature.customer.discovery

import com.group12.salonlink.core.util.SalonTime
import com.group12.salonlink.domain.model.BookedService
import com.group12.salonlink.domain.model.Booking
import com.group12.salonlink.domain.model.BookingStatus
import com.group12.salonlink.domain.model.BookingType
import com.group12.salonlink.domain.model.Role
import com.group12.salonlink.domain.model.Salon
import com.group12.salonlink.domain.model.SalonService
import com.group12.salonlink.domain.model.ServiceCategory
import com.group12.salonlink.domain.model.Staff
import com.group12.salonlink.domain.model.TimeRange
import com.group12.salonlink.domain.model.UserProfile
import com.group12.salonlink.domain.model.WeeklyHours
import com.group12.salonlink.testing.FakeAuthRepository
import com.group12.salonlink.testing.FakeBookingRepository
import com.group12.salonlink.testing.FakeSalonRepository
import com.group12.salonlink.testing.MainDispatcherRule
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import com.group12.salonlink.domain.model.StyleProfile
import com.group12.salonlink.domain.model.StyleTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Discovery (customer home): search, categories, featured barber and the next appointment. */
@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveryViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val thursday = LocalDate.of(2026, 12, 3)
    private fun on(date: LocalDate, h: Int, m: Int = 0) = SalonTime.toInstant(date, LocalTime.of(h, m))
    private val clock = Clock.fixed(on(thursday, 10), ZoneOffset.UTC) // Thursday 10:00 Colombo
    private val hours = WeeklyHours(DayOfWeek.entries.associateWith { TimeRange(LocalTime.of(9, 0), LocalTime.of(19, 0)) })

    private val salons = FakeSalonRepository().apply {
        salons.value = mapOf("aura" to Salon("aura", "Aura Salon", "24 Havelock Road", "Colombo 05", null, hours))
        staff.value = listOf(
            Staff("nadeesha", "Nadeesha Fernando", "", listOf("Long Hair"), 4.8, 86, 6, null, emptyList(), hours),
            Staff("kasun", "Kasun Perera", "", listOf("Fade"), 4.9, 128, 8, null, emptyList(), hours),
        )
        services.value = listOf(
            SalonService("haircut", "Haircut", "", ServiceCategory.HAIRCUT, 30, 1500, listOf("kasun", "nadeesha")),
            SalonService("beard", "Beard Trim", "", ServiceCategory.BEARD, 20, 800, listOf("kasun")),
            SalonService("colour", "Hair Colour", "", ServiceCategory.COLOUR, 90, 4500, listOf("nadeesha")),
        )
    }
    private val bookings = FakeBookingRepository()
    private val auth = FakeAuthRepository().apply {
        signInAs(UserProfile("kavindu", "Kavindu Perera", "kavindu@example.com", null, Role.CUSTOMER))
    }

    private val style = com.group12.salonlink.testing.FakeStyleRepository()

    private fun TestScope.viewModel() = DiscoveryViewModel(salons, bookings, auth, style, clock).also { vm ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
    }

    private fun booking(id: String, start: java.time.Instant, status: BookingStatus = BookingStatus.BOOKED) = Booking(
        id = id, salonId = "aura", customerId = "kavindu", customerName = "Kavindu Perera",
        staffId = "kasun", staffName = "Kasun Perera",
        services = listOf(BookedService("haircut", "Haircut", 30, 1500), BookedService("beard", "Beard Trim", 20, 800)),
        start = start, end = start.plusSeconds(50 * 60), totalPrice = 2300,
        status = status, type = BookingType.APPOINTMENT,
    )

    @Test
    fun `greets the customer and features the best-rated barber`() = runTest {
        val state = viewModel().uiState.value
        assertEquals("Kavindu", state.firstName)
        assertEquals(SalonTime.Greeting.MORNING, state.greeting)
        assertEquals("Colombo 05", state.area)
        assertEquals(listOf("kasun", "nadeesha"), state.barbers.map { it.staff.id })
        assertEquals("kasun", state.featured?.staff?.id)
        assertEquals(800L, state.barbers.first().fromPrice) // cheapest of Kasun's services: Beard Trim
    }

    @Test
    fun `with style tags the best match is featured, with a favourite that one comes first`() = runTest {
        style.profiles.value = mapOf("kavindu" to StyleProfile(tags = setOf(StyleTag.LONG_HAIR)))
        val vm = viewModel()
        // Nadeesha does long hair: 80 + 19 = 99; Kasun (4.9, fade only): 0 + 20 = 20.
        assertEquals(listOf("nadeesha", "kasun"), vm.uiState.value.barbers.map { it.staff.id })
        assertEquals(99, vm.uiState.value.featured?.match)

        style.profiles.value = mapOf("kavindu" to StyleProfile(tags = setOf(StyleTag.LONG_HAIR), preferredStaffId = "kasun"))
        assertEquals("kasun", vm.uiState.value.featured?.staff?.id)
    }

    @Test
    fun `search matches service names, not just barber names`() = runTest {
        val vm = viewModel()
        vm.onEvent(DiscoveryEvent.QueryChanged("beard"))
        assertEquals(listOf("kasun"), vm.uiState.value.barbers.map { it.staff.id })

        vm.onEvent(DiscoveryEvent.QueryChanged("nobody here"))
        assertTrue(vm.uiState.value.barbers.isEmpty())
        assertTrue(vm.uiState.value.isFiltering)
    }

    @Test
    fun `a category shows only barbers who offer it, tapping it again clears it`() = runTest {
        val vm = viewModel()
        vm.onEvent(DiscoveryEvent.CategorySelected(ServiceCategory.COLOUR))
        assertEquals(listOf("nadeesha"), vm.uiState.value.barbers.map { it.staff.id })

        vm.onEvent(DiscoveryEvent.CategorySelected(ServiceCategory.COLOUR))
        assertNull(vm.uiState.value.category)
        assertEquals(2, vm.uiState.value.barbers.size)
    }

    @Test
    fun `upcoming is the next booking still to come, book again repeats the latest`() = runTest {
        bookings.bookings.value = listOf(
            booking("past", on(thursday.minusDays(7), 17)),
            booking("cancelled", on(thursday.plusDays(2), 9), BookingStatus.CANCELLED),
            booking("later", on(thursday.plusDays(1), 11)),
            booking("today", on(thursday, 17, 30)),
        ).associateBy { it.id }

        val state = viewModel().uiState.value
        assertEquals("today", state.upcoming?.id)
        assertEquals(UpcomingDay.TODAY, state.upcomingDay)
        assertEquals("Aura Salon", state.upcomingSalon?.name)
        // Newest first, cancelled skipped: the booking tomorrow is the latest real appointment.
        assertEquals("later", state.lastBooking?.id)
    }

    @Test
    fun `no bookings means no upcoming card and no book again`() = runTest {
        val state = viewModel().uiState.value
        assertNull(state.upcoming)
        assertNull(state.lastBooking)
    }
}
