// Covers: US-05, FR-05 (view and cancel bookings), FR-11 (waitlist offers) · Design: new
package com.group12.salonlink.feature.customer.bookings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group12.salonlink.domain.booking.BookingPolicy
import com.group12.salonlink.domain.model.AuthState
import com.group12.salonlink.domain.model.Booking
import com.group12.salonlink.domain.model.BookingStatus
import com.group12.salonlink.domain.model.DataError
import com.group12.salonlink.domain.model.DataException
import com.group12.salonlink.domain.model.Salon
import com.group12.salonlink.domain.model.WaitlistEntry
import com.group12.salonlink.domain.model.WaitlistStatus
import com.group12.salonlink.domain.repository.AuthRepository
import com.group12.salonlink.domain.repository.BookingRepository
import com.group12.salonlink.domain.repository.SalonRepository
import com.group12.salonlink.domain.repository.WaitlistRepository
import com.group12.salonlink.domain.usecase.AcceptWaitlistOfferUseCase
import com.group12.salonlink.domain.usecase.CancelBookingUseCase
import com.group12.salonlink.feature.customer.booking.toDataError
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BookingsTab { UPCOMING, HISTORY }

/**
 * One booking in the list.
 * @property canCancel the customer may still cancel it in the app (BOOKED and ≥ 24 h ahead).
 * @property lateToCancel still active but inside the 24 h window: show "call the salon".
 */
data class BookingRow(
    val booking: Booking,
    val salon: Salon?,
    val canCancel: Boolean,
    val lateToCancel: Boolean,
)

/** One waitlist entry as shown: with its barber's name and whether an offer is open right now. */
data class WaitlistRow(val entry: WaitlistEntry, val staffName: String, val offerOpen: Boolean)

/** A short message shown once at the bottom of the screen. */
sealed interface BookingsMessage {
    data object Cancelled : BookingsMessage
    data class Accepted(val start: Instant) : BookingsMessage
    data class Failed(val error: DataError) : BookingsMessage
}

data class MyBookingsUiState(
    val loading: Boolean = true,
    val tab: BookingsTab = BookingsTab.UPCOMING,
    val upcoming: List<BookingRow> = emptyList(),
    val history: List<BookingRow> = emptyList(),
    val waitlist: List<WaitlistRow> = emptyList(),
    val error: DataError? = null,
    /** The booking whose "Cancel?" dialog is open. */
    val cancelTarget: BookingRow? = null,
    val cancelling: Boolean = false,
    val busyWaitlistId: String? = null,
    val message: BookingsMessage? = null,
) {
    val visible: List<BookingRow> get() = if (tab == BookingsTab.UPCOMING) upcoming else history
}

sealed interface MyBookingsEvent {
    data class SelectTab(val tab: BookingsTab) : MyBookingsEvent
    data class RequestCancel(val bookingId: String) : MyBookingsEvent
    data object DismissCancel : MyBookingsEvent
    data object ConfirmCancel : MyBookingsEvent
    data class AcceptOffer(val entryId: String) : MyBookingsEvent
    data class LeaveWaitlist(val entryId: String) : MyBookingsEvent
    data object MessageShown : MyBookingsEvent
}

/**
 * "Your bookings": upcoming visits (soonest first), history (newest first) and the waitlist with
 * any open offer, live from Firestore. Cancelling goes through [CancelBookingUseCase].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MyBookingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    bookingRepository: BookingRepository,
    salonRepository: SalonRepository,
    private val waitlistRepository: WaitlistRepository,
    private val cancelBooking: CancelBookingUseCase,
    private val acceptOffer: AcceptWaitlistOfferUseCase,
    private val clock: Clock,
) : ViewModel() {

    private data class Ui(
        val tab: BookingsTab = BookingsTab.UPCOMING,
        val cancelTargetId: String? = null,
        val cancelling: Boolean = false,
        val busyWaitlistId: String? = null,
        val message: BookingsMessage? = null,
    )

    private val ui = MutableStateFlow(Ui())

    private sealed interface Load {
        data object Loading : Load
        data class Ready(val bookings: List<Booking>) : Load
        data class Failed(val error: DataError) : Load
    }

    private val uid = authRepository.authState.filterIsInstance<AuthState.SignedIn>().map { it.user.uid }

    private val bookings = uid
        .flatMapLatest { bookingRepository.observeCustomerBookings(it) }
        .map<List<Booking>, Load> { Load.Ready(it) }
        .onStart { emit(Load.Loading) }
        .catch { emit(Load.Failed(it.toDataError())) }

    private val salons = salonRepository.observeSalons()
        .map { list -> list.associateBy { it.id } }
        .onStart { emit(emptyMap()) }
        .catch { emit(emptyMap()) } // names are a nicety; the list still works without them

    /** Barber names of every salon (for "waiting for … with Kasun"). */
    private val staffNames = salonRepository.observeSalons()
        .flatMapLatest { list ->
            if (list.isEmpty()) flowOf(emptyMap())
            else combine(list.map { salonRepository.observeStaff(it.id, includeInactive = true) }) { all ->
                all.flatMap { it }.associate { it.id to it.name }
            }
        }
        .onStart { emit(emptyMap()) }
        .catch { emit(emptyMap()) }

    private val waitlist = uid
        .flatMapLatest { waitlistRepository.observeMine(it) }
        .onStart { emit(emptyList()) }
        .catch { emit(emptyList()) } // the bookings still show if the waitlist can't be read

    val uiState: StateFlow<MyBookingsUiState> = combine(bookings, salons, staffNames, waitlist, ui) { load, salons, names, waiting, ui ->
        val now = clock.instant()
        val all = (load as? Load.Ready)?.bookings.orEmpty().map { b ->
            val canCancel = BookingPolicy.customerCanCancel(b, now)
            BookingRow(b, salons[b.salonId], canCancel, lateToCancel = !canCancel && b.status == BookingStatus.BOOKED && b.isUpcoming(now))
        }
        val (upcoming, history) = all.partition { it.booking.isUpcoming(now) }
        MyBookingsUiState(
            loading = load is Load.Loading,
            tab = ui.tab,
            upcoming = upcoming.sortedBy { it.booking.start },
            history = history.sortedByDescending { it.booking.start },
            waitlist = waiting
                .filter { (it.status == WaitlistStatus.WAITING || it.status == WaitlistStatus.OFFERED) && it.windowEnd > now }
                .sortedBy { it.date }
                .map { WaitlistRow(it, names[it.staffId].orEmpty(), offerOpen = it.activeOffer(now) != null) },
            error = (load as? Load.Failed)?.error,
            cancelTarget = all.firstOrNull { it.booking.id == ui.cancelTargetId },
            cancelling = ui.cancelling,
            busyWaitlistId = ui.busyWaitlistId,
            message = ui.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyBookingsUiState())

    fun onEvent(event: MyBookingsEvent) {
        when (event) {
            is MyBookingsEvent.SelectTab -> ui.update { it.copy(tab = event.tab) }
            is MyBookingsEvent.RequestCancel -> ui.update { it.copy(cancelTargetId = event.bookingId) }
            MyBookingsEvent.DismissCancel -> ui.update { if (it.cancelling) it else it.copy(cancelTargetId = null) }
            MyBookingsEvent.ConfirmCancel -> confirmCancel()
            is MyBookingsEvent.AcceptOffer -> accept(event.entryId)
            is MyBookingsEvent.LeaveWaitlist -> leave(event.entryId)
            MyBookingsEvent.MessageShown -> ui.update { it.copy(message = null) }
        }
    }

    private fun confirmCancel() {
        val target = uiState.value.cancelTarget?.booking ?: return
        if (ui.value.cancelling) return
        ui.update { it.copy(cancelling = true) }
        viewModelScope.launch {
            val message = try {
                cancelBooking(target.salonId, target.id)
                BookingsMessage.Cancelled
            } catch (e: DataException) {
                BookingsMessage.Failed(e.error)
            }
            ui.update { it.copy(cancelling = false, cancelTargetId = null, message = message) }
        }
    }

    private fun accept(entryId: String) {
        val row = uiState.value.waitlist.firstOrNull { it.entry.id == entryId } ?: return
        runWaitlist(entryId) {
            val user = authRepository.authState.filterIsInstance<AuthState.SignedIn>().first().user
            val booking = acceptOffer(row.entry, user.name)
            BookingsMessage.Accepted(booking.start)
        }
    }

    private fun leave(entryId: String) {
        val row = uiState.value.waitlist.firstOrNull { it.entry.id == entryId } ?: return
        runWaitlist(entryId) {
            waitlistRepository.leave(row.entry.salonId, entryId)
            null
        }
    }

    private fun runWaitlist(entryId: String, block: suspend () -> BookingsMessage?) {
        if (ui.value.busyWaitlistId != null) return
        ui.update { it.copy(busyWaitlistId = entryId) }
        viewModelScope.launch {
            val message = try {
                block()
            } catch (e: DataException) {
                BookingsMessage.Failed(e.error)
            }
            ui.update { it.copy(busyWaitlistId = null, message = message) }
        }
    }
}
