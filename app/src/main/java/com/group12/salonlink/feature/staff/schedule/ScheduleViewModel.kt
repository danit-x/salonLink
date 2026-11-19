// Covers: US-07, US-09, FR-06, FR-08, FR-10 (day schedule, status updates, daily dashboard)
package com.group12.salonlink.feature.staff.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group12.salonlink.core.util.SalonTime
import com.group12.salonlink.domain.model.AuthState
import com.group12.salonlink.domain.model.Booking
import com.group12.salonlink.domain.model.BookingStatus
import com.group12.salonlink.domain.model.BookingType
import com.group12.salonlink.domain.model.DataError
import com.group12.salonlink.domain.model.DataException
import com.group12.salonlink.domain.model.Role
import com.group12.salonlink.domain.model.Staff
import com.group12.salonlink.domain.model.UserProfile
import com.group12.salonlink.domain.repository.AuthRepository
import com.group12.salonlink.domain.repository.BookingRepository
import com.group12.salonlink.domain.repository.SalonRepository
import com.group12.salonlink.domain.usecase.UpdateBookingStatusUseCase
import com.group12.salonlink.feature.customer.booking.toDataError
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Numbers for the owner's "Today at a glance" cards. */
data class DayStats(
    val bookings: Int = 0,
    val completed: Int = 0,
    val walkIns: Int = 0,
    /** Paid so far: completed bookings. */
    val revenue: Long = 0,
    /** If every remaining booking is completed too. */
    val expectedRevenue: Long = 0,
) {
    companion object {
        /** Cancelled and missed bookings don't count as work done or expected. */
        fun of(bookings: List<Booking>): DayStats {
            val live = bookings.filter { it.status != BookingStatus.CANCELLED && it.status != BookingStatus.MISSED }
            return DayStats(
                bookings = live.size,
                completed = live.count { it.status == BookingStatus.COMPLETED },
                walkIns = live.count { it.type == BookingType.WALK_IN },
                revenue = live.filter { it.status == BookingStatus.COMPLETED }.sumOf { it.totalPrice },
                expectedRevenue = live.sumOf { it.totalPrice },
            )
        }
    }
}

/** A status change waiting for "are you sure?" (no-show and cancel can't be undone). */
data class PendingChange(val booking: Booking, val next: BookingStatus)

/** A one-off message: a status was changed, or the change failed. */
sealed interface ScheduleMessage {
    data class Updated(val customerName: String, val status: BookingStatus) : ScheduleMessage
    data class Failed(val error: DataError) : ScheduleMessage
}

/**
 * @property isOwner owners see every barber (with a filter) and the day's numbers.
 * @property bookings the visible bookings of [date], by start time.
 */
data class ScheduleUiState(
    val loading: Boolean = true,
    val isOwner: Boolean = false,
    val date: LocalDate = LocalDate.MIN,
    val isToday: Boolean = true,
    val staff: List<Staff> = emptyList(),
    val staffFilter: String? = null,
    val bookings: List<Booking> = emptyList(),
    val stats: DayStats = DayStats(),
    val busyIds: Set<String> = emptySet(),
    val pending: PendingChange? = null,
    val message: ScheduleMessage? = null,
    val error: DataError? = null,
)

sealed interface ScheduleEvent {
    data object PreviousDay : ScheduleEvent
    data object NextDay : ScheduleEvent
    data object Today : ScheduleEvent
    data class FilterStaff(val staffId: String?) : ScheduleEvent
    /** Tapped a status button. No-show and cancel ask for confirmation first. */
    data class ChangeStatus(val booking: Booking, val next: BookingStatus) : ScheduleEvent
    data object ConfirmPending : ScheduleEvent
    data object DismissPending : ScheduleEvent
    data object MessageShown : ScheduleEvent
}

/**
 * One salon day, live. Used by both staff (their own bookings) and the owner (everyone + numbers).
 * The role comes from the signed-in profile, so the same screen serves both shells.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val bookingRepository: BookingRepository,
    private val salonRepository: SalonRepository,
    private val updateStatus: UpdateBookingStatusUseCase,
    clock: Clock,
) : ViewModel() {

    private val today = LocalDate.now(clock.withZone(SalonTime.SALON_ZONE))

    private data class Ui(
        val date: LocalDate,
        val staffFilter: String? = null,
        val busyIds: Set<String> = emptySet(),
        val pending: PendingChange? = null,
        val message: ScheduleMessage? = null,
    )

    private val ui = MutableStateFlow(Ui(date = today))

    private val user: Flow<UserProfile> = authRepository.authState
        .filterIsInstance<AuthState.SignedIn>()
        .map { it.user }
        .distinctUntilChanged()

    private sealed interface Day {
        data object Loading : Day
        data class Ready(val bookings: List<Booking>, val staff: List<Staff>) : Day
        data class Failed(val error: DataError) : Day
    }

    private val day: Flow<Day> = combine(user, ui.map { it.date }.distinctUntilChanged()) { u, d -> u to d }
        .flatMapLatest { (u, date) ->
            val salonId = u.salonId ?: return@flatMapLatest flowOf(Day.Failed(DataError.PERMISSION_DENIED))
            val bookings = if (u.role == Role.OWNER) bookingRepository.observeSalonDay(salonId, date)
            else bookingRepository.observeStaffDay(salonId, u.staffId.orEmpty(), date)
            combine(bookings, salonRepository.observeStaff(salonId)) { b, s -> Day.Ready(b.sortedBy { it.start }, s) as Day }
                .onStart { emit(Day.Loading) }
                .catch { emit(Day.Failed(it.toDataError())) }
        }

    val uiState: StateFlow<ScheduleUiState> = combine(user, day, ui) { u, day, ui ->
        val ready = day as? Day.Ready
        val all = ready?.bookings.orEmpty()
        ScheduleUiState(
            loading = day is Day.Loading,
            isOwner = u.role == Role.OWNER,
            date = ui.date,
            isToday = ui.date == today,
            staff = ready?.staff.orEmpty(),
            staffFilter = ui.staffFilter,
            bookings = all.filter { ui.staffFilter == null || it.staffId == ui.staffFilter },
            stats = DayStats.of(all),
            busyIds = ui.busyIds,
            pending = ui.pending,
            message = ui.message,
            error = (day as? Day.Failed)?.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleUiState(date = today))

    fun onEvent(event: ScheduleEvent) {
        when (event) {
            ScheduleEvent.PreviousDay -> ui.update { it.copy(date = it.date.minusDays(1)) }
            ScheduleEvent.NextDay -> ui.update { it.copy(date = it.date.plusDays(1)) }
            ScheduleEvent.Today -> ui.update { it.copy(date = today) }
            is ScheduleEvent.FilterStaff -> ui.update { it.copy(staffFilter = event.staffId) }
            is ScheduleEvent.ChangeStatus ->
                if (event.next == BookingStatus.MISSED || event.next == BookingStatus.CANCELLED) {
                    ui.update { it.copy(pending = PendingChange(event.booking, event.next)) }
                } else {
                    apply(event.booking, event.next)
                }
            ScheduleEvent.ConfirmPending -> ui.value.pending?.let {
                ui.update { s -> s.copy(pending = null) }
                apply(it.booking, it.next)
            }
            ScheduleEvent.DismissPending -> ui.update { it.copy(pending = null) }
            ScheduleEvent.MessageShown -> ui.update { it.copy(message = null) }
        }
    }

    private fun apply(booking: Booking, next: BookingStatus) {
        if (booking.id in ui.value.busyIds) return // a double tap must not send two updates
        ui.update { it.copy(busyIds = it.busyIds + booking.id) }
        viewModelScope.launch {
            val message = try {
                updateStatus(booking.salonId, booking.id, next)
                ScheduleMessage.Updated(booking.customerName, next)
            } catch (e: DataException) {
                ScheduleMessage.Failed(e.error)
            }
            ui.update { it.copy(busyIds = it.busyIds - booking.id, message = message) }
        }
    }
}

/**
 * The buttons a booking card shows, main action first. WHY derived from the status table:
 * the screen can never offer a move the rules would refuse.
 */
fun Booking.staffActions(now: java.time.Instant): List<BookingStatus> = buildList {
    val next = status.nextStatuses
    listOf(BookingStatus.CHECKED_IN, BookingStatus.IN_SERVICE, BookingStatus.COMPLETED)
        .firstOrNull { it in next }?.let(::add)
    // A no-show only makes sense once the start time has passed.
    if (BookingStatus.MISSED in next && now >= start) add(BookingStatus.MISSED)
    if (BookingStatus.CANCELLED in next) add(BookingStatus.CANCELLED)
}
