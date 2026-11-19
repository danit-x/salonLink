// Covers: US-09, FR-10 (salon details and opening hours) · Design: new, in the app's visual language
package com.group12.salonlink.feature.owner.salon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group12.salonlink.R
import com.group12.salonlink.core.designsystem.component.BannerTone
import com.group12.salonlink.core.designsystem.component.FormBanner
import com.group12.salonlink.core.designsystem.component.GlassButton
import com.group12.salonlink.core.designsystem.component.GradientButton
import com.group12.salonlink.core.designsystem.component.SalonTextField
import com.group12.salonlink.core.designsystem.component.SalonTopBar
import com.group12.salonlink.core.designsystem.theme.Spacing
import com.group12.salonlink.domain.model.AuthState
import com.group12.salonlink.domain.model.DataError
import com.group12.salonlink.domain.model.DataException
import com.group12.salonlink.domain.model.Salon
import com.group12.salonlink.domain.repository.AuthRepository
import com.group12.salonlink.domain.repository.SalonAdminRepository
import com.group12.salonlink.domain.repository.SalonRepository
import com.group12.salonlink.feature.customer.booking.message
import com.group12.salonlink.feature.owner.HoursDraft
import com.group12.salonlink.feature.owner.WeeklyHoursEditor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SalonSettingsUiState(
    val loading: Boolean = true,
    val salon: Salon? = null,
    val name: String = "",
    val address: String = "",
    val area: String = "",
    val phone: String = "",
    val hours: HoursDraft? = null,
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: DataError? = null,
) {
    val isValid: Boolean get() = name.trim().length in 1..80 && hours?.isValid == true
}

/** Loads the salon once, edits a local copy, saves on demand. */
@HiltViewModel
class SalonSettingsViewModel @Inject constructor(
    authRepository: AuthRepository,
    salonRepository: SalonRepository,
    private val admin: SalonAdminRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SalonSettingsUiState())
    val state: StateFlow<SalonSettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val salonId = authRepository.authState.filterIsInstance<AuthState.SignedIn>().first().user.salonId
                    ?: throw DataException(DataError.PERMISSION_DENIED)
                val salon = salonRepository.observeSalon(salonId).first() ?: throw DataException(DataError.NOT_FOUND)
                _state.value = SalonSettingsUiState(
                    loading = false, salon = salon, name = salon.name, address = salon.address, area = salon.area,
                    phone = salon.phone.orEmpty(), hours = HoursDraft.from(salon.openingHours),
                )
            } catch (e: DataException) {
                _state.update { it.copy(loading = false, error = e.error) }
            }
        }
    }

    fun update(change: (SalonSettingsUiState) -> SalonSettingsUiState) = _state.update(change)

    fun messageShown() = _state.update { it.copy(saved = false, error = null) }

    fun save() {
        val s = _state.value
        val salon = s.salon ?: return
        if (!s.isValid) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                admin.saveSalon(
                    salon.copy(
                        name = s.name.trim(), address = s.address.trim(), area = s.area.trim(),
                        phone = s.phone.trim().ifEmpty { null }, openingHours = s.hours!!.toWeeklyHours(),
                    ),
                )
                _state.update { it.copy(saving = false, saved = true) }
            } catch (e: DataException) {
                _state.update { it.copy(saving = false, error = e.error) }
            }
        }
    }
}

@Composable
fun SalonSettingsScreen(viewModel: SalonSettingsViewModel, state: SalonSettingsUiState, onSignOut: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val savedText = stringResource(R.string.saved)
    LaunchedEffect(state.saved) {
        if (state.saved) {
            viewModel.messageShown()
            snackbar.showSnackbar(savedText)
        }
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SalonTopBar(title = stringResource(R.string.salon_settings_title))
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screenHorizontal)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                SalonTextField(
                    state.name, { v -> viewModel.update { it.copy(name = v) } }, stringResource(R.string.salon_name_label),
                    error = if (state.showErrors && state.name.isBlank()) stringResource(R.string.field_error_required) else null,
                )
                SalonTextField(state.address, { v -> viewModel.update { it.copy(address = v.take(160)) } }, stringResource(R.string.salon_address_label))
                SalonTextField(state.area, { v -> viewModel.update { it.copy(area = v.take(60)) } }, stringResource(R.string.salon_area_label))
                SalonTextField(
                    state.phone, { v -> viewModel.update { it.copy(phone = v.filter { c -> c.isDigit() || c == '+' }.take(15)) } },
                    stringResource(R.string.salon_phone_label), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                state.hours?.let { hours ->
                    Text(stringResource(R.string.opening_hours_title), style = MaterialTheme.typography.titleMedium)
                    WeeklyHoursEditor(hours, { h -> viewModel.update { it.copy(hours = h) } })
                    if (state.showErrors && !hours.isValid) FormBanner(stringResource(R.string.hours_error), BannerTone.ERROR)
                }
                state.error?.let { FormBanner(it.message(), BannerTone.ERROR) }
                GradientButton(stringResource(R.string.action_save), viewModel::save, Modifier.fillMaxWidth(), loading = state.saving)
                GlassButton(stringResource(R.string.sign_out), onSignOut, Modifier.fillMaxWidth())
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}
