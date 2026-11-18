// Covers: US-01 (entry point to register / log in) · Design: new screen in the Figma visual language
package com.group12.salonlink.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.group12.salonlink.R
import com.group12.salonlink.core.designsystem.component.BrandMark
import com.group12.salonlink.core.designsystem.component.GlassButton
import com.group12.salonlink.core.designsystem.component.GradientButton
import com.group12.salonlink.core.designsystem.theme.SalonLinkTheme
import com.group12.salonlink.core.designsystem.theme.SalonTheme
import com.group12.salonlink.core.designsystem.theme.Spacing

/**
 * First screen a signed-out user sees.
 *
 * Stateless by design: it only shows UI and reports clicks upward through lambdas. The
 * navigation decision belongs to [com.group12.salonlink.core.navigation.SalonLinkNavHost].
 * That makes the screen trivial to preview and test.
 */
@Composable
fun WelcomeScreen(
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding() // keep content clear of status bar, notch and nav bar
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.lg),
    ) {
        // --- Brand row ---
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            BrandMark()
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
        }

        // --- Hero illustration (pure Compose shapes, no image file needed) ---
        WelcomeHero(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        // --- Copy ---
        Text(
            text = stringResource(R.string.welcome_eyebrow).uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
            color = SalonTheme.colors.coral,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.welcome_headline),
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 38.sp),
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = stringResource(R.string.welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))

        // --- Actions ---
        GradientButton(
            text = stringResource(R.string.welcome_create_account),
            onClick = onCreateAccount,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = R.drawable.ic_arrow_right,
        )
        Spacer(Modifier.height(Spacing.md))
        GlassButton(
            text = stringResource(R.string.welcome_sign_in),
            onClick = onSignIn,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        )
        Spacer(Modifier.height(Spacing.lg))
        Text(
            text = stringResource(R.string.welcome_footer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Soft glowing orb with floating dots, echoing the Figma confirmation screen's check-mark
 * orb. Built from circles and gradients, so it scales on any screen size.
 */
@Composable
private fun WelcomeHero(modifier: Modifier = Modifier) {
    val colors = SalonTheme.colors
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Outer halo
        Box(
            Modifier
                .size(220.dp)
                .background(
                    Brush.radialGradient(listOf(Color(0xFFEDE6FB), Color(0xFFE5F3F7), Color.Transparent)),
                    CircleShape,
                ),
        )
        // Floating accent dots (positions picked to feel balanced, like the Figma confetti)
        Dot(colors.peachContainer, 34.dp, Modifier.offset(x = (-112).dp, y = (-40).dp))
        Dot(Color(0xFFDDEEF3), 18.dp, Modifier.offset(x = 104.dp, y = (-72).dp))
        Dot(Color(0xFFF9DDE6), 22.dp, Modifier.offset(x = 96.dp, y = 70.dp))
        Dot(colors.lavenderContainer, 14.dp, Modifier.offset(x = (-80).dp, y = 84.dp))
        // The logo, large, in the middle
        BrandMark(size = 104.dp)
    }
}

@Composable
private fun Dot(color: Color, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(color, CircleShape))
}

@Preview(showSystemUi = true)
@Composable
private fun WelcomeScreenPreview() {
    SalonLinkTheme { WelcomeScreen(onCreateAccount = {}, onSignIn = {}) }
}
