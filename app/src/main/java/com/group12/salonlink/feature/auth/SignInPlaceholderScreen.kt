// TEMPORARY (Phase 0 only). Replaced by real Firebase email/password sign-in in Phase 1.
package com.group12.salonlink.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.group12.salonlink.R
import com.group12.salonlink.core.designsystem.component.GlassButton
import com.group12.salonlink.core.designsystem.component.GradientButton
import com.group12.salonlink.core.designsystem.component.IconBubble
import com.group12.salonlink.core.designsystem.component.SalonTopBar
import com.group12.salonlink.core.designsystem.theme.CardShape
import com.group12.salonlink.core.designsystem.theme.SalonLinkTheme
import com.group12.salonlink.core.designsystem.theme.SalonTheme
import com.group12.salonlink.core.designsystem.theme.Spacing

/**
 * Phase 0 stand-in for the sign-in screen. It lets the team and examiners open each role's
 * navigation before authentication exists. In Phase 1 the user's role comes from their
 * Firestore profile instead of these buttons.
 */
@Composable
fun SignInPlaceholderScreen(
    onBack: () -> Unit,
    onPreviewCustomer: () -> Unit,
    onPreviewStaff: () -> Unit,
    onPreviewOwner: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SalonTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundAlt)
            .safeDrawingPadding(),
    ) {
        SalonTopBar(title = stringResource(R.string.signin_title), onBack = onBack)

        Column(
            modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // Explanation card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.selectedGradient, CardShape)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                IconBubble(R.drawable.ic_user_round, colors.glass, colors.violet, size = 52.dp)
                Text(
                    stringResource(R.string.signin_placeholder_title),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.signin_placeholder_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Text(
                text = stringResource(R.string.signin_preview_label),
                style = MaterialTheme.typography.labelMedium,
                color = colors.coral,
                modifier = Modifier.padding(top = Spacing.md),
            )
            GradientButton(
                text = stringResource(R.string.role_customer),
                onClick = onPreviewCustomer,
                modifier = Modifier.fillMaxWidth(),
            )
            GlassButton(
                text = stringResource(R.string.role_staff),
                onClick = onPreviewStaff,
                leadingIcon = R.drawable.ic_scissors,
                modifier = Modifier.fillMaxWidth(),
            )
            GlassButton(
                text = stringResource(R.string.role_owner),
                onClick = onPreviewOwner,
                leadingIcon = R.drawable.ic_map_pin,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun SignInPlaceholderPreview() {
    SalonLinkTheme { SignInPlaceholderScreen({}, {}, {}, {}) }
}
