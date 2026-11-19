// Covers: US-02 (find a barber), FR-02, US-04 (see my next booking) · Figma: "Discovery · SalonLink"
package com.group12.salonlink.feature.customer.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group12.salonlink.core.util.SalonTime
import com.group12.salonlink.domain.model.AuthState
import com.group12.salonlink.domain.model.Booking
import com.group12.salonlink.domain.model.BookingStatus
import com.group12.salonlink.domain.model.BookingType
import com.group12.salonlink.domain.model.DataError
import com.group12.salonlink.domain.model.Salon
import com.group12.salonlink.domain.model.ServiceCategory
import com.group12.salonlink.domain.model.Staff
import com.group12.salonlink.domain.model.UserProfile
import com.group12.salonlink.domain.repository.AuthRepository
import com.group12.salonlink.domain.repository.BookingRepository
import com.group12.salonlink.domain.repository.SalonRepository
import com.group12.salonlink.domain.repository.StyleRepository
import com.group12.salonlink.domain.model.StyleProfile
import com.group12.salonlink.domain.style.BarberMatcher
import com.group12.salonlink.feature.customer.booking.toDataError
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalTime
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * One barber as listed on Discovery.
 *
 * @property fromPrice cheapest service they offer ("From Rs. 1,500"), null if none.
 * @property categories kinds of service they offer, used by the category bubbles.
 * @property serviceNames used by the search box ("beard" finds barbers who do a Beard Trim).
 */
data class BarberCard(
    val salon: Salon,
    val staff: Staff,
    val fromPrice: Long?,
    val categories: Set<ServiceCategory>,
    val serviceNames: List<String>,
    /** 0–100 fit with the customer's style profile (Phase 9); null without style tags. */
    val match: Int? = null,
)

/** How to label the upcoming appointment: "Today", "Tomorrow" or its date. */
enum class UpcomingDay { TODAY, TOMORROW, LATER }

/**
 * @property barbers the barbers matching the search and category, best rated first.
 *   The first one is the big "Your next great cut" card.
 * @property featured the best-rated barber overall, used by the "Book" quick action.
 * @property lastBooking the most recent appointment, repeated by "Book Again".
 */
data class DiscoveryUiState(
    val loading: Boolean = true,
    val firstName: String = "",
    val greeting: SalonTime.Greeting = SalonTime.Greeting.MORNING,
    val area: String? = null,
    val query: String = "",
    val category: ServiceCategory? = null,
    val barbers: List<BarberCard> = emptyList(),
    val featured: BarberCard? = null,
    val upcoming: Booking? = null,
    val upcomingSalon: Salon? = null,
    val upcomingDay: UpcomingDay = UpcomingDay.LATER,
    val lastBooking: Booking? = null,
    val catalogueError: DataError? = null,
    val bookingsError: DataError? = null,
) {
    val isFiltering: Boolean get() = query.isNotBlank() || category != null
}

sealed interface DiscoveryEvent {
    data class QueryChanged(val query: String) : DiscoveryEvent

    /** null = "All styles". Tapping the selected category again also clears it. */
    data class CategorySelected(val category: ServiceCategory?) : DiscoveryEvent
}

/**
 * Customer home. Combines three live sources:
 *  1. the catalogue (every salon's active barbers and services),
 *  2. the signed-in customer's bookings (for "Upcoming appointment" and "Book Again"),
 *  3. what the user typed or tapped (search + category).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val salonRepository: SalonRepository,
    bookingRepository: BookingRepository,
    authRepository: AuthRepository,
    styleRepository: StyleRepository,
    private val clock: Clock,
) : ViewModel() {

    private data class Filters(val query: String = "", val category: ServiceCategory? = null)

    private val filters = MutableStateFlow(Filters())

    private val user: Flow<UserProfile> =
        authRepository.authState.filterIsInstance<AuthState.SignedIn>().map { it.user }

    /** Result of loading the catalogue: the cards, or why it failed. */
    private data class CatalogueResult(val cards: List<BarberCard>?, val error: DataError?)

    private val catalogue: Flow<CatalogueResult> = salonRepository.observeSalons()
        .flatMapLatest { salons ->
            if (salons.isEmpty()) flowOf(emptyList())
            else combine(salons.map(::cardsFor)) { perSalon -> perSalon.flatMap { it } }
        }
        .map { CatalogueResult(it, null) }
        .catch { emit(CatalogueResult(null, it.toDataError())) }

    private data class BookingsResult(val bookings: List<Booking>, val error: DataError?)

    private val bookings: Flow<BookingsResult> = user
        .flatMapLatest { bookingRepository.observeCustomerBookings(it.uid) }
        .map { BookingsResult(it, null) }
        // WHY a separate error: if only the bookings fail, the barbers should still show.
        .catch { emit(BookingsResult(emptyList(), it.toDataError())) }
        .onStart { emit(BookingsResult(emptyList(), null)) }

    /** The style profile ranks barbers by match. Without one (or if it fails) the order is by rating. */
    private val style: Flow<StyleProfile> = user
        .flatMapLatest { styleRepository.observeProfile(it.uid) }
        .catch { emit(StyleProfile()) }
        .onStart { emit(StyleProfile()) }

    val uiState: StateFlow<DiscoveryUiState> =
        combine(user, catalogue, bookings, filters, style) { user, catalogue, bookings, filters, style ->
            buildState(user, catalogue, bookings, filters, style)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiscoveryUiState())

    fun onEvent(event: DiscoveryEvent) {
        when (event) {
            is DiscoveryEvent.QueryChanged -> filters.update { it.copy(query = event.query.take(MAX_QUERY)) }
            is DiscoveryEvent.CategorySelected -> filters.update {
                it.copy(category = if (event.category == it.category) null else event.category)
            }
        }
    }

    /** Barbers of one salon with what they offer. */
    private fun cardsFor(salon: Salon): Flow<List<BarberCard>> =
        combine(salonRepository.observeStaff(salon.id), salonRepository.observeServices(salon.id)) { staff, services ->
            staff.filter { it.active }.map { member ->
                val offered = services.filter { it.isOfferedBy(member.id) }
                BarberCard(
                    salon = salon,
                    staff = member,
                    fromPrice = offered.minOfOrNull { it.price },
                    categories = offered.map { it.category }.toSet(),
                    serviceNames = offered.map { it.name },
                )
            }
        }

    private fun buildState(
        user: UserProfile,
        catalogue: CatalogueResult,
        bookings: BookingsResult,
        filters: Filters,
        style: StyleProfile,
    ): DiscoveryUiState {
        val now = clock.instant()
        val today = SalonTime.localDateOf(now)
        // WHY this order: the customer's favourite first, then best match for their styles,
        // then best rated. Without a profile this is simply "best rated first" (Phase 4).
        val all = catalogue.cards.orEmpty()
            .map { it.copy(match = BarberMatcher.score(style, it.staff)) }
            .sortedWith(
                compareByDescending<BarberCard> { it.staff.id == style.preferredStaffId }
                    .thenByDescending { it.match ?: -1 }
                    .then(BEST_RATED_FIRST),
            )
        val upcoming = bookings.bookings.filter { it.isUpcoming(now) }.minByOrNull { it.start }
        val upcomingDate = upcoming?.let { SalonTime.localDateOf(it.start) }
        return DiscoveryUiState(
            loading = catalogue.cards == null && catalogue.error == null,
            firstName = user.firstName,
            greeting = SalonTime.greetingFor(LocalTime.ofInstant(now, SalonTime.SALON_ZONE)),
            area = all.firstOrNull()?.salon?.area,
            query = filters.query,
            category = filters.category,
            barbers = all.filter { it.matches(filters) },
            featured = all.firstOrNull(),
            upcoming = upcoming,
            upcomingSalon = upcoming?.let { b -> all.firstOrNull { it.salon.id == b.salonId }?.salon },
            upcomingDay = when (upcomingDate) {
                today -> UpcomingDay.TODAY
                today.plusDays(1) -> UpcomingDay.TOMORROW
                else -> UpcomingDay.LATER
            },
            // Bookings arrive newest first; repeat the latest real appointment.
            lastBooking = bookings.bookings.firstOrNull {
                it.type == BookingType.APPOINTMENT && it.status != BookingStatus.CANCELLED
            },
            catalogueError = catalogue.error,
            bookingsError = bookings.error,
        )
    }

    private fun BarberCard.matches(filters: Filters): Boolean {
        if (filters.category != null && filters.category !in categories) return false
        val q = filters.query.trim().lowercase(Locale.ENGLISH)
        if (q.isEmpty()) return true
        val haystack = buildList {
            add(staff.name); add(salon.name); add(salon.area)
            addAll(staff.specialties); addAll(serviceNames)
        }
        return haystack.any { it.lowercase(Locale.ENGLISH).contains(q) }
    }

    private companion object {
        const val MAX_QUERY = 60
        val BEST_RATED_FIRST = compareByDescending<BarberCard> { it.staff.rating }
            .thenByDescending { it.staff.reviewCount }
            .thenBy { it.staff.name }
    }
}
