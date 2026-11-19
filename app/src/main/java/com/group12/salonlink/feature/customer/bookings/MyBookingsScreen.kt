// Covers: US-05, FR-05 (my bookings, cancel) · Design: new, in the app's visual language
package com.group12.salonlink.feature.customer.bookings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.group12.salonlink.R
import com.group12.salonlink.core.designsystem.component.BannerTone
import com.group12.salonlink.core.designsystem.component.FormBanner
import com.group12.salonlink.core.designsystem.component.GlassSurface
import com.group12.salonlink.core.designsystem.component.GradientButton
import com.group12.salonlink.core.designsystem.component.SalonTag
import com.group12.salonlink.core.designsystem.component.SalonTopBar
import com.group12.salonlink.core.designsystem.theme.CardShape
import com.group12.salonlink.core.designsystem.theme.PillShape
import com.group12.salonlink.core.designsystem.theme.SalonLinkTheme
import com.group12.salonlink.core.designsystem.theme.SalonTheme
import com.group12.salonlink.core.designsystem.theme.Spacing
import com.group12.salonlink.core.util.Money
import com.group12.salonlink.core.util.SalonTime
import com.group12.salonlink.domain.booking.BookingPolicy
import com.group12.salonlink.domain.model.Booking
import com.group12.salonlink.domain.model.BookingStatus
import com.group12.salonlink.feature.customer.booking.labelRes
import com.group12.salonlink.feature.customer.booking.message
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthFormatter = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

/**
 * The Bookings tab: Upcoming / History, each booking a card with its status.
 * Tapping a card opens the booking ticket; "Cancel booking" asks first.
 */
@Composable
fun MyBookingsScreen(
    state: MyBookingsUiState,
    onEvent: (MyBookingsEvent) -> Unit,
    onOpenBooking: (Booking) -> Unit,
    onBookNow: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val cancelledText = stringResource(R.string.bookings_cancelled_message)
    val failure = (state.message as? BookingsMessage.Failed)?.error?.message()
    LaunchedEffect(state.message) {
        val text = when (state.message) {
            BookingsMessage.Cancelled -> cancelledText
            is BookingsMessage.Failed -> failure
            null -> null
        } ?: return@LaunchedEffect
        onEvent(MyBookingsEvent.MessageShown)
        snackbar.showSnackbar(text)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SalonTopBar(title = stringResource(R.string.bookings_title))
            TabSwitcher(state, onEvent)
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                state.error != null -> Box(Modifier.padding(Spacing.screenHorizontal)) {
                    FormBanner(state.error.message(), BannerTone.ERROR)
                }
                state.visible.isEmpty() -> EmptyState(state.tab, onBookNow)
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.visible, key = { it.booking.id }) { row ->
                        BookingCard(
                            row = row,
                            onOpen = { onOpenBooking(row.booking) },
                            onCancel = { onEvent(MyBookingsEvent.RequestCancel(row.booking.id)) },
                        )
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    state.cancelTarget?.let { target ->
        CancelBookingDialog(
            booking = target.booking,
            cancelling = state.cancelling,
            onConfirm = { onEvent(MyBookingsEvent.ConfirmCancel) },
            onDismiss = { onEvent(MyBookingsEvent.DismissCancel) },
        )
    }
}

/** Two pill tabs with counts: "Upcoming 2 · History 5". */
@Composable
private fun TabSwitcher(state: MyBookingsUiState, onEvent: (MyBookingsEvent) -> Unit) {
    val colors = SalonTheme.colors
    Row(
        modifier = Modifier
            .padding(horizontal = Spacing.screenHorizontal)
            .fillMaxWidth()
            .clip(PillShape)
            .background(colors.hairline.copy(alpha = 0.6f))
            .padding(4.dp)
            .selectableGroup(),
    ) {
        listOf(
            BookingsTab.UPCOMING to "${stringResource(R.string.bookings_tab_upcoming)} ${state.upcoming.size}",
            BookingsTab.HISTORY to "${stringResource(R.string.bookings_tab_history)} ${state.history.size}",
        ).forEach { (tab, label) ->
            val selected = state.tab == tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(PillShape)
                    .background(if (selected) Color.White else Color.Transparent)
                    .selectable(selected = selected, role = Role.Tab) { onEvent(MyBookingsEvent.SelectTab(tab)) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) colors.violet else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BookingCard(row: BookingRow, onOpen: () -> Unit, onCancel: () -> Unit) {
    val colors = SalonTheme.colors
    val b = row.booking
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .clickable(onClick = onOpen),
        contentPadding = 16.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                // Date block: "19 / NOV"
                Column(
                    modifier = Modifier
                        .size(54.dp)
                        .background(colors.selectedGradient, RoundedCornerShape(18.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val local = b.start.atZone(SalonTime.SALON_ZONE)
                    Text(local.dayOfMonth.toString(), style = MaterialTheme.typography.titleLarge, color = colors.violet)
                    Text(monthFormatter.format(local).uppercase(Locale.ENGLISH), style = MaterialTheme.typography.labelSmall, color = colors.violet)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(b.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = stringResource(R.string.bookings_with, b.staffName, SalonTime.formatRange(b.start, b.end)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    row.salon?.let {
                        Text(it.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusChip(b.status)
                    Text(Money.formatLkr(b.totalPrice), style = MaterialTheme.typography.titleSmall)
                }
            }
            when {
                row.canCancel -> TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.action_cancel_booking), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
                row.lateToCancel -> Text(
                    text = stringResource(R.string.cancel_late_note, BookingPolicy.FREE_CANCELLATION_HOURS.toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Status chip; active statuses blue, finished green-teal, cancelled / missed coral. */
@Composable
internal fun StatusChip(status: BookingStatus) {
    val colors = SalonTheme.colors
    val (bg, fg) = when (status) {
        BookingStatus.CANCELLED, BookingStatus.MISSED -> colors.peachContainer to colors.coral
        BookingStatus.COMPLETED -> colors.tealContainer to colors.teal
        else -> colors.blueContainer to MaterialTheme.colorScheme.primary
    }
    SalonTag(text = stringResource(status.labelRes()), containerColor = bg, contentColor = fg)
}

@Composable
private fun EmptyState(tab: BookingsTab, onBookNow: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.screenHorizontal)
            .padding(top = 40.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FormBanner(
            stringResource(if (tab == BookingsTab.UPCOMING) R.string.bookings_empty_upcoming else R.string.bookings_empty_history),
            BannerTone.INFO,
        )
        if (tab == BookingsTab.UPCOMING) {
            GradientButton(stringResource(R.string.bookings_book_now), onBookNow, Modifier.fillMaxWidth(), R.drawable.ic_calendar_days)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFDF8)
@Composable
private fun MyBookingsEmptyPreview() {
    SalonLinkTheme { MyBookingsScreen(MyBookingsUiState(loading = false), {}, {}, {}) }
}
