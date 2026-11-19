// Covers: US-09, FR-10, P2 blocked time · Design: new, in the app's visual language
package com.group12.salonlink.feature.owner.team

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.group12.salonlink.R
import com.group12.salonlink.core.designsystem.component.BannerTone
import com.group12.salonlink.core.designsystem.component.FormBanner
import com.group12.salonlink.core.designsystem.component.GlassSurface
import com.group12.salonlink.core.designsystem.component.GradientButton
import com.group12.salonlink.core.designsystem.component.SalonTextField
import com.group12.salonlink.core.designsystem.component.SalonTopBar
import com.group12.salonlink.core.designsystem.theme.Spacing
import com.group12.salonlink.core.util.SalonTime
import com.group12.salonlink.feature.customer.booking.message
import com.group12.salonlink.feature.owner.DateButton
import com.group12.salonlink.feature.owner.LabeledSwitch
import com.group12.salonlink.feature.owner.TimeButton
import com.group12.salonlink.feature.owner.WeeklyHoursEditor

@Composable
fun StaffEditorScreen(state: StaffEditorUiState, onEvent: (StaffEditorEvent) -> Unit, onBack: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val text = when (val m = state.message) {
        StaffEditorMessage.Saved -> stringResource(R.string.saved)
        is StaffEditorMessage.InviteSent -> stringResource(R.string.invite_sent, m.email)
        is StaffEditorMessage.Failed -> m.error.message()
        null -> null
    }
    LaunchedEffect(state.message) {
        if (text != null) {
            onEvent(StaffEditorEvent.MessageShown)
            snackbar.showSnackbar(text)
        }
    }
    val d = state.draft
    val change: (StaffDraft) -> Unit = { onEvent(StaffEditorEvent.Change(it)) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SalonTopBar(
                title = if (state.staffId.isBlank()) stringResource(R.string.staff_editor_new) else stringResource(R.string.staff_editor_edit, d.name),
                onBack = onBack,
            )
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
                    d.name, { change(d.copy(name = it)) }, stringResource(R.string.staff_name_label),
                    error = if (state.showErrors && !d.nameValid) stringResource(R.string.field_error_required) else null,
                )
                SalonTextField(d.bio, { change(d.copy(bio = it)) }, stringResource(R.string.staff_bio_label))
                SalonTextField(d.specialties, { change(d.copy(specialties = it)) }, stringResource(R.string.staff_specialties_label))
                SalonTextField(
                    d.years, { change(d.copy(years = it.filter(Char::isDigit).take(2))) }, stringResource(R.string.staff_years_label),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                LabeledSwitch(stringResource(R.string.staff_active_label), d.active) { change(d.copy(active = it)) }
                Text(stringResource(R.string.hours_title), style = MaterialTheme.typography.titleMedium)
                WeeklyHoursEditor(d.hours, { change(d.copy(hours = it)) })
                if (state.showErrors && !d.hours.isValid) FormBanner(stringResource(R.string.hours_error), BannerTone.ERROR)
                GradientButton(stringResource(R.string.action_save), { onEvent(StaffEditorEvent.Save) }, Modifier.fillMaxWidth(), loading = state.busy)

                HorizontalDivider(Modifier.padding(vertical = Spacing.sm))
                if (state.staffId.isBlank()) {
                    FormBanner(stringResource(R.string.save_staff_first), BannerTone.INFO)
                } else {
                    BlocksSection(state, onEvent)
                    HorizontalDivider(Modifier.padding(vertical = Spacing.sm))
                    InviteSection(state, onEvent)
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun BlocksSection(state: StaffEditorUiState, onEvent: (StaffEditorEvent) -> Unit) {
    val b = state.blockDraft
    val change: (BlockDraft) -> Unit = { onEvent(StaffEditorEvent.ChangeBlock(it)) }
    Text(stringResource(R.string.blocks_title), style = MaterialTheme.typography.titleMedium)
    if (state.blocks.isEmpty()) {
        Text(stringResource(R.string.blocks_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    state.blocks.forEach { block ->
        GlassSurface(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${SalonTime.formatShortDate(block.start)} · ${SalonTime.formatRange(block.start, block.end)}", style = MaterialTheme.typography.titleSmall)
                    if (block.reason.isNotBlank()) Text(block.reason, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { onEvent(StaffEditorEvent.DeleteBlock(block.id)) }) {
                    Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        DateButton(b.date) { change(b.copy(date = it)) }
        TimeButton(b.from, stringResource(R.string.block_from)) { change(b.copy(from = it)) }
        Text("–")
        TimeButton(b.to, stringResource(R.string.block_to)) { change(b.copy(to = it)) }
    }
    SalonTextField(b.reason, { change(b.copy(reason = it)) }, stringResource(R.string.block_reason_label))
    OutlinedButton(onClick = { onEvent(StaffEditorEvent.AddBlock) }, enabled = b.isValid && !state.busy) {
        Text(stringResource(R.string.blocks_add))
    }
}

@Composable
private fun InviteSection(state: StaffEditorUiState, onEvent: (StaffEditorEvent) -> Unit) {
    Text(stringResource(R.string.invite_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.invite_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SalonTextField(
        state.inviteEmail, { onEvent(StaffEditorEvent.InviteEmailChanged(it)) }, stringResource(R.string.invite_email_label),
        error = if (state.inviteError) stringResource(R.string.field_error_invalid_email) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
    )
    OutlinedButton(onClick = { onEvent(StaffEditorEvent.SendInvite) }, enabled = !state.busy) { Text(stringResource(R.string.invite_send)) }
}
