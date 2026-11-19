// Covers: US-03, US-04, FR-03, FR-04, T02, T03, T11 · Figma: "Service selection" + "Date & time"
package com.group12.salonlink.feature.customer.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group12.salonlink.core.navigation.BookingFlowRoute
import com.group12.salonlink.core.util.SalonTime
import com.group12.salonlink.domain.booking.AvailabilityCalculator
import com.group12.salonlink.domain.booking.BookingPolicy
import com.group12.salonlink.domain.booking.DayAvailabilityInput
import com.group12.salonlink.domain.booking.SlotOption
import com.group12.salonlink.domain.booking.SlotProblem
import com.group12.salonlink.domain.model.AuthState
import com.group12.salonlink.domain.model.DataError
import com.group12.salonlink.domain.model.DataException
import com.group12.salonlink.domain.model.Salon
import com.group12.salonlink.domain.model.SalonService
import com.group12.salonlink.domain.model.SlotUnavailableException
import com.group12.salonlink.domain.model.Staff
import com.group12.salonlink.domain.repository.AuthRepository
import com.group12.salonlink.domain.repository.SalonRepository
import com.group12.salonlink.domain.repository.WaitlistRepository
import com.group12.salonlink.domain.model.WaitlistEntry
import com.group12.salonlink.domain.model.WaitlistStatus
import com.group12.salonlink.domain.usecase.BookingRequest
import com.group12.salonlink.domain.usecase.CreateBookingUseCase
import com.group12.salonlink.domain.usecase.ObserveAvailabilityUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why "Confirm appointment" didn't work. */
sealed interface SubmitError {
    /** The time can't be booked (taken a moment ago, break, past…). The user picks another time. */
    data class Unavailable(val problem: SlotProblem) : SubmitError

    /** Network, permissions… The user can simply tap Confirm again (safe, see [BookingFlowViewModel]). */
    data class Failed(val error: DataError) : SubmitError
}

/** The times shown for the selected day. */
sealed interface SlotsState {
    data object Loading : SlotsState
    data class Ready(val options: List<SlotOption>) : SlotsState
    data class Failed(val error: DataError) : SlotsState
}

/**
 * Everything the two booking-flow screens draw. Derived values (total price, end time…) are
 * computed here, not in the UI, so they are unit-tested and both screens agree.
 *
 * @property selectedDate the day shown under the calendar (defaults to the first working day).
 * @property selectedStart the chosen time, or null. It is cleared automatically when the time
 *   stops being free (e.g. someone else books it while the screen is open).
 * @property confirmedBookingId set once the booking is saved: the screen then opens the
 *   confirmation and reports back with [BookingFlowEvent.ConfirmationShown].
 */
data class BookingFlowUiState(
    val loading: Boolean = true,
    val loadError: DataError? = null,
    val salon: Salon? = null,
    val staff: Staff? = null,
    val services: List<SalonService> = emptyList(),
    val selectedServiceIds: Set<String> = emptySet(),
    val today: LocalDate = LocalDate.MIN,
    val lastBookableDate: LocalDate = LocalDate.MIN,
    val visibleMonth: YearMonth = YearMonth.of(2000, 1),
    val selectedDate: LocalDate? = null,
    val slots: SlotsState = SlotsState.Loading,
    val selectedStart: Instant? = null,
    val submitting: Boolean = false,
    val submitError: SubmitError? = null,
    val confirmedBookingId: String? = null,
    /** The day the customer joined the waitlist for (shown as a confirmation). */
    val waitlistJoinedDate: LocalDate? = null,
    val joiningWaitlist: Boolean = false,
) {
    /** Chosen services, in the salon's catalogue order. */
    val selectedServices: List<SalonService> get() = services.filter { it.id in selectedServiceIds }
    val totalPrice: Long get() = selectedServices.sumOf { it.price }
    val durationMin: Int get() = selectedServices.sumOf { it.durationMin }
    val selectedEnd: Instant? get() = selectedStart?.plusSeconds(durationMin * 60L)

    /** Step 1 → 2 needs at least one service. */
    val canChooseTime: Boolean get() = selectedServices.isNotEmpty()
    val canConfirm: Boolean get() = canChooseTime && selectedStart != null && !submitting

    val canGoToPreviousMonth: Boolean get() = visibleMonth > YearMonth.from(today)
    val canGoToNextMonth: Boolean get() = visibleMonth < YearMonth.from(lastBookableDate)

    /** True if [date] can be picked: inside the booking window and both salon and barber work. */
    fun isBookableDay(date: LocalDate): Boolean {
        if (date < today || date > lastBookableDate) return false
        return workingDay(salon, staff, date)
    }
}

/** What the user did on the Services or Date & time screen. */
sealed interface BookingFlowEvent {
    data class ToggleService(val serviceId: String) : BookingFlowEvent
    data object PreviousMonth : BookingFlowEvent
    data object NextMonth : BookingFlowEvent
    data class SelectDate(val date: LocalDate) : BookingFlowEvent
    data class SelectTime(val start: Instant) : BookingFlowEvent
    data object Confirm : BookingFlowEvent
    data object DismissError : BookingFlowEvent
    data object ConfirmationShown : BookingFlowEvent
    /** No time suits: be offered one if somebody cancels on the selected day (Phase 7). */
    data object JoinWaitlist : BookingFlowEvent
}

/**
 * Shared ViewModel of the 3-step booking flow. It lives in the nested navigation graph, so
 * Services (step 1) and Date & time (step 2) read and change the same state.
 *
 * Data flow:
 * ```
 *  Firestore ─(salon, barber, services)──┐
 *  user taps ─onEvent──▶ form ───────────┼─▶ uiState ─▶ screens
 *  form.date + duration ─▶ slot options ─┘   (live: a time booked by someone else disappears)
 * ```
 *
 * WHY one [bookingId] for the whole flow: it is created once, here. If "Confirm" is tapped
 * again after a network error, the same id goes to the engine, which returns the booking it
 * already saved instead of making a duplicate (T11). A refused time writes nothing, so trying
 * another time with the same id is also safe.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = BookingFlowViewModel.Factory::class)
class BookingFlowViewModel @AssistedInject constructor(
    @Assisted private val args: BookingFlowRoute,
    salonRepository: SalonRepository,
    private val authRepository: AuthRepository,
    private val observeAvailability: ObserveAvailabilityUseCase,
    private val createBooking: CreateBookingUseCase,
    private val waitlistRepository: WaitlistRepository,
    private val clock: Clock,
) : ViewModel() {

    /** Hilt creates this ViewModel with the navigation arguments of the flow. */
    @AssistedFactory
    interface Factory {
        fun create(args: BookingFlowRoute): BookingFlowViewModel
    }

    private val bookingId = UUID.randomUUID().toString()
    private val today = LocalDate.now(clock.withZone(SalonTime.SALON_ZONE))
    private val lastBookableDate = today.plusDays(BookingPolicy.BOOKING_WINDOW_DAYS)

    /** Only what the user chose. Everything else is derived from Firestore data. */
    private data class Form(
        val selectedServiceIds: Set<String>,
        val visibleMonth: YearMonth? = null,
        val selectedDate: LocalDate? = null,
        val selectedStart: Instant? = null,
        val submitting: Boolean = false,
        val submitError: SubmitError? = null,
        val confirmedBookingId: String? = null,
        val waitlistJoinedDate: LocalDate? = null,
        val joiningWaitlist: Boolean = false,
    )

    private sealed interface Catalogue {
        data object Loading : Catalogue
        data class Ready(val salon: Salon, val staff: Staff, val services: List<SalonService>) : Catalogue
        data class Failed(val error: DataError) : Catalogue
    }

    private val form = MutableStateFlow(Form(selectedServiceIds = args.preselectedServiceIds.toSet()))

    private val catalogue: Flow<Catalogue> = combine(
        salonRepository.observeSalon(args.salonId),
        salonRepository.observeStaffMember(args.salonId, args.staffId),
        salonRepository.observeServices(args.salonId),
    ) { salon, staff, services ->
        if (salon == null || staff == null || !staff.active) {
            Catalogue.Failed(DataError.NOT_FOUND)
        } else {
            // Only what THIS barber offers, cuts first then colour and treatments (Figma order).
            // sortedBy is stable, so services in the same category keep their A–Z order.
            Catalogue.Ready(salon, staff, services.filter { it.isOfferedBy(staff.id) }.sortedBy { it.category.ordinal })
        }
    }
        .onStart<Catalogue> { emit(Catalogue.Loading) }
        .catch { emit(Catalogue.Failed(it.toDataError())) }

    /**
     * The day whose times are shown: the user's pick, or else the first day the barber works
     * that is not already over (late in the evening that is tomorrow, not an empty today).
     */
    private fun effectiveDate(catalogue: Catalogue, form: Form): LocalDate? {
        if (catalogue !is Catalogue.Ready) return null
        return form.selectedDate ?: generateSequence(today) { it.plusDays(1) }
            .takeWhile { it <= lastBookableDate }
            .firstOrNull { workingDay(catalogue.salon, catalogue.staff, it) && !dayIsOver(catalogue, it) }
    }

    private fun dayIsOver(catalogue: Catalogue.Ready, date: LocalDate): Boolean {
        if (date != today) return false
        val window = AvailabilityCalculator.workingWindow(
            DayAvailabilityInput(date, catalogue.salon.openingHours, catalogue.staff.workingHours, emptyList(), emptySet(), Instant.EPOCH),
        ) ?: return true
        return SalonTime.toInstant(date, window.end) <= clock.instant()
    }

    /** Re-subscribes only when the day or the total duration changes, not on every tap. */
    private val slots: Flow<SlotsState> = combine(catalogue, form) { catalogue, form ->
        val date = effectiveDate(catalogue, form)
        val duration = (catalogue as? Catalogue.Ready)?.services
            ?.filter { it.id in form.selectedServiceIds }?.sumOf { it.durationMin } ?: 0
        if (date == null || duration == 0) null else date to duration
    }
        .distinctUntilChanged()
        .flatMapLatest { key ->
            if (key == null) {
                flowOf(SlotsState.Ready(emptyList()))
            } else {
                observeAvailability.dayOptions(args.salonId, args.staffId, key.first, key.second)
                    .map<List<SlotOption>, SlotsState> { SlotsState.Ready(it) }
                    .onStart { emit(SlotsState.Loading) }
                    .catch { emit(SlotsState.Failed(it.toDataError())) }
            }
        }

    val uiState: StateFlow<BookingFlowUiState> = combine(catalogue, form, slots) { catalogue, form, slots ->
        val date = effectiveDate(catalogue, form)
        val ready = catalogue as? Catalogue.Ready
        // A chosen time is only kept while it is still free in the live slot list.
        val start = form.selectedStart?.takeIf { chosen ->
            slots is SlotsState.Ready && slots.options.any { it.start == chosen && it.isAvailable }
        }
        BookingFlowUiState(
            loading = catalogue is Catalogue.Loading,
            loadError = (catalogue as? Catalogue.Failed)?.error,
            salon = ready?.salon,
            staff = ready?.staff,
            services = ready?.services.orEmpty(),
            selectedServiceIds = form.selectedServiceIds,
            today = today,
            lastBookableDate = lastBookableDate,
            visibleMonth = form.visibleMonth ?: YearMonth.from(date ?: today),
            selectedDate = date,
            slots = slots,
            selectedStart = start,
            submitting = form.submitting,
            submitError = form.submitError,
            confirmedBookingId = form.confirmedBookingId,
            waitlistJoinedDate = form.waitlistJoinedDate,
            joiningWaitlist = form.joiningWaitlist,
        )
    }.stateIn(
        scope = viewModelScope,
        // Keep listening for 5 s after the screen goes away (survives rotation without re-reading).
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BookingFlowUiState(
            today = today,
            lastBookableDate = lastBookableDate,
            visibleMonth = YearMonth.from(today),
            selectedServiceIds = form.value.selectedServiceIds,
        ),
    )

    fun onEvent(event: BookingFlowEvent) {
        when (event) {
            is BookingFlowEvent.ToggleService -> form.update {
                val ids = if (event.serviceId in it.selectedServiceIds) it.selectedServiceIds - event.serviceId
                else it.selectedServiceIds + event.serviceId
                it.copy(selectedServiceIds = ids, submitError = null)
            }
            BookingFlowEvent.PreviousMonth -> if (uiState.value.canGoToPreviousMonth) {
                form.update { it.copy(visibleMonth = uiState.value.visibleMonth.minusMonths(1)) }
            }
            BookingFlowEvent.NextMonth -> if (uiState.value.canGoToNextMonth) {
                form.update { it.copy(visibleMonth = uiState.value.visibleMonth.plusMonths(1)) }
            }
            is BookingFlowEvent.SelectDate -> if (uiState.value.isBookableDay(event.date)) {
                form.update { it.copy(selectedDate = event.date, selectedStart = null, submitError = null) }
            }
            is BookingFlowEvent.SelectTime -> form.update { it.copy(selectedStart = event.start, submitError = null) }
            BookingFlowEvent.Confirm -> confirm()
            BookingFlowEvent.DismissError -> form.update { it.copy(submitError = null) }
            BookingFlowEvent.ConfirmationShown -> form.update { it.copy(confirmedBookingId = null) }
            BookingFlowEvent.JoinWaitlist -> joinWaitlist()
        }
    }

    /**
     * Joins the waitlist for the selected day, any time within the barber's working hours.
     * When someone cancels, OfferFreedSlotUseCase offers the freed time to the first in line.
     */
    private fun joinWaitlist() {
        val state = uiState.value
        val date = state.selectedDate ?: return
        val salon = state.salon ?: return
        val staff = state.staff ?: return
        if (!state.canChooseTime || state.joiningWaitlist) return
        val window = AvailabilityCalculator.workingWindow(
            DayAvailabilityInput(date, salon.openingHours, staff.workingHours, emptyList(), emptySet(), Instant.EPOCH),
        ) ?: return
        form.update { it.copy(joiningWaitlist = true, submitError = null) }
        viewModelScope.launch {
            try {
                val user = authRepository.authState.filterIsInstance<AuthState.SignedIn>().first().user
                waitlistRepository.join(
                    WaitlistEntry(
                        id = "",
                        salonId = salon.id,
                        customerId = user.uid,
                        staffId = staff.id,
                        serviceIds = state.selectedServices.map { it.id },
                        durationMin = state.durationMin,
                        date = date,
                        windowStart = SalonTime.toInstant(date, window.start),
                        windowEnd = SalonTime.toInstant(date, window.end),
                        status = WaitlistStatus.WAITING,
                    ),
                )
                form.update { it.copy(joiningWaitlist = false, waitlistJoinedDate = date) }
            } catch (e: DataException) {
                form.update { it.copy(joiningWaitlist = false, submitError = SubmitError.Failed(e.error)) }
            }
        }
    }

    private fun confirm() {
        val state = uiState.value
        val start = state.selectedStart
        // The button is disabled in these cases; this guard also stops a fast double tap.
        if (!state.canConfirm || start == null) return
        form.update { it.copy(submitting = true, submitError = null) }

        viewModelScope.launch {
            try {
                val user = authRepository.authState.filterIsInstance<AuthState.SignedIn>().first().user
                val booking = createBooking(
                    BookingRequest(
                        bookingId = bookingId,
                        salonId = args.salonId,
                        staffId = args.staffId,
                        serviceIds = state.selectedServices.map { it.id },
                        start = start,
                        customerId = user.uid,
                        customerName = user.name,
                    ),
                )
                form.update { it.copy(submitting = false, confirmedBookingId = booking.id) }
            } catch (e: SlotUnavailableException) {
                // Nothing was written. Clear the time so the user picks a new one.
                form.update { it.copy(submitting = false, selectedStart = null, submitError = SubmitError.Unavailable(e.problem)) }
            } catch (e: DataException) {
                form.update { it.copy(submitting = false, submitError = SubmitError.Failed(e.error)) }
            }
        }
    }
}

/** True if both the salon and the barber work on [date] (their hours overlap). */
internal fun workingDay(salon: Salon?, staff: Staff?, date: LocalDate): Boolean {
    if (salon == null || staff == null) return false
    val input = DayAvailabilityInput(
        date = date,
        salonHours = salon.openingHours,
        staffHours = staff.workingHours,
        blocks = emptyList(),
        takenSlots = emptySet(),
        earliestStart = Instant.EPOCH,
    )
    return AvailabilityCalculator.workingWindow(input) != null
}

/** Repositories fail with [DataException]. Anything else is unexpected, shown as UNKNOWN. */
internal fun Throwable.toDataError(): DataError {
    if (this is CancellationException) throw this
    return (this as? DataException)?.error ?: DataError.UNKNOWN
}
