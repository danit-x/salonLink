// Covers: US-04 (see the confirmed booking), FR-04 · Figma: "Booking confirmation · SalonLink"
package com.group12.salonlink.feature.customer.booking.confirmation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group12.salonlink.core.navigation.BookingConfirmationRoute
import com.group12.salonlink.domain.model.Booking
import com.group12.salonlink.domain.model.DataError
import com.group12.salonlink.domain.model.Salon
import com.group12.salonlink.domain.repository.BookingRepository
import com.group12.salonlink.domain.repository.SalonRepository
import com.group12.salonlink.feature.customer.booking.toDataError
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * @property justBooked true when we arrive straight from "Confirm appointment" (celebration).
 * @property error set if the booking can't be read (offline without cache, deleted, no access).
 */
data class BookingConfirmationUiState(
    val loading: Boolean = true,
    val justBooked: Boolean = false,
    val booking: Booking? = null,
    val salon: Salon? = null,
    val error: DataError? = null,
)

/**
 * Shows one booking, live. WHY live: if staff check the customer in, or the booking is
 * cancelled from another phone, the status chip on this ticket changes by itself.
 */
@HiltViewModel(assistedFactory = BookingConfirmationViewModel.Factory::class)
class BookingConfirmationViewModel @AssistedInject constructor(
    @Assisted args: BookingConfirmationRoute,
    bookingRepository: BookingRepository,
    salonRepository: SalonRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(args: BookingConfirmationRoute): BookingConfirmationViewModel
    }

    val uiState: StateFlow<BookingConfirmationUiState> = combine(
        bookingRepository.observeBooking(args.salonId, args.bookingId),
        salonRepository.observeSalon(args.salonId),
    ) { booking, salon ->
        BookingConfirmationUiState(
            loading = false,
            justBooked = args.justBooked,
            booking = booking,
            salon = salon,
            error = if (booking == null) DataError.NOT_FOUND else null,
        )
    }
        .catch { emit(BookingConfirmationUiState(loading = false, justBooked = args.justBooked, error = it.toDataError())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookingConfirmationUiState(justBooked = args.justBooked))
}
