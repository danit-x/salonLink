package com.group12.salonlink.feature.customer.bookings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import com.group12.salonlink.R
import com.group12.salonlink.core.util.SalonTime
import com.group12.salonlink.domain.model.Booking

/**
 * "Cancel this booking?" confirmation (proposal: cancel with confirm dialog).
 * WHY ask first: cancelling frees the slot for someone else and can't be undone.
 */
@Composable
fun CancelBookingDialog(
    booking: Booking,
    cancelling: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cancel_dialog_title)) },
        text = {
            Text(
                stringResource(
                    R.string.cancel_dialog_text,
                    booking.title,
                    booking.staffName,
                    "${SalonTime.formatShortDate(booking.start)}, ${SalonTime.formatTime(booking.start)}",
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !cancelling) {
                if (cancelling) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.action_cancel_booking), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !cancelling) { Text(stringResource(R.string.action_keep_booking)) }
        },
    )
}
