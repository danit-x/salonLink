// Covers: FR-07 (in-app notifications) · Design: new, in the app's visual language
package com.group12.salonlink.feature.customer.notifications

import android.text.format.DateUtils
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.group12.salonlink.R
import com.group12.salonlink.core.designsystem.component.BannerTone
import com.group12.salonlink.core.designsystem.component.FormBanner
import com.group12.salonlink.core.designsystem.component.GlassSurface
import com.group12.salonlink.core.designsystem.component.IconBubble
import com.group12.salonlink.core.designsystem.component.SalonTopBar
import com.group12.salonlink.core.designsystem.theme.CardShape
import com.group12.salonlink.core.designsystem.theme.SalonTheme
import com.group12.salonlink.core.designsystem.theme.Spacing
import com.group12.salonlink.domain.model.AppNotification
import com.group12.salonlink.domain.model.NotificationType
import com.group12.salonlink.feature.customer.booking.message

/** The feed behind the bell on Explore. Tapping an entry marks it read and opens the booking. */
@Composable
fun NotificationsScreen(
    state: NotificationsUiState,
    onBack: () -> Unit,
    onOpen: (AppNotification) -> Unit,
    onMarkAllRead: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SalonTopBar(
            title = stringResource(R.string.notifications_title),
            onBack = onBack,
            actions = {
                if (state.unreadCount > 0) {
                    TextButton(onClick = onMarkAllRead) { Text(stringResource(R.string.notifications_mark_all_read)) }
                }
            },
        )
        state.error?.let { Box(Modifier.padding(horizontal = Spacing.screenHorizontal)) { FormBanner(it.message(), BannerTone.ERROR) } }
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.items.isEmpty() -> Box(Modifier.padding(Spacing.screenHorizontal)) {
                FormBanner(stringResource(R.string.notifications_empty), BannerTone.INFO)
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.items, key = { it.id }) { NotificationRow(it, onClick = { onOpen(it) }) }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: AppNotification, onClick: () -> Unit) {
    val context = LocalContext.current
    val colors = SalonTheme.colors
    val (title, body) = NotificationText.of(context, n)
    val (icon, bg) = when (n.type) {
        NotificationType.BOOKING_CANCELLED -> R.drawable.ic_calendar_days to colors.peachContainer
        NotificationType.WAITLIST_OFFER -> R.drawable.ic_sparkles to colors.lavenderContainer
        NotificationType.STATUS_CHANGED -> R.drawable.ic_scissors to colors.tealContainer
        else -> R.drawable.ic_check to colors.blueContainer
    }
    GlassSurface(
        modifier = Modifier.fillMaxWidth().clip(CardShape).clickable(onClick = onClick),
        color = if (n.read) colors.glass else androidx.compose.ui.graphics.Color.White,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubble(icon = icon, containerColor = bg, iconTint = colors.violet, size = 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                n.createdAt?.let {
                    Text(
                        text = DateUtils.getRelativeTimeSpanString(it.toEpochMilli()).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!n.read) Box(Modifier.size(9.dp).background(colors.coral, CircleShape))
        }
    }
}
