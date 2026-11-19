// Covers: US-04, FR-04, T02 (booking confirmed) · Figma: "Booking confirmation · SalonLink" (step 3 of 3)
package com.group12.salonlink.feature.customer.booking.confirmation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.group12.salonlink.R
import com.group12.salonlink.core.designsystem.component.BannerTone
import com.group12.salonlink.core.designsystem.component.FormBanner
import com.group12.salonlink.core.designsystem.component.GlassButton
import com.group12.salonlink.core.designsystem.component.GlassIconButton
import com.group12.salonlink.core.designsystem.component.GradientButton
import com.group12.salonlink.core.designsystem.component.SalonTag
import com.group12.salonlink.core.designsystem.component.SalonTopBar
import com.group12.salonlink.core.designsystem.theme.CardShape
import com.group12.salonlink.core.designsystem.theme.SalonLinkTheme
import com.group12.salonlink.core.designsystem.theme.SalonTheme
import com.group12.salonlink.core.designsystem.theme.Spacing
import com.group12.salonlink.core.util.Money
import com.group12.salonlink.core.util.SalonTime
import com.group12.salonlink.domain.booking.BookingPolicy
import com.group12.salonlink.domain.model.BookedService
import com.group12.salonlink.domain.model.Booking
import com.group12.salonlink.domain.model.BookingStatus
import com.group12.salonlink.domain.model.BookingType
import com.group12.salonlink.domain.model.DataError
import com.group12.salonlink.domain.model.Salon
import com.group12.salonlink.feature.customer.booking.BookingFlowPreviewData
import com.group12.salonlink.feature.customer.booking.labelRes
import com.group12.salonlink.feature.customer.booking.message
import com.group12.salonlink.feature.customer.booking.messageRes
import com.group12.salonlink.feature.customer.bookings.CancelBookingDialog
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/**
 * The ticket after booking (or when opening a booking later).
 *
 * @param onViewBookings opens the Bookings tab.
 */
@Composable
fun BookingConfirmationScreen(
    state: BookingConfirmationUiState,
    onBack: () -> Unit,
    onViewBookings: () -> Unit,
    onEvent: (BookingConfirmationEvent) -> Unit = {},
) {
    val context = LocalContext.current
    val booking = state.booking
    val salon = state.salon
    // Celebrate only a fresh, still-valid booking. An old or cancelled one is just "Your booking".
    val celebrate = state.justBooked && booking?.status == BookingStatus.BOOKED

    // Prepared here (composable scope) because stringResource can't be called inside onClick.
    val shareText = if (booking != null && salon != null) {
        stringResource(
            R.string.confirmation_share_text,
            booking.title, booking.staffName, salon.name,
            SalonTime.formatShortDate(booking.start), SalonTime.formatTime(booking.start),
        )
    } else null
    val shareTitle = stringResource(R.string.cd_share_booking)
    val calendarTitle = if (booking != null && salon != null) {
        stringResource(R.string.calendar_event_title, booking.title, salon.name)
    } else null
    val noAppMessage = stringResource(R.string.no_app_for_action)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        SalonTopBar(
            title = stringResource(if (celebrate) R.string.confirmation_title else R.string.confirmation_title_existing),
            onBack = onBack,
            actions = {
                if (shareText != null) {
                    GlassIconButton(
                        icon = R.drawable.ic_share,
                        contentDescription = shareTitle,
                        onClick = { context.shareText(shareText, shareTitle, noAppMessage) },
                    )
                }
            },
        )
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            booking == null || salon == null -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(Spacing.screenHorizontal),
                contentAlignment = Alignment.Center,
            ) {
                FormBanner(stringResource((state.error ?: DataError.NOT_FOUND).messageRes()), BannerTone.ERROR)
            }
            else -> Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screenHorizontal)
                    .padding(top = 8.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (celebrate) {
                    Celebration()
                    ConfirmationMessage(booking)
                }
                AppointmentTicket(booking, salon)
                GradientButton(
                    text = stringResource(R.string.confirmation_add_calendar),
                    onClick = { context.addToCalendar(booking, salon, calendarTitle.orEmpty(), noAppMessage) },
                    trailingIcon = R.drawable.ic_calendar_plus,
                    modifier = Modifier.fillMaxWidth(),
                )
                GlassButton(
                    text = stringResource(R.string.confirmation_view_booking),
                    onClick = onViewBookings,
                    trailingIcon = R.drawable.ic_arrow_up_right,
                    containerColor = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                )
                PolicyNote(booking)
                state.cancelError?.let { FormBanner(it.message(), BannerTone.ERROR) }
                when {
                    state.canCancel -> androidx.compose.material3.TextButton(onClick = { onEvent(BookingConfirmationEvent.RequestCancel) }) {
                        Text(stringResource(R.string.action_cancel_booking), color = MaterialTheme.colorScheme.error)
                    }
                    state.lateToCancel -> Text(
                        text = stringResource(R.string.cancel_late_note, BookingPolicy.FREE_CANCELLATION_HOURS.toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    if (state.showCancelDialog && booking != null) {
        CancelBookingDialog(
            booking = booking,
            cancelling = state.cancelling,
            onConfirm = { onEvent(BookingConfirmationEvent.ConfirmCancel) },
            onDismiss = { onEvent(BookingConfirmationEvent.DismissCancel) },
        )
    }
}

/** Gradient seal with a tick inside a soft halo, plus three floating bubbles (Figma "Celebration"). */
@Composable
private fun Celebration() {
    val colors = SalonTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(148.dp)
                .background(colors.selectedGradient, CircleShape),
        )
        Bubble(colors.peachContainer, 27.dp, Modifier.offset(x = (-105).dp, y = (-5).dp))
        Bubble(colors.tealContainer, 12.dp, Modifier.offset(x = 95.dp, y = (-35).dp))
        Bubble(colors.lavenderContainer, 17.dp, Modifier.offset(x = 110.dp, y = 50.dp))
        Box(
            modifier = Modifier
                .size(100.dp)
                .shadow(16.dp, CircleShape, ambientColor = colors.shadowPrimary, spotColor = colors.shadowPrimary)
                .background(colors.brandGradient, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

@Composable
private fun Bubble(color: Color, size: Dp, modifier: Modifier) {
    Box(modifier.size(size).background(color, CircleShape))
}

/** "IT'S A GOOD HAIR DATE · You're in, Kavindu. · Kasun's looking forward to seeing you." */
@Composable
private fun ConfirmationMessage(booking: Booking) {
    val customerFirstName = booking.customerName.trim().substringBefore(' ')
    val barberFirstName = booking.staffName.trim().substringBefore(' ')
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.confirmation_eyebrow),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
            color = SalonTheme.colors.violet,
        )
        Text(
            text = stringResource(R.string.confirmation_headline, customerFirstName),
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.confirmation_body, barberFirstName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** White ticket: status + reference, when / what / where, and the amount to pay at the salon. */
@Composable
private fun AppointmentTicket(booking: Booking, salon: Salon) {
    val colors = SalonTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, CardShape, ambientColor = colors.shadowSoft, spotColor = colors.shadowSoft)
            .clip(CardShape)
            .background(Color.White)
            .padding(21.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val active = booking.status.holdsSlot || booking.status == BookingStatus.COMPLETED
            SalonTag(
                text = stringResource(booking.status.labelRes()),
                containerColor = if (active) colors.blueContainer else colors.peachContainer,
                contentColor = if (active) MaterialTheme.colorScheme.primary else colors.coral,
                icon = if (active) R.drawable.ic_check else null,
            )
            Text(
                text = stringResource(R.string.confirmation_reference, referenceOf(booking.id)),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TicketDetail(
                icon = R.drawable.ic_calendar_days,
                label = SalonTime.formatLongDate(booking.start),
                meta = SalonTime.formatRange(booking.start, booking.end),
            )
            TicketDetail(
                icon = R.drawable.ic_scissors,
                label = booking.title,
                meta = stringResource(R.string.confirmation_service_meta, booking.durationMin, booking.staffName),
            )
            TicketDetail(
                icon = R.drawable.ic_map_pin,
                label = salon.name,
                meta = listOfNotNull(salon.address, salon.phone).joinToString("\n"),
            )
        }
        HorizontalDivider(color = colors.hairline)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.confirmation_pay_label), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(R.string.confirmation_pay_methods),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(Money.formatLkr(booking.totalPrice), style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp))
        }
    }
}

@Composable
private fun TicketDetail(@DrawableRes icon: Int, label: String, meta: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(SalonTheme.colors.blueContainer, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "Plans change. Cancel free until Wed, 7 Oct at 5:30 pm…" (or just where to find it, if too late). */
@Composable
private fun PolicyNote(booking: Booking) {
    val freeUntil = booking.start.minusSeconds(BookingPolicy.FREE_CANCELLATION_HOURS * 3600)
    val text = if (booking.status == BookingStatus.BOOKED && freeUntil > Instant.now()) {
        stringResource(
            R.string.confirmation_policy,
            stringResource(R.string.datetime_selected, SalonTime.formatShortDate(freeUntil), SalonTime.formatTime(freeUntil)),
        )
    } else {
        stringResource(R.string.confirmation_policy_late)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/**
 * Short, readable reference for the counter: first 6 characters of the booking id, upper case
 * ("SL-3F9A1C"). The full random id stays the real key.
 */
internal fun referenceOf(bookingId: String): String =
    bookingId.filter { it.isLetterOrDigit() }.take(6).uppercase(Locale.ENGLISH)

/**
 * Opens the phone's calendar app with the appointment filled in.
 * WHY an Intent (not writing to the calendar ourselves): no calendar permission is needed, and
 * the user sees and approves the event in their own calendar app.
 */
private fun Context.addToCalendar(booking: Booking, salon: Salon, title: String, noAppMessage: String) {
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, booking.start.toEpochMilli())
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, booking.end.toEpochMilli())
        .putExtra(CalendarContract.Events.TITLE, title)
        .putExtra(CalendarContract.Events.EVENT_LOCATION, "${salon.name}, ${salon.address}")
    startSafely(intent, noAppMessage)
}

/** Android's share sheet (WhatsApp, SMS, email…). */
private fun Context.shareText(text: String, chooserTitle: String, noAppMessage: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    startSafely(Intent.createChooser(send, chooserTitle), noAppMessage)
}

/** Some phones (or emulators) have no calendar app: tell the user instead of crashing. */
private fun Context.startSafely(intent: Intent, noAppMessage: String) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, noAppMessage, Toast.LENGTH_SHORT).show()
    }
}

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun BookingConfirmationPreview() {
    val start = SalonTime.toInstant(LocalDate.of(2026, 10, 8), LocalTime.of(17, 30))
    val booking = Booking(
        id = "3f9a1c7e", salonId = "aura", customerId = "u1", customerName = "Kavindu Perera",
        staffId = "kasun", staffName = "Kasun Perera",
        services = listOf(BookedService("haircut", "Haircut", 30, 1500), BookedService("beard-trim", "Beard Trim", 20, 800)),
        start = start, end = start.plusSeconds(50 * 60), totalPrice = 2300,
        status = BookingStatus.BOOKED, type = BookingType.APPOINTMENT,
    )
    SalonLinkTheme {
        BookingConfirmationScreen(
            state = BookingConfirmationUiState(loading = false, justBooked = true, booking = booking, salon = BookingFlowPreviewData.salon),
            onBack = {},
            onViewBookings = {},
        )
    }
}
