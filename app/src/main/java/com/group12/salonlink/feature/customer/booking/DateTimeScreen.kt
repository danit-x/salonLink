// Covers: US-03, US-04, FR-03, FR-04, T02, T03 · Figma: "Date & time · SalonLink" (step 2 of 3)
package com.group12.salonlink.feature.customer.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.group12.salonlink.R
import com.group12.salonlink.core.designsystem.component.BannerTone
import com.group12.salonlink.core.designsystem.component.FormBanner
import com.group12.salonlink.core.designsystem.component.GradientButton
import com.group12.salonlink.core.designsystem.component.StepProgress
import com.group12.salonlink.core.designsystem.theme.PillShape
import com.group12.salonlink.core.designsystem.theme.SalonLinkTheme
import com.group12.salonlink.core.designsystem.theme.SalonTheme
import com.group12.salonlink.core.util.Money
import com.group12.salonlink.core.util.SalonTime
import com.group12.salonlink.domain.booking.SlotOption
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Step 2: pick a day on the calendar, then one of the free times. "Confirm appointment"
 * creates the booking through the booking engine (transaction + slot locks, PLAN.md §5).
 *
 * The time grid is live: if another customer books 5:30 pm while this screen is open, the
 * button turns struck-through by itself, and a chosen time that is taken is un-selected.
 */
@Composable
fun DateTimeScreen(
    state: BookingFlowUiState,
    onEvent: (BookingFlowEvent) -> Unit,
    onBack: () -> Unit,
) {
    BookingFlowScaffold(
        title = stringResource(R.string.datetime_title),
        subtitle = stringResource(R.string.datetime_step),
        onBack = onBack,
        state = state,
        bottom = { ConfirmArea(state, onEvent) },
    ) { staff ->
        val salon = state.salon ?: return@BookingFlowScaffold
        StepProgress(steps = bookingSteps(), currentStep = 1)
        PageIntro(
            heading = stringResource(R.string.datetime_headline),
            body = stringResource(R.string.datetime_body, staff.name, salon.name),
        )
        BarberSummaryRow(
            staff = staff,
            detail = pluralStringResource(
                R.plurals.datetime_barber_summary,
                state.selectedServices.size,
                stringResource(R.string.salon_with_area, salon.name, salon.area),
                state.selectedServices.size,
                state.durationMin,
                Money.formatLkr(state.totalPrice),
            ),
        )
        MonthCalendar(state = state, salonName = salon.name, onEvent = onEvent)
        TimesSection(state = state, barberFirstName = staff.firstName, onEvent = onEvent)
    }
}

// ---------------------------------------------------------------------------------------
// Calendar
// ---------------------------------------------------------------------------------------

/** Month grid, Monday first. Days the barber can't take bookings are greyed out and inert. */
@Composable
private fun MonthCalendar(state: BookingFlowUiState, salonName: String, onEvent: (BookingFlowEvent) -> Unit) {
    val month = state.visibleMonth
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.datetime_month, SalonTime.formatMonth(month), salonName),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            MonthArrow(R.drawable.ic_chevron_left, R.string.cd_previous_month, state.canGoToPreviousMonth) {
                onEvent(BookingFlowEvent.PreviousMonth)
            }
            MonthArrow(R.drawable.ic_chevron_right, R.string.cd_next_month, state.canGoToNextMonth) {
                onEvent(BookingFlowEvent.NextMonth)
            }
        }
        // Weekday initials: M T W T F S S
        Row {
            DayOfWeek.entries.forEach { day ->
                Text(
                    text = day.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // Blank cells before the 1st so that day 1 sits under its weekday.
        val leadingBlanks = month.atDay(1).dayOfWeek.value - 1
        val cells: List<LocalDate?> = List(leadingBlanks) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            cells.chunked(7).forEach { week ->
                Row {
                    week.forEach { date ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (date != null) DayCell(date, state, onEvent)
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun MonthArrow(icon: Int, label: Int, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp)) {
        Icon(
            painter = painterResource(icon),
            contentDescription = stringResource(label),
            tint = if (enabled) MaterialTheme.colorScheme.primary else SalonTheme.colors.hairlineStrong,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** One day: gradient pill when selected, lavender pill for today, faded when not bookable. */
@Composable
private fun DayCell(date: LocalDate, state: BookingFlowUiState, onEvent: (BookingFlowEvent) -> Unit) {
    val colors = SalonTheme.colors
    val selected = date == state.selectedDate
    val bookable = state.isBookableDay(date)
    val isToday = date == state.today
    val description = SalonTime.formatDayHeading(date)
    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 32.dp)
            .then(
                if (selected) Modifier.shadow(8.dp, PillShape, ambientColor = colors.shadowPrimary, spotColor = colors.shadowPrimary)
                else Modifier,
            )
            .clip(PillShape)
            .then(
                when {
                    selected -> Modifier.background(colors.brandGradient)
                    isToday -> Modifier.background(colors.navIndicator)
                    else -> Modifier
                },
            )
            .selectable(selected = selected, enabled = bookable, role = Role.Button) {
                onEvent(BookingFlowEvent.SelectDate(date))
            }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = when {
                selected -> Color.White
                bookable -> MaterialTheme.colorScheme.onSurface
                else -> colors.hairlineStrong
            },
        )
    }
}

// ---------------------------------------------------------------------------------------
// Times
// ---------------------------------------------------------------------------------------

@Composable
private fun TimesSection(state: BookingFlowUiState, barberFirstName: String, onEvent: (BookingFlowEvent) -> Unit) {
    val date = state.selectedDate
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (date == null) {
            FormBanner(stringResource(R.string.datetime_pick_date), BannerTone.INFO)
            return@Column
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = SalonTime.formatDayHeading(date),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.datetime_zone),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (val slots = state.slots) {
            SlotsState.Loading -> Box(Modifier.fillMaxWidth().height(90.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.primary)
            }
            is SlotsState.Failed -> FormBanner(stringResource(slots.error.messageRes()), BannerTone.ERROR)
            is SlotsState.Ready -> when {
                !state.isBookableDay(date) ->
                    FormBanner(stringResource(R.string.datetime_day_off, barberFirstName), BannerTone.INFO)
                slots.options.none { it.isAvailable } ->
                    FormBanner(stringResource(R.string.datetime_no_slots), BannerTone.INFO)
                else -> TimeGrid(slots.options, state.selectedStart, onEvent)
            }
        }
        state.selectedEnd?.let { end ->
            Text(
                text = stringResource(R.string.datetime_ends_at, SalonTime.formatTime(end)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Phase 7: nothing suits? Wait for a cancellation on this day.
        if (state.slots is SlotsState.Ready && state.isBookableDay(date)) {
            WaitlistPrompt(state, date, onEvent)
        }
    }
}

@Composable
private fun WaitlistPrompt(state: BookingFlowUiState, date: LocalDate, onEvent: (BookingFlowEvent) -> Unit) {
    if (state.waitlistJoinedDate == date) {
        FormBanner(stringResource(R.string.waitlist_joined, SalonTime.formatDayHeading(date)), BannerTone.INFO)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        androidx.compose.material3.TextButton(
            onClick = { onEvent(BookingFlowEvent.JoinWaitlist) },
            enabled = state.canChooseTime && !state.joiningWaitlist,
        ) { Text(stringResource(R.string.waitlist_join)) }
        Text(
            stringResource(R.string.waitlist_join_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/** Three time buttons per row, as in Figma. Taken times stay visible but struck through. */
@Composable
private fun TimeGrid(options: List<SlotOption>, selectedStart: Instant?, onEvent: (BookingFlowEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { option ->
                    TimeChip(
                        option = option,
                        selected = option.start == selectedStart,
                        onClick = { onEvent(BookingFlowEvent.SelectTime(option.start)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TimeChip(option: SlotOption, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SalonTheme.colors
    val label = SalonTime.formatTime(option.start)
    val unavailableLabel = stringResource(R.string.cd_time_unavailable, label)
    Box(
        modifier = modifier
            .height(41.dp)
            .then(
                if (selected) Modifier.shadow(10.dp, PillShape, ambientColor = colors.shadowPrimary, spotColor = colors.shadowPrimary)
                else Modifier,
            )
            .clip(PillShape)
            .then(
                when {
                    selected -> Modifier.background(colors.brandGradient)
                    option.isAvailable -> Modifier.background(Color.White).border(1.dp, colors.hairline, PillShape)
                    else -> Modifier.border(1.dp, colors.hairline, PillShape)
                },
            )
            .selectable(selected = selected, enabled = option.isAvailable, role = Role.RadioButton, onClick = onClick)
            .then(if (option.isAvailable) Modifier else Modifier.semantics { contentDescription = unavailableLabel }),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                textDecoration = if (option.isAvailable) null else TextDecoration.LineThrough,
            ),
            color = when {
                selected -> Color.White
                option.isAvailable -> MaterialTheme.colorScheme.onSurface
                else -> colors.hairlineStrong
            },
        )
    }
}

// ---------------------------------------------------------------------------------------
// Bottom: summary + confirm
// ---------------------------------------------------------------------------------------

@Composable
private fun ConfirmArea(state: BookingFlowUiState, onEvent: (BookingFlowEvent) -> Unit) {
    val firstName = state.staff?.firstName.orEmpty()
    when (val error = state.submitError) {
        is SubmitError.Unavailable -> FormBanner(slotProblemMessage(error.problem, firstName), BannerTone.ERROR)
        is SubmitError.Failed -> FormBanner(stringResource(error.error.messageRes()), BannerTone.ERROR)
        null -> Unit
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val start = state.selectedStart
        Text(
            text = if (start == null) stringResource(R.string.datetime_pick_time)
            else stringResource(R.string.datetime_selected, SalonTime.formatShortDate(start), SalonTime.formatTime(start)),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
            color = if (start == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        Text(Money.formatLkr(state.totalPrice), style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp))
    }
    GradientButton(
        text = stringResource(R.string.datetime_confirm),
        onClick = { onEvent(BookingFlowEvent.Confirm) },
        enabled = state.canConfirm,
        loading = state.submitting,
        trailingIcon = R.drawable.ic_check,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = stringResource(R.string.datetime_payment_note),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
    )
}

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun DateTimePreview() {
    SalonLinkTheme {
        DateTimeScreen(state = BookingFlowPreviewData.dateTimeState, onEvent = {}, onBack = {})
    }
}
