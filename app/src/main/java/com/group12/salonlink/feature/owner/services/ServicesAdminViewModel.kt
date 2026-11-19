// Covers: US-09, FR-10 (owner manages services and prices) · Design: new
package com.group12.salonlink.feature.owner.services

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group12.salonlink.domain.model.AuthState
import com.group12.salonlink.domain.model.DataError
import com.group12.salonlink.domain.model.DataException
import com.group12.salonlink.domain.model.SalonService
import com.group12.salonlink.domain.model.ServiceCategory
import com.group12.salonlink.domain.model.Staff
import com.group12.salonlink.domain.repository.AuthRepository
import com.group12.salonlink.domain.repository.SalonAdminRepository
import com.group12.salonlink.domain.repository.SalonRepository
import com.group12.salonlink.feature.customer.booking.toDataError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The service being added or edited, as typed (numbers are text until saved). */
data class ServiceDraft(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val category: ServiceCategory = ServiceCategory.HAIRCUT,
    val duration: String = "30",
    val price: String = "",
    val staffIds: Set<String> = emptySet(),
    val active: Boolean = true,
) {
    val durationMin: Int? get() = duration.trim().toIntOrNull()?.takeIf { it in 10..160 && it % 10 == 0 }
    val priceRupees: Long? get() = price.trim().toLongOrNull()?.takeIf { it in 0..1_000_000 }
    val nameValid: Boolean get() = name.trim().length in 1..60
    val isValid: Boolean get() = nameValid && durationMin != null && priceRupees != null

    fun toService() = SalonService(
        id = id, name = name.trim(), description = description.trim().take(200), category = category,
        durationMin = durationMin ?: 0, price = priceRupees ?: 0, staffIds = staffIds.toList(), active = active,
    )

    companion object {
        fun of(s: SalonService) = ServiceDraft(
            s.id, s.name, s.description, s.category, s.durationMin.toString(), s.price.toString(), s.staffIds.toSet(), s.active,
        )
    }
}

data class ServicesAdminUiState(
    val loading: Boolean = true,
    val services: List<SalonService> = emptyList(),
    val staff: List<Staff> = emptyList(),
    val editor: ServiceDraft? = null,
    /** Show field errors only after the first Save attempt. */
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: DataError? = null,
)

sealed interface ServicesAdminEvent {
    data object Add : ServicesAdminEvent
    data class Edit(val service: SalonService) : ServicesAdminEvent
    data class Change(val draft: ServiceDraft) : ServicesAdminEvent
    data object Save : ServicesAdminEvent
    data object Close : ServicesAdminEvent
    data object MessageShown : ServicesAdminEvent
}

/** Owner's list of services (including hidden ones) with an add / edit sheet. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ServicesAdminViewModel @Inject constructor(
    authRepository: AuthRepository,
    salonRepository: SalonRepository,
    private val admin: SalonAdminRepository,
) : ViewModel() {

    private data class Ui(
        val editor: ServiceDraft? = null,
        val showErrors: Boolean = false,
        val saving: Boolean = false,
        val saved: Boolean = false,
        val error: DataError? = null,
    )

    private val ui = MutableStateFlow(Ui())
    private val salonId = authRepository.authState.filterIsInstance<AuthState.SignedIn>().map { it.user.salonId }

    private var currentSalonId: String? = null

    val uiState: StateFlow<ServicesAdminUiState> = salonId.flatMapLatest { id ->
        currentSalonId = id
        if (id == null) flowOf(Pair(emptyList<SalonService>(), emptyList<Staff>()))
        else combine(salonRepository.observeServices(id, includeInactive = true), salonRepository.observeStaff(id)) { a, b -> a to b }
    }.combine(ui) { (services, staff), ui ->
        ServicesAdminUiState(false, services, staff, ui.editor, ui.showErrors, ui.saving, ui.saved, ui.error)
    }.catch { emit(ServicesAdminUiState(loading = false, error = it.toDataError())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServicesAdminUiState())

    fun onEvent(event: ServicesAdminEvent) {
        when (event) {
            ServicesAdminEvent.Add -> ui.update {
                // A new service is offered by everyone by default; untick to restrict.
                Ui(editor = ServiceDraft(staffIds = uiState.value.staff.map { s -> s.id }.toSet()))
            }
            is ServicesAdminEvent.Edit -> ui.update { Ui(editor = ServiceDraft.of(event.service)) }
            is ServicesAdminEvent.Change -> ui.update { it.copy(editor = event.draft) }
            ServicesAdminEvent.Close -> ui.update { if (it.saving) it else it.copy(editor = null) }
            ServicesAdminEvent.MessageShown -> ui.update { it.copy(saved = false, error = null) }
            ServicesAdminEvent.Save -> save()
        }
    }

    private fun save() {
        val draft = ui.value.editor ?: return
        val salon = currentSalonId ?: return
        if (!draft.isValid) {
            ui.update { it.copy(showErrors = true) }
            return
        }
        ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                admin.saveService(salon, draft.toService())
                ui.update { Ui(saved = true) }
            } catch (e: DataException) {
                ui.update { it.copy(saving = false, error = e.error) }
            }
        }
    }
}
