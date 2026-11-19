// Covers: US-04 (see the confirmed booking), US-05 (cancel it), FR-04, FR-05 · Design: booking ticket
package com.group12.salonlink.feature.customer.booking.confirmation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group12.salonlink.core.navigation.BookingConfirmationRoute
import com.group12.salonlink.domain.booking.BookingPolicy
import com.group12.salonlink.domain.model.Booking
import com.group12.salonlink.domain.model.BookingStatus
import com.group12.salonlink.domain.model.DataError
import com.group12.salonlink.domain.model.DataException
import com.group12.salonlink.domain.model.Salon
import com.group12.salonlink.domain.repository.BookingRepository
import com.group12.salonlink.domain.repository.SalonRepository
import com.group12.salonlink.domain.usecase.CancelBookingUseCase
import com.group12.salonlink.feature.customer.booking.toDataError
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * @property justBooked true when we arrive straight from "Confirm appointment" (celebration).
 * @property error set if the booking can't be read (offline without cache, deleted, no access).
 * @property canCancel the customer may still cancel in the app (≥ 24 h ahead).
 * @property lateToCancel still active but inside 24 h: the screen says "call the salon".
 */
data class BookingConfirmationUiState(
    val loading: Boolean = true,
    val justBooked: Boolean = false,
    val booking: Booking? = null,
    val salon: Salon? = null,
    val error: DataError? = null,
    val canCancel: Boolean = false,
    val lateToCancel: Boolean = false,
    val showCancelDialog: Boolean = false,
    val cancelling: Boolean = false,
    val cancelError: DataError? = null,
)

sealed interface BookingConfirmationEvent {
    data object RequestCancel : BookingConfirmationEvent
    data object DismissCancel : BookingConfirmationEvent
    data object ConfirmCancel : BookingConfirmationEvent
}

/**
 * Shows one booking, live. WHY live: if staff check the customer in, or the booking is
 * cancelled from another phone, the status chip on this ticket changes by itself.
 */
@HiltViewModel(assistedFactory = BookingConfirmationViewModel.Factory::class)
class BookingConfirmationViewModel @AssistedInject constructor(
    @Assisted private val args: BookingConfirmationRoute,
    bookingRepository: BookingRepository,
    salonRepository: SalonRepository,
    private val cancelBooking: CancelBookingUseCase,
    private val clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(args: BookingConfirmationRoute): BookingConfirmationViewModel
    }

    private data class CancelUi(val dialog: Boolean = false, val cancelling: Boolean = false, val error: DataError? = null)

    private val cancelUi = MutableStateFlow(CancelUi())

    val uiState: StateFlow<BookingConfirmationUiState> = combine(
        bookingRepository.observeBooking(args.salonId, args.bookingId),
        salonRepository.observeSalon(args.salonId),
        cancelUi,
    ) { booking, salon, cancel ->
        val now = clock.instant()
        val canCancel = booking != null && BookingPolicy.customerCanCancel(booking, now)
        BookingConfirmationUiState(
            loading = false,
            justBooked = args.justBooked,
            booking = booking,
            salon = salon,
            error = if (booking == null) DataError.NOT_FOUND else null,
            canCancel = canCancel,
            lateToCancel = booking != null && !canCancel && booking.status == BookingStatus.BOOKED && booking.isUpcoming(now),
            showCancelDialog = cancel.dialog && canCancel,
            cancelling = cancel.cancelling,
            cancelError = cancel.error,
        )
    }
        .catch { emit(BookingConfirmationUiState(loading = false, justBooked = args.justBooked, error = it.toDataError())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookingConfirmationUiState(justBooked = args.justBooked))

    fun onEvent(event: BookingConfirmationEvent) {
        when (event) {
            BookingConfirmationEvent.RequestCancel -> cancelUi.update { it.copy(dialog = true, error = null) }
            BookingConfirmationEvent.DismissCancel -> cancelUi.update { if (it.cancelling) it else it.copy(dialog = false) }
            BookingConfirmationEvent.ConfirmCancel -> confirmCancel()
        }
    }

    private fun confirmCancel() {
        if (cancelUi.value.cancelling) return
        cancelUi.update { it.copy(cancelling = true) }
        viewModelScope.launch {
            val error = try {
                cancelBooking(args.salonId, args.bookingId)
                null
            } catch (e: DataException) {
                e.error
            }
            // The ticket updates itself to "Cancelled" through the live booking listener.
            cancelUi.update { CancelUi(error = error) }
        }
    }
}
