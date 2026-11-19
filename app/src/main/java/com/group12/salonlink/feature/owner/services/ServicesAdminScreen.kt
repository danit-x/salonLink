// Covers: US-09, FR-10 (services CRUD) · Design: new, in the app's visual language
package com.group12.salonlink.feature.owner.services

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.group12.salonlink.R
import com.group12.salonlink.core.designsystem.component.BannerTone
import com.group12.salonlink.core.designsystem.component.FormBanner
import com.group12.salonlink.core.designsystem.component.GlassSurface
import com.group12.salonlink.core.designsystem.component.GradientButton
import com.group12.salonlink.core.designsystem.component.IconBubble
import com.group12.salonlink.core.designsystem.component.SalonTag
import com.group12.salonlink.core.designsystem.component.SalonTextField
import com.group12.salonlink.core.designsystem.component.SalonTopBar
import com.group12.salonlink.core.designsystem.theme.CardShape
import com.group12.salonlink.core.designsystem.theme.SalonTheme
import com.group12.salonlink.core.designsystem.theme.Spacing
import com.group12.salonlink.core.util.Money
import com.group12.salonlink.domain.model.ServiceCategory
import com.group12.salonlink.feature.customer.booking.bubbleColors
import com.group12.salonlink.feature.customer.booking.iconRes
import com.group12.salonlink.feature.customer.booking.message
import com.group12.salonlink.feature.owner.LabeledSwitch

internal fun ServiceCategory.labelRes(): Int = when (this) {
    ServiceCategory.HAIRCUT -> R.string.category_haircut
    ServiceCategory.BEARD -> R.string.category_beard
    ServiceCategory.COLOUR -> R.string.category_colour
    ServiceCategory.TREATMENT -> R.string.category_treatment
    ServiceCategory.OTHER -> R.string.category_other
}

@Composable
fun ServicesAdminScreen(state: ServicesAdminUiState, onEvent: (ServicesAdminEvent) -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val savedText = stringResource(R.string.saved)
    LaunchedEffect(state.saved) {
        if (state.saved) {
            onEvent(ServicesAdminEvent.MessageShown)
            snackbar.showSnackbar(savedText)
        }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SalonTopBar(title = stringResource(R.string.services_admin_title)) }
            item {
                GradientButton(
                    stringResource(R.string.services_admin_add), { onEvent(ServicesAdminEvent.Add) },
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal),
                )
            }
            if (state.editor == null) state.error?.let { item { Box(Modifier.padding(horizontal = Spacing.screenHorizontal)) { FormBanner(it.message(), BannerTone.ERROR) } } }
            items(state.services, key = { it.id }) { service ->
                val (bg, tint) = service.category.bubbleColors()
                GlassSurface(
                    modifier = Modifier
                        .padding(horizontal = Spacing.screenHorizontal)
                        .fillMaxWidth()
                        .clip(CardShape)
                        .clickable { onEvent(ServicesAdminEvent.Edit(service)) },
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(service.category.iconRes(), bg, tint, size = 40.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(service.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${service.durationMin} min · ${Money.formatLkr(service.price)} · " +
                                    state.staff.filter { service.isOfferedBy(it.id) }.joinToString { it.firstName },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!service.active) {
                            SalonTag(stringResource(R.string.services_admin_inactive), SalonTheme.colors.peachContainer, SalonTheme.colors.coral)
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    state.editor?.let { ServiceEditorSheet(state, it, onEvent) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ServiceEditorSheet(state: ServicesAdminUiState, draft: ServiceDraft, onEvent: (ServicesAdminEvent) -> Unit) {
    val change: (ServiceDraft) -> Unit = { onEvent(ServicesAdminEvent.Change(it)) }
    ModalBottomSheet(onDismissRequest = { onEvent(ServicesAdminEvent.Close) }) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenHorizontal)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                stringResource(if (draft.id.isBlank()) R.string.service_editor_new else R.string.service_editor_edit),
                style = MaterialTheme.typography.titleLarge,
            )
            SalonTextField(
                draft.name, { change(draft.copy(name = it)) }, stringResource(R.string.service_name_label),
                error = if (state.showErrors && !draft.nameValid) stringResource(R.string.field_error_required) else null,
            )
            SalonTextField(draft.description, { change(draft.copy(description = it)) }, stringResource(R.string.service_description_label))
            Text(stringResource(R.string.service_category_label), style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ServiceCategory.entries.forEach { c ->
                    FilterChip(
                        selected = draft.category == c,
                        onClick = { change(draft.copy(category = c)) },
                        label = { Text(stringResource(c.labelRes())) },
                        leadingIcon = { androidx.compose.material3.Icon(painterResource(c.iconRes()), null) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                SalonTextField(
                    draft.duration, { change(draft.copy(duration = it.filter(Char::isDigit).take(3))) },
                    stringResource(R.string.service_duration_label), Modifier.weight(1f),
                    error = if (state.showErrors && draft.durationMin == null) stringResource(R.string.service_error_duration) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                SalonTextField(
                    draft.price, { change(draft.copy(price = it.filter(Char::isDigit).take(7))) },
                    stringResource(R.string.service_price_label), Modifier.weight(1f),
                    error = if (state.showErrors && draft.priceRupees == null) stringResource(R.string.service_error_price) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Text(stringResource(R.string.service_staff_label), style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.staff.forEach { s ->
                    val on = s.id in draft.staffIds
                    FilterChip(
                        selected = on,
                        onClick = { change(draft.copy(staffIds = if (on) draft.staffIds - s.id else draft.staffIds + s.id)) },
                        label = { Text(s.name) },
                    )
                }
            }
            LabeledSwitch(stringResource(R.string.service_active_label), draft.active) { change(draft.copy(active = it)) }
            state.error?.let { FormBanner(it.message(), BannerTone.ERROR) }
            GradientButton(stringResource(R.string.action_save), { onEvent(ServicesAdminEvent.Save) }, Modifier.fillMaxWidth(), loading = state.saving)
        }
    }
}
