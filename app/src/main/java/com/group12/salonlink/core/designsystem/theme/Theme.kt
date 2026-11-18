package com.group12.salonlink.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Material 3 colour roles filled with the SalonLink palette.
 * Material components (TextField, Dialog, Snackbar...) automatically use these roles.
 */
private val SalonColorScheme = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEBEEFF),
    onPrimaryContainer = Ink,
    secondary = Violet,
    onSecondary = Color.White,
    tertiary = Coral,
    onTertiary = Color.White,
    background = Cream,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = CreamAlt,
    onSurfaceVariant = InkMuted,
    outline = HairlineStrong,
    outlineVariant = Hairline,
    error = ErrorRed,
    onError = Color.White,
)

/**
 * Root theme for the whole app. Wrap every screen (and every @Preview) in this.
 *
 * WHY light only: the Figma design defines a single warm, light look and no dark variant.
 * Inventing dark colours would not match the approved design, so the app stays light.
 */
@Composable
fun SalonLinkTheme(content: @Composable () -> Unit) {
    // The font is loaded from assets, which needs a Context. `remember` builds the
    // typography once instead of on every recomposition.
    val context = LocalContext.current
    val typography = remember(context) { salonTypography(interFontFamily(context.assets)) }

    CompositionLocalProvider(LocalSalonColors provides SalonColors()) {
        MaterialTheme(
            colorScheme = SalonColorScheme,
            typography = typography,
            shapes = SalonShapes,
            content = content,
        )
    }
}

/**
 * Short accessor for the extra design tokens, e.g. `SalonTheme.colors.brandGradient`.
 * Mirrors how `MaterialTheme.colorScheme` works.
 */
object SalonTheme {
    val colors: SalonColors
        @Composable @ReadOnlyComposable
        get() = LocalSalonColors.current
}
