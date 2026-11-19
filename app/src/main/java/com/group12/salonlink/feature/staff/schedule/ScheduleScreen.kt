// Covers: US-07, US-09, FR-06, FR-08, FR-10 · Design: new, in the app's visual language
package com.group12.salonlink.feature.staff.schedule

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import com.group12.salonlink.R
import com.group12.salonlink.core.designsystem.component.BannerTone
import com.group12.salonlink.core.designsystem.component.FormBanner
import com.group12.salonlink.core.designsystem.component.GlassIconButton
import com.group12.salonlink.core.designsystem.component.GlassSurface
import com.group12.salonlink.core.designsystem.component.GradientButton
import com.group12.salonlink.core.designsystem.component.SalonTag
import com.group12.salonlink.core.designsystem.component.SalonTopBar
import com.group12.salonlink.core.designsystem.theme.SalonTheme
import com.group12.salonlink.core.designsystem.theme.Spacing
import com.group12.salonlink.core.util.Money
import com.group12.salonlink.core.util.SalonTime
import com.group12.salonlink.domain.model.Booking
import com.group12.salonlink.domain.model.BookingStatus
import com.group12.salonlink.domain.model.BookingType
import com.group12.salonlink.feature.customer.booking.labelRes
import com.group12.salonlink.feature.customer.booking.message
import com.group12.salonlink.feature.customer.bookings.StatusChip
import java.time.Instant

/** Button label for moving a booking to [status]. */
private fun actionLabel(status: BookingStatus): Int = when (status) {
    BookingStatus.CHECKED_IN -> R.string.action_check_in
    BookingStatus.IN_SERVICE -> R.string.action_start
    BookingStatus.COMPLETED -> R.string.action_complete
    BookingStatus.MISSED -> R.string.action_no_show
    else -> R.string.action_cancel
}

/**
 * A salon day: date switcher, (owner) numbers + barber filter, then one card per booking with
 * the next status buttons. Everything updates live as other staff change bookings.
 */
@Composable
fun ScheduleScreen(state: ScheduleUiState, onEvent: (ScheduleEvent) -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val message = when (val m = state.message) {
        is ScheduleMessage.Updated -> stringResource(R.string.status_updated, m.customerName, stringResource(m.status.labelRes()))
        is ScheduleMessage.Failed -> m.error.message()
        null -> null
    }
    LaunchedEffect(state.message) {
        if (message != null) {
            onEvent(ScheduleEvent.MessageShown)
            snackbar.showSnackbar(message)
        }
    }
    val now = Instant.now()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SalonTopBar(
                    title = stringResource(if (state.isOwner) R.string.schedule_title_owner else R.string.schedule_title_staff),
                    subtitle = SalonTime.formatDayHeading(state.date),
                    actions = {
                        GlassIconButton(R.drawable.ic_chevron_left, stringResource(R.string.cd_previous_day), { onEvent(ScheduleEvent.PreviousDay) })
                        GlassIconButton(R.drawable.ic_chevron_right, stringResource(R.string.cd_next_day), { onEvent(ScheduleEvent.NextDay) })
                    },
                )
            }
            if (!state.isToday) {
                item {
                    TextButton(onClick = { onEvent(ScheduleEvent.Today) }, modifier = Modifier.padding(horizontal = Spacing.lg)) {
                        Text(stringResource(R.string.schedule_back_to_today))
                    }
                }
            }
            if (state.isOwner) {
                item { StatsRow(state.stats) }
                item { StaffFilter(state, onEvent) }
            }
            when {
                state.loading -> item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                state.error != null -> item {
                    Box(Modifier.padding(horizontal = Spacing.screenHorizontal)) { FormBanner(state.error.message(), BannerTone.ERROR) }
                }
                state.bookings.isEmpty() -> item {
                    Box(Modifier.padding(horizontal = Spacing.screenHorizontal)) {
                        FormBanner(stringResource(R.string.schedule_empty), BannerTone.INFO)
                    }
                }
                else -> items(state.bookings, key = { it.id }) { booking ->
                    ScheduleCard(
                        booking = booking,
                        showBarber = state.isOwner && state.staffFilter == null,
                        busy = booking.id in state.busyIds,
                        actions = booking.staffActions(now),
                        onAction = { onEvent(ScheduleEvent.ChangeStatus(booking, it)) },
                        modifier = Modifier.padding(horizontal = Spacing.screenHorizontal),
                    )
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    state.pending?.let { pending ->
        AlertDialog(
            onDismissRequest = { onEvent(ScheduleEvent.DismissPending) },
            title = {
                Text(
                    stringResource(
                        if (pending.next == BookingStatus.MISSED) R.string.confirm_status_title_missed
                        else R.string.confirm_status_title_cancelled,
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        R.string.confirm_status_text,
                        pending.booking.customerName,
                        SalonTime.formatRange(pending.booking.start, pending.booking.end),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { onEvent(ScheduleEvent.ConfirmPending) }) {
                    Text(stringResource(R.string.action_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { onEvent(ScheduleEvent.DismissPending) }) { Text(stringResource(R.string.action_back)) } },
        )
    }
}

@Composable
private fun StatsRow(stats: DayStats) {
    val colors = SalonTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatCard(stringResource(R.string.stat_bookings), stats.bookings.toString(), null)
        StatCard(stringResource(R.string.stat_completed), stats.completed.toString(), null)
        StatCard(stringResource(R.string.stat_walk_ins), stats.walkIns.toString(), null)
        StatCard(
            stringResource(R.string.stat_revenue),
            Money.formatLkr(stats.revenue),
            stringResource(R.string.stat_revenue_expected, Money.formatLkr(stats.expectedRevenue)),
            highlight = colors.violet,
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, footnote: String?, highlight: androidx.compose.ui.graphics.Color? = null) {
    GlassSurface(contentPadding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, color = highlight ?: MaterialTheme.colorScheme.onSurface)
            footnote?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun StaffFilter(state: ScheduleUiState, onEvent: (ScheduleEvent) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = state.staffFilter == null,
            onClick = { onEvent(ScheduleEvent.FilterStaff(null)) },
            label = { Text(stringResource(R.string.schedule_all_staff)) },
        )
        state.staff.forEach { member ->
            FilterChip(
                selected = state.staffFilter == member.id,
                onClick = { onEvent(ScheduleEvent.FilterStaff(member.id)) },
                label = { Text(member.firstName) },
            )
        }
    }
}

/** One booking: time, customer, services, status, note and the next-step buttons. */
@Composable
internal fun ScheduleCard(
    booking: Booking,
    showBarber: Boolean,
    busy: Boolean,
    actions: List<BookingStatus>,
    onAction: (BookingStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SalonTheme.colors
    GlassSurface(modifier = modifier.fillMaxWidth(), contentPadding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(SalonTime.formatRange(booking.start, booking.end), style = MaterialTheme.typography.titleSmall, color = colors.violet)
                if (booking.type == BookingType.WALK_IN) {
                    SalonTag(stringResource(R.string.schedule_walk_in_label), colors.peachContainer, colors.coral)
                }
                Box(Modifier.weight(1f))
                StatusChip(booking.status)
            }
            Text(booking.customerName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = listOfNotNull(booking.title, booking.staffName.takeIf { showBarber }, Money.formatLkr(booking.totalPrice)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            booking.note?.let {
                Text(stringResource(R.string.schedule_note, it), style = MaterialTheme.typography.bodySmall, color = colors.teal)
            }
            if (actions.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        val main = actions.first().takeIf { it != BookingStatus.MISSED && it != BookingStatus.CANCELLED }
                        if (main != null) {
                            GradientButton(stringResource(actionLabel(main)), { onAction(main) }, Modifier.weight(1f))
                        }
                        actions.filter { it != main }.forEach { secondary ->
                            OutlinedButton(onClick = { onAction(secondary) }) { Text(stringResource(actionLabel(secondary))) }
                        }
                    }
                }
            }
        }
    }
}
