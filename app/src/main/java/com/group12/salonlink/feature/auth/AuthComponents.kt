package com.group12.salonlink.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.group12.salonlink.R
import com.group12.salonlink.core.designsystem.component.SalonTopBar
import com.group12.salonlink.core.designsystem.theme.SalonTheme
import com.group12.salonlink.core.designsystem.theme.Spacing

/**
 * Shared layout for Sign in and Register: back button, an eyebrow + big headline in the
 * Figma "page hero" style, then the form. It scrolls and moves up with the keyboard
 * ([imePadding]), so fields are never hidden behind it.
 */
@Composable
fun AuthScaffold(
    eyebrow: String,
    headline: String,
    body: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SalonTheme.colors.backgroundAlt)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        SalonTopBar(title = "", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
        ) {
            Text(
                text = eyebrow.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                color = SalonTheme.colors.coral,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = headline,
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xl))
            content()
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

/** "Show" / "Hide" text toggle for password fields (clearer than an eye icon for TalkBack). */
@Composable
fun PasswordVisibilityToggle(visible: Boolean, onToggle: () -> Unit) {
    TextButton(onClick = onToggle) {
        Text(
            text = stringResource(if (visible) R.string.auth_hide_password else R.string.auth_show_password),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** "New to SalonLink? Create an account" style footer link. */
@Composable
fun AuthSwitchPrompt(prompt: String, action: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(prompt, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onClick) {
            Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}
