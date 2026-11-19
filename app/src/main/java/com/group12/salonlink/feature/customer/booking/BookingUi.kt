// Shared building blocks for the customer booking screens (Discovery → Barber → Services →
// Date & time → Confirmation). Kept in one file so all five screens look and speak alike.
package com.group12.salonlink.feature.customer.booking

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.group12.salonlink.R
import com.group12.salonlink.core.designsystem.component.SalonPhoto
import com.group12.salonlink.core.designsystem.theme.SalonTheme
import com.group12.salonlink.domain.booking.SlotProblem
import com.group12.salonlink.domain.model.BookingStatus
import com.group12.salonlink.domain.model.DataError
import com.group12.salonlink.domain.model.ServiceCategory
import com.group12.salonlink.domain.model.Staff
import java.util.Locale

/** Icon for a service category (Figma "Service selection" bubbles). */
@DrawableRes
fun ServiceCategory.iconRes(): Int = when (this) {
    ServiceCategory.HAIRCUT -> R.drawable.ic_scissors
    ServiceCategory.BEARD -> R.drawable.ic_beard
    ServiceCategory.COLOUR -> R.drawable.ic_palette
    ServiceCategory.TREATMENT -> R.drawable.ic_droplets
    ServiceCategory.OTHER -> R.drawable.ic_sparkles
}

/**
 * Bubble colours per category. Figma uses cool blue for cuts and warm peach for colour and
 * treatments, so a glance at the list tells "quick tidy" from "longer salon service".
 */
@Composable
fun ServiceCategory.bubbleColors(): Pair<Color, Color> {
    val colors = SalonTheme.colors
    return when (this) {
        ServiceCategory.HAIRCUT, ServiceCategory.BEARD -> colors.blueContainer to MaterialTheme.colorScheme.primary
        ServiceCategory.COLOUR, ServiceCategory.TREATMENT -> colors.peachContainer to colors.coral
        ServiceCategory.OTHER -> colors.lavenderContainer to colors.violet
    }
}

/** 4.9 → "4.9" (always one decimal, always a dot, whatever the phone's language). */
fun formatRating(rating: Double): String = String.format(Locale.ENGLISH, "%.1f", rating)

/** User-facing text for a [DataError] (NFR-10). */
@StringRes
fun DataError.messageRes(): Int = when (this) {
    DataError.NETWORK -> R.string.data_error_network
    DataError.PERMISSION_DENIED -> R.string.data_error_permission
    DataError.NOT_FOUND -> R.string.data_error_not_found
    DataError.SLOT_TAKEN -> R.string.data_error_slot_taken
    DataError.SLOT_UNAVAILABLE -> R.string.data_error_invalid
    DataError.NOT_ALLOWED -> R.string.data_error_not_allowed
    DataError.INVALID_REQUEST -> R.string.data_error_invalid
    DataError.UNKNOWN -> R.string.data_error_unknown
}

/** Why a chosen time can't be booked, in plain words ("Kasun is on a break then."). */
@Composable
fun slotProblemMessage(problem: SlotProblem, barberFirstName: String): String = when (problem) {
    SlotProblem.CLOSED -> stringResource(R.string.slot_problem_closed, barberFirstName)
    SlotProblem.OUTSIDE_HOURS -> stringResource(R.string.slot_problem_outside_hours)
    SlotProblem.BLOCKED -> stringResource(R.string.slot_problem_blocked, barberFirstName)
    SlotProblem.TAKEN -> stringResource(R.string.data_error_slot_taken)
    SlotProblem.IN_PAST -> stringResource(R.string.slot_problem_in_past)
    SlotProblem.TOO_LONG -> stringResource(R.string.slot_problem_too_long)
    SlotProblem.NOT_ALIGNED -> stringResource(R.string.data_error_invalid)
}

/** Label of the status chip on a booking ticket. */
@StringRes
fun BookingStatus.labelRes(): Int = when (this) {
    BookingStatus.BOOKED -> R.string.status_booked
    BookingStatus.CHECKED_IN -> R.string.status_checked_in
    BookingStatus.WAITING -> R.string.status_waiting
    BookingStatus.IN_SERVICE -> R.string.status_in_service
    BookingStatus.COMPLETED -> R.string.status_completed
    BookingStatus.CANCELLED -> R.string.status_cancelled
    BookingStatus.MISSED -> R.string.status_missed
}

/**
 * "Barber summary" row from the booking-flow frames: round photo, name, a detail line and
 * the star rating on the right.
 */
@Composable
fun BarberSummaryRow(staff: Staff, detail: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SalonPhoto(
            url = staff.photoUrl,
            name = staff.name,
            contentDescription = stringResource(R.string.cd_photo_of, staff.name),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.size(48.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(staff.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_star),
                contentDescription = null,
                tint = SalonTheme.colors.coral,
                modifier = Modifier.size(12.dp),
            )
            Text(formatRating(staff.rating), style = MaterialTheme.typography.labelLarge.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize))
        }
    }
}
