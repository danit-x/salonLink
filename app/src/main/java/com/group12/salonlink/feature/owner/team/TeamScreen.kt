// Covers: US-09, FR-10 (owner's team list) · Design: new, in the app's visual language
package com.group12.salonlink.feature.owner.team

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group12.salonlink.R
import com.group12.salonlink.core.designsystem.component.GlassSurface
import com.group12.salonlink.core.designsystem.component.GradientButton
import com.group12.salonlink.core.designsystem.component.SalonTopBar
import com.group12.salonlink.core.designsystem.theme.CardShape
import com.group12.salonlink.core.designsystem.theme.Spacing
import com.group12.salonlink.domain.model.AuthState
import com.group12.salonlink.domain.model.Staff
import com.group12.salonlink.domain.repository.AuthRepository
import com.group12.salonlink.domain.repository.SalonRepository
import com.group12.salonlink.feature.customer.booking.BarberSummaryRow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** Everyone on the team, including people who currently don't take bookings. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TeamViewModel @Inject constructor(
    authRepository: AuthRepository,
    salonRepository: SalonRepository,
) : ViewModel() {
    val staff: StateFlow<List<Staff>> = authRepository.authState.filterIsInstance<AuthState.SignedIn>()
        .flatMapLatest { s -> s.user.salonId?.let { salonRepository.observeStaff(it, includeInactive = true) } ?: flowOf(emptyList()) }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun TeamScreen(staff: List<Staff>, onOpen: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SalonTopBar(title = stringResource(R.string.team_title)) }
        item {
            GradientButton(
                stringResource(R.string.team_add), { onOpen("") },
                Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal),
            )
        }
        items(staff, key = { it.id }) { member ->
            GlassSurface(
                Modifier
                    .padding(horizontal = Spacing.screenHorizontal)
                    .fillMaxWidth()
                    .clip(CardShape)
                    .clickable { onOpen(member.id) },
            ) {
                BarberSummaryRow(
                    staff = member,
                    detail = if (member.active) member.specialties.joinToString(" · ") else stringResource(R.string.team_inactive),
                )
            }
        }
    }
}
