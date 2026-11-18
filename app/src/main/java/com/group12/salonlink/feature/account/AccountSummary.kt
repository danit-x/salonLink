// Covers: US-01 acceptance ("access the correct features") — shows who is signed in and their role.
package com.group12.salonlink.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.group12.salonlink.R
import com.group12.salonlink.core.designsystem.component.GlassButton
import com.group12.salonlink.core.designsystem.component.GlassSurface
import com.group12.salonlink.core.designsystem.component.IconBubble
import com.group12.salonlink.core.designsystem.component.SalonTag
import com.group12.salonlink.core.designsystem.theme.SalonLinkTheme
import com.group12.salonlink.core.designsystem.theme.SalonTheme
import com.group12.salonlink.core.designsystem.theme.Spacing
import com.group12.salonlink.domain.model.Role
import com.group12.salonlink.domain.model.UserProfile

/**
 * Signed-in user's card: name, email, role badge, plus a "coming next" note and sign-out.
 * Used on the last tab of every role until that tab gets its real screen.
 */
@Composable
fun AccountSummary(
    user: UserProfile,
    nextUpNote: String,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SalonTheme.colors
    Box(modifier.fillMaxSize().padding(Spacing.screenHorizontal), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            GlassSurface(Modifier.fillMaxWidth(), contentPadding = 20.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(R.drawable.ic_user_round, colors.lavenderContainer, colors.violet, size = 52.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(user.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SalonTag(text = stringResource(user.role.labelRes()), containerColor = colors.tealContainer, contentColor = colors.teal)
                }
            }
            Text(
                text = nextUpNote,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GlassButton(text = stringResource(R.string.sign_out), onClick = onSignOut, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Display name of a role ("Customer", "Staff member", "Salon owner"). */
fun Role.labelRes(): Int = when (this) {
    Role.CUSTOMER -> R.string.role_customer
    Role.STAFF -> R.string.role_staff
    Role.OWNER -> R.string.role_owner
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFDF8)
@Composable
private fun AccountSummaryPreview() {
    SalonLinkTheme {
        AccountSummary(
            user = UserProfile("u1", "Kavindu Perera", "kavindu@example.com", null, Role.CUSTOMER),
            nextUpNote = "Your style profile arrives in Phase 9.",
            onSignOut = {},
        )
    }
}
