// Covers: US-08, FR-09 (staff add walk-in customers) · Design: new, in the app's visual language
package com.group12.salonlink.feature.staff.walkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group12.salonlink.core.util.SalonTime
import com.group12.salonlink.domain.booking.AvailabilityCalculator
import com.group12.salonlink.domain.booking.SlotGrid
import com.group12.salonlink.domain.model.AuthState
import com.group12.salonlink.domain.model.BookingType
import com.group12.salonlink.domain.model.DataError
import com.group12.salonlink.domain.model.DataException
import com.group12.salonlink.domain.model.Role
import com.group12.salonlink.domain.model.SalonService
import com.group12.salonlink.domain.model.Staff
import com.group12.salonlink.domain.model.UserProfile
import com.group12.salonlink.domain.repository.AuthRepository
import com.group12.salonlink.domain.repository.SalonRepository
import com.group12.salonlink.domain.usecase.BookingRequest
import com.group12.salonlink.domain.usecase.CreateBookingUseCase
import com.group12.salonlink.domain.usecase.ObserveAvailabilityUseCase
import com.group12.salonlink.domain.validation.FieldError
import com.group12.salonlink.domain.validation.FormValidators
import com.group12.salonlink.feature.customer.booking.toDataError
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WalkInUiState(
    val loading: Boolean = true,
    val salonId: String? = null,
    /** Staff can only add walk-ins for themselves; owners pick a barber. */
    val canPickBarber: Boolean = false,
    val staff: List<Staff> = emptyList(),
    val staffId: String? = null,
    val services: List<SalonService> = emptyList(),
    val selectedServiceIds: Set<String> = emptySet(),
    val name: String = "",
    val nameError: FieldError? = null,
    /** Free start times left today for the chosen barber and services (10-minute steps). */
    val times: List<Instant> = emptyList(),
    val start: Instant? = null,
    val submitting: Boolean = false,
    val added: Pair<String, Instant>? = null,
    val error: DataError? = null,
) {
    val durationMin: Int get() = services.filter { it.id in selectedServiceIds }.sumOf { it.durationMin }
    val canSubmit: Boolean get() = selectedServiceIds.isNotEmpty() && start != null && name.isNotBlank() && !submitting
}

sealed interface WalkInEvent {
    data class NameChanged(val value: String) : WalkInEvent
    data class PickBarber(val staffId: String) : WalkInEvent
    data class ToggleService(val serviceId: String) : WalkInEvent
    data class PickTime(val start: Instant) : WalkInEvent
    data object Submit : WalkInEvent
    data object MessageShown : WalkInEvent
}

/**
 * Adds a walk-in guest through the same booking engine as the app (so a walk-in can never
 * overlap a booked customer). WHY 10-minute steps here (customers see 30): a walk-in is
 * standing at the counter, so the earliest free 10 minutes matters.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WalkInViewModel @Inject constructor(
    authRepository: AuthRepository,
    salonRepository: SalonRepository,
    private val observeAvailability: ObserveAvailabilityUseCase,
    private val createBooking: CreateBookingUseCase,
    private val clock: Clock,
) : ViewModel() {

    private data class Form(
        val staffId: String? = null,
        val selected: Set<String> = emptySet(),
        val name: String = "",
        val nameError: FieldError? = null,
        val start: Instant? = null,
        val submitting: Boolean = false,
        val added: Pair<String, Instant>? = null,
        val error: DataError? = null,
        /** New id after every successful add; kept on failure so a retry can't duplicate (T11). */
        val bookingId: String = UUID.randomUUID().toString(),
    )

    private val form = MutableStateFlow(Form())

    private val user: Flow<UserProfile> = authRepository.authState
        .filterIsInstance<AuthState.SignedIn>().map { it.user }.distinctUntilChanged()

    private val catalogue = user.flatMapLatest { u ->
        val salonId = u.salonId ?: return@flatMapLatest flowOf(Triple(u, emptyList<Staff>(), emptyList<SalonService>()))
        combine(salonRepository.observeStaff(salonId), salonRepository.observeServices(salonId)) { s, sv -> Triple(u, s, sv) }
    }.catch { emit(Triple(UserProfile("", "", "", null, Role.STAFF), emptyList(), emptyList())) }

    private val times: Flow<List<Instant>> = combine(catalogue, form) { (u, staff, services), f ->
        val staffId = chosenStaffId(f, u, staff)
        val duration = services.filter { it.id in f.selected }.sumOf { it.durationMin }
        Triple(u.salonId, staffId, duration)
    }.distinctUntilChanged().flatMapLatest { (salonId, staffId, duration) ->
        if (salonId == null || staffId == null || duration == 0) return@flatMapLatest flowOf(emptyList())
        val today = LocalDate.now(clock.withZone(SalonTime.SALON_ZONE))
        observeAvailability.observeDayInput(salonId, staffId, today).map { input ->
            if (input == null) emptyList()
            else {
                // A walk-in may start in the current 10-minute slot (CreateBookingUseCase allows it).
                val slotStart = SlotGrid.startOf(SlotGrid.slotOf(clock.instant()))
                AvailabilityCalculator.dayOptions(input.copy(earliestStart = slotStart), duration, stepMin = SlotGrid.SLOT_MINUTES)
                    .filter { it.isAvailable }.map { it.start }.take(MAX_TIMES)
            }
        }.catch { emit(emptyList()) }
    }

    val uiState: StateFlow<WalkInUiState> = combine(catalogue, form, times) { (u, staff, services), f, times ->
        val staffId = chosenStaffId(f, u, staff)
        WalkInUiState(
            loading = u.uid.isEmpty() && staff.isEmpty(),
            salonId = u.salonId,
            canPickBarber = u.role == Role.OWNER,
            staff = staff,
            staffId = staffId,
            services = services.filter { staffId != null && it.isOfferedBy(staffId) },
            selectedServiceIds = f.selected,
            name = f.name,
            nameError = f.nameError,
            times = times,
            start = f.start?.takeIf { it in times },
            submitting = f.submitting,
            added = f.added,
            error = f.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WalkInUiState())

    fun onEvent(event: WalkInEvent) {
        when (event) {
            is WalkInEvent.NameChanged -> form.update { it.copy(name = event.value, nameError = null) }
            is WalkInEvent.PickBarber -> form.update { it.copy(staffId = event.staffId, selected = emptySet(), start = null) }
            is WalkInEvent.ToggleService -> form.update {
                val ids = if (event.serviceId in it.selected) it.selected - event.serviceId else it.selected + event.serviceId
                it.copy(selected = ids, start = null)
            }
            is WalkInEvent.PickTime -> form.update { it.copy(start = event.start) }
            WalkInEvent.Submit -> submit()
            WalkInEvent.MessageShown -> form.update { it.copy(added = null, error = null) }
        }
    }

    private fun submit() {
        val state = uiState.value
        val nameError = FormValidators.name(state.name)
        if (nameError != null) {
            form.update { it.copy(nameError = nameError) }
            return
        }
        val start = state.start ?: return
        val staffId = state.staffId ?: return
        if (!state.canSubmit) return
        form.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            try {
                val salonId = state.salonId ?: throw DataException(DataError.PERMISSION_DENIED)
                createBooking(
                    BookingRequest(
                        bookingId = form.value.bookingId,
                        salonId = salonId,
                        staffId = staffId,
                        serviceIds = state.services.filter { it.id in state.selectedServiceIds }.map { it.id },
                        start = start,
                        customerId = null,
                        customerName = state.name.trim(),
                        type = BookingType.WALK_IN,
                    ),
                )
                // Clear the form for the next guest, but keep the chosen barber.
                form.update { Form(staffId = it.staffId, added = state.name.trim() to start) }
            } catch (e: DataException) {
                form.update { it.copy(submitting = false, error = e.error) }
            }
        }
    }

    /**
     * The barber the walk-in is for: the one picked, else the signed-in staff member, else (for an
     * owner) the first barber. WHY one function: the chip highlight and the time list must agree.
     */
    private fun chosenStaffId(f: Form, u: UserProfile, staff: List<Staff>): String? =
        f.staffId ?: u.staffId ?: staff.firstOrNull()?.id

    private companion object {
        const val MAX_TIMES = 12
    }
}
