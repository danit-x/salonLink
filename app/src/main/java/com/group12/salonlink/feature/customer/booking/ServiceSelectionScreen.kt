// Covers: US-03, FR-03 (choose services) · Figma: "Service selection · SalonLink" (step 1 of 3)
package com.group12.salonlink.feature.customer.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.group12.salonlink.R
import com.group12.salonlink.core.designsystem.component.BannerTone
import com.group12.salonlink.core.designsystem.component.FormBanner
import com.group12.salonlink.core.designsystem.component.GradientButton
import com.group12.salonlink.core.designsystem.component.SalonTopBar
import com.group12.salonlink.core.designsystem.component.StepProgress
import com.group12.salonlink.core.designsystem.theme.CardShape
import com.group12.salonlink.core.designsystem.theme.PillShape
import com.group12.salonlink.core.designsystem.theme.SalonLinkTheme
import com.group12.salonlink.core.designsystem.theme.SalonTheme
import com.group12.salonlink.core.designsystem.theme.Spacing
import com.group12.salonlink.core.util.Money
import com.group12.salonlink.domain.model.DataError
import com.group12.salonlink.domain.model.SalonService
import com.group12.salonlink.domain.model.Staff

/** Labels of the three-step progress bar, shared by every step. */
@Composable
internal fun bookingSteps() = listOf(
    stringResource(R.string.step_services),
    stringResource(R.string.step_date_time),
    stringResource(R.string.step_confirm),
)

/**
 * Step 1: tick one or more services. The running total and duration update as you tap.
 * Stateless: everything comes from [state], every tap goes to [onEvent].
 */
@Composable
fun ServiceSelectionScreen(
    state: BookingFlowUiState,
    onEvent: (BookingFlowEvent) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    BookingFlowScaffold(
        title = stringResource(R.string.services_title),
        subtitle = stringResource(R.string.services_step),
        onBack = onBack,
        state = state,
        bottom = {
            HorizontalDivider(color = SalonTheme.colors.hairline)
            SelectionSummary(state)
            GradientButton(
                text = stringResource(R.string.services_continue),
                onClick = onContinue,
                enabled = state.canChooseTime,
                trailingIcon = R.drawable.ic_arrow_right,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) { staff ->
        StepProgress(steps = bookingSteps(), currentStep = 0)
        PageIntro(
            heading = stringResource(R.string.services_headline),
            body = stringResource(R.string.services_body),
        )
        val salon = state.salon
        if (salon != null) {
            BarberSummaryRow(staff = staff, detail = stringResource(R.string.salon_and_area, salon.name, salon.area))
        }
        if (state.services.isEmpty()) {
            FormBanner(stringResource(R.string.services_empty, staff.firstName), BannerTone.INFO)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.services.forEach { service ->
                ServiceOption(
                    service = service,
                    selected = service.id in state.selectedServiceIds,
                    onToggle = { onEvent(BookingFlowEvent.ToggleService(service.id)) },
                )
            }
        }
    }
}

/**
 * Frame shared by both booking steps: cream background, back bar, scrolling content and a
 * pinned bottom area (summary + main button). Shows loading and error states itself.
 */
@Composable
internal fun BookingFlowScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    state: BookingFlowUiState,
    bottom: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.(staff: Staff) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        SalonTopBar(title = title, subtitle = subtitle, onBack = onBack)
        val staff = state.staff
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            state.loadError != null || staff == null -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(Spacing.screenHorizontal),
                contentAlignment = Alignment.Center,
            ) {
                FormBanner(stringResource((state.loadError ?: DataError.NOT_FOUND).messageRes()), BannerTone.ERROR)
            }
            else -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.screenHorizontal)
                        .padding(top = 14.dp, bottom = Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xl),
                ) { content(staff) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.screenHorizontal)
                        .padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = bottom,
                )
            }
        }
    }
}

/** Big page heading + one line of copy ("Make it your own."). */
@Composable
internal fun PageIntro(heading: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(heading, style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One service card. Selected = lavender gradient, blue border and a gradient tick (Figma). */
@Composable
private fun ServiceOption(service: SalonService, selected: Boolean, onToggle: () -> Unit) {
    val colors = SalonTheme.colors
    val (bubbleBg, bubbleTint) = service.category.bubbleColors()
    val selectedText = stringResource(R.string.cd_selected)
    val notSelectedText = stringResource(R.string.cd_not_selected)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) Modifier.shadow(12.dp, CardShape, ambientColor = colors.shadowPrimary, spotColor = colors.shadowPrimary)
                else Modifier,
            )
            .clip(CardShape)
            .then(
                if (selected) Modifier.background(colors.selectedGradient).border(1.dp, colors.selectedBorder, CardShape)
                else Modifier.background(Color.White.copy(alpha = 0.56f)).border(1.dp, colors.hairline, CardShape),
            )
            // Whole card is one checkbox for TalkBack: "Haircut, 30 min, Rs. 1,500, Selected".
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { stateDescription = if (selected) selectedText else notSelectedText }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(PillShape)
                .background(if (selected) colors.glass else bubbleBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(service.category.iconRes()),
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else bubbleTint,
                modifier = Modifier.size(19.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(service.name, style = MaterialTheme.typography.titleSmall)
            if (service.description.isNotBlank()) {
                Text(
                    text = service.description,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.service_meta, service.durationMin, Money.formatLkr(service.price)),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            )
        }
        SelectionMark(selected)
    }
}

/** 24 dp circle: gradient with a tick when selected, empty ring otherwise. */
@Composable
private fun SelectionMark(selected: Boolean) {
    val colors = SalonTheme.colors
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(PillShape)
            .then(
                if (selected) Modifier.background(colors.brandGradient).border(1.dp, Color.White.copy(alpha = 0.5f), PillShape)
                else Modifier.background(Color.White.copy(alpha = 0.5f)).border(1.dp, colors.hairlineStrong, PillShape),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** "2 services selected · 50 minutes of you time ······ Rs. 2,300" */
@Composable
private fun SelectionSummary(state: BookingFlowUiState) {
    val count = state.selectedServices.size
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = if (count == 0) stringResource(R.string.services_none_selected)
                else pluralStringResource(R.plurals.services_selected, count, count),
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
            )
            Text(
                text = if (count == 0) stringResource(R.string.services_pick_hint)
                else stringResource(R.string.services_duration, state.durationMin),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = Money.formatLkr(state.totalPrice),
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp),
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun ServiceSelectionPreview() {
    SalonLinkTheme {
        ServiceSelectionScreen(
            state = BookingFlowPreviewData.servicesState,
            onEvent = {},
            onBack = {},
            onContinue = {},
        )
    }
}
