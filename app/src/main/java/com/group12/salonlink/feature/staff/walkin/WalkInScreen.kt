// Covers: US-08, FR-09 (add walk-in) · Design: new, in the app's visual language
package com.group12.salonlink.feature.staff.walkin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.group12.salonlink.R
import com.group12.salonlink.core.designsystem.component.BannerTone
import com.group12.salonlink.core.designsystem.component.FormBanner
import com.group12.salonlink.core.designsystem.component.GradientButton
import com.group12.salonlink.core.designsystem.component.SalonTextField
import com.group12.salonlink.core.designsystem.component.SalonTopBar
import com.group12.salonlink.core.designsystem.theme.Spacing
import com.group12.salonlink.core.util.Money
import com.group12.salonlink.core.util.SalonTime
import com.group12.salonlink.feature.auth.messageRes
import com.group12.salonlink.feature.customer.booking.message

/** Counter form: guest name, barber (owners only), services, then the next free start times today. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WalkInScreen(state: WalkInUiState, onEvent: (WalkInEvent) -> Unit, onBack: (() -> Unit)? = null) {
    val snackbar = remember { SnackbarHostState() }
    val addedText = state.added?.let { (name, start) -> stringResource(R.string.walkin_added, name, SalonTime.formatTime(start)) }
    LaunchedEffect(state.added) {
        if (addedText != null) {
            onEvent(WalkInEvent.MessageShown)
            snackbar.showSnackbar(addedText)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SalonTopBar(title = stringResource(R.string.walkin_title), onBack = onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screenHorizontal)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Text(stringResource(R.string.walkin_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SalonTextField(
                    value = state.name,
                    onValueChange = { onEvent(WalkInEvent.NameChanged(it)) },
                    label = stringResource(R.string.walkin_name_label),
                    placeholder = stringResource(R.string.walkin_name_placeholder),
                    error = state.nameError?.let { stringResource(it.messageRes()) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                if (state.canPickBarber) {
                    Label(R.string.walkin_barber_label)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.staff.forEach { member ->
                            FilterChip(
                                selected = member.id == state.staffId,
                                onClick = { onEvent(WalkInEvent.PickBarber(member.id)) },
                                label = { Text(member.name) },
                            )
                        }
                    }
                }
                Label(R.string.walkin_services_label)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.services.forEach { service ->
                        FilterChip(
                            selected = service.id in state.selectedServiceIds,
                            onClick = { onEvent(WalkInEvent.ToggleService(service.id)) },
                            label = { Text("${service.name} · ${service.durationMin} min · ${Money.formatLkr(service.price)}") },
                        )
                    }
                }
                Label(R.string.walkin_time_label)
                when {
                    state.selectedServiceIds.isEmpty() -> FormBanner(stringResource(R.string.walkin_pick_services), BannerTone.INFO)
                    state.times.isEmpty() -> FormBanner(stringResource(R.string.walkin_no_times), BannerTone.INFO)
                    else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.times.forEach { time ->
                            FilterChip(
                                selected = time == state.start,
                                onClick = { onEvent(WalkInEvent.PickTime(time)) },
                                label = { Text(SalonTime.formatTime(time)) },
                            )
                        }
                    }
                }
                state.error?.let { FormBanner(it.message(), BannerTone.ERROR) }
                GradientButton(
                    text = stringResource(R.string.walkin_submit),
                    onClick = { onEvent(WalkInEvent.Submit) },
                    enabled = state.canSubmit,
                    loading = state.submitting,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Label(res: Int) {
    Text(stringResource(res), style = MaterialTheme.typography.titleSmall)
}
