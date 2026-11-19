package com.group12.salonlink.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.group12.salonlink.R
import com.group12.salonlink.core.designsystem.theme.PillShape
import com.group12.salonlink.core.designsystem.theme.SalonLinkTheme
import com.group12.salonlink.core.designsystem.theme.SalonTheme

/**
 * The main call-to-action button ("Choose date & time", "Confirm appointment").
 * Figma: "Glass action": 56 dp pill, brand gradient, white semibold label.
 *
 * @param loading shows a spinner and blocks taps. Use it while a network call runs, so a
 *   user cannot double-tap and send the same booking twice.
 */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes trailingIcon: Int? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = SalonTheme.colors
    Box(
        modifier = modifier
            .heightIn(min = 56.dp)
            // Coloured shadow under the button (only drawn while it's usable).
            .shadow(
                elevation = if (enabled) 12.dp else 0.dp,
                shape = PillShape,
                ambientColor = colors.shadowPrimary,
                spotColor = colors.shadowPrimary,
            )
            .clip(PillShape)
            .background(colors.brandGradient, alpha = if (enabled) 1f else 0.45f)
            .border(1.dp, Color.White.copy(alpha = 0.44f), PillShape)
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = text, style = MaterialTheme.typography.labelLarge, color = Color.White)
                if (trailingIcon != null) {
                    Icon(
                        painter = painterResource(trailingIcon),
                        contentDescription = null, // decorative: the label already says it
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/**
 * Secondary pill button on a frosted-white background ("View my booking", "Book Again").
 */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes leadingIcon: Int? = null,
    @DrawableRes trailingIcon: Int? = null,
    containerColor: Color = SalonTheme.colors.glass,
    contentColor: Color = SalonTheme.colors.violet,
    enabled: Boolean = true,
) {
    val colors = SalonTheme.colors
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .shadow(8.dp, PillShape, ambientColor = colors.shadowSoft, spotColor = colors.shadowSoft)
            .clip(PillShape)
            .background(containerColor)
            .border(1.dp, colors.glassBorder, PillShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                painter = painterResource(leadingIcon),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = contentColor)
        if (trailingIcon != null) {
            Icon(
                painter = painterResource(trailingIcon),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Round 40 dp icon button used in app bars (back, bell, heart).
 * Figma: "Quick action".
 *
 * @param contentDescription read aloud by TalkBack. Always describe the action.
 */
@Composable
fun GlassIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SalonTheme.colors
    Box(
        modifier = modifier
            .size(40.dp)
            .shadow(8.dp, PillShape, ambientColor = colors.shadowSoft, spotColor = colors.shadowSoft)
            .clip(PillShape)
            .background(colors.glass)
            .border(1.dp, colors.glassBorder, PillShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(19.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFDF8)
@Composable
private fun ButtonsPreview() {
    SalonLinkTheme {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GradientButton("Choose date & time", {}, Modifier.fillMaxWidth(), R.drawable.ic_arrow_right)
            GradientButton("Saving…", {}, Modifier.fillMaxWidth(), loading = true)
            GlassButton("View my booking", {}, Modifier.fillMaxWidth())
            GlassIconButton(R.drawable.ic_arrow_left, "Back", {})
        }
    }
}
