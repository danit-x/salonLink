package com.group12.salonlink.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.group12.salonlink.R
import com.group12.salonlink.core.designsystem.theme.CardShape
import com.group12.salonlink.core.designsystem.theme.SalonLinkTheme
import com.group12.salonlink.core.designsystem.theme.SalonTheme
import com.group12.salonlink.core.designsystem.theme.Spacing

/**
 * Friendly "not built yet" screen.
 *
 * WHY: navigation for every role is wired up in Phase 0, so the app can be demoed end to
 * end from day one. Each placeholder is replaced by the real screen in the phase named on
 * [badge] (see PLAN.md §7). It is also used for the Concierge tab while AI is on hold.
 */
@Composable
fun PlaceholderScreen(
    title: String,
    message: String,
    badge: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val colors = SalonTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.screenHorizontal),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.selectedGradient, CardShape)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            IconBubble(icon = icon, containerColor = colors.glass, iconTint = colors.violet, size = 56.dp)
            SalonTag(text = badge, containerColor = colors.glass, contentColor = colors.violet)
            Text(text = title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null) {
                GlassButton(text = actionLabel, onClick = onAction, modifier = Modifier.padding(top = Spacing.sm))
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFDF8)
@Composable
private fun PlaceholderScreenPreview() {
    SalonLinkTheme {
        PlaceholderScreen(
            title = "Your bookings",
            message = "Upcoming visits, history and cancellations will live here.",
            badge = "Coming in Phase 5",
            icon = R.drawable.ic_calendar_days,
        )
    }
}
