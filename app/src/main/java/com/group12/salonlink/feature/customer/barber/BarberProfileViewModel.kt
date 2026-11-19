// Covers: US-02 (see a barber before booking), FR-02 · Figma: "Barber profile · Kasun Perera"
package com.group12.salonlink.feature.customer.barber

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group12.salonlink.core.navigation.BarberProfileRoute
import com.group12.salonlink.domain.model.DataError
import com.group12.salonlink.domain.model.Salon
import com.group12.salonlink.domain.model.Staff
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
 * @property fromPrice cheapest service this barber offers ("From Rs. 1,500"), null if none.
 */
data class BarberProfileUiState(
    val loading: Boolean = true,
    val salon: Salon? = null,
    val staff: Staff? = null,
    val fromPrice: Long? = null,
    val error: DataError? = null,
)

/** Reads one barber, their salon and their services, live. */
@HiltViewModel(assistedFactory = BarberProfileViewModel.Factory::class)
class BarberProfileViewModel @AssistedInject constructor(
    @Assisted args: BarberProfileRoute,
    salonRepository: SalonRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(args: BarberProfileRoute): BarberProfileViewModel
    }

    val uiState: StateFlow<BarberProfileUiState> = combine(
        salonRepository.observeSalon(args.salonId),
        salonRepository.observeStaffMember(args.salonId, args.staffId),
        salonRepository.observeServices(args.salonId),
    ) { salon, staff, services ->
        val found = salon != null && staff != null && staff.active
        BarberProfileUiState(
            loading = false,
            salon = salon,
            staff = staff?.takeIf { found },
            fromPrice = staff?.let { s -> services.filter { it.isOfferedBy(s.id) }.minOfOrNull { it.price } },
            error = if (found) null else DataError.NOT_FOUND,
        )
    }
        .catch { emit(BarberProfileUiState(loading = false, error = it.toDataError())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BarberProfileUiState())
}
