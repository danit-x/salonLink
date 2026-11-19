package com.group12.salonlink.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/*
 * =====================================================================================
 * SalonLink colour tokens
 * -------------------------------------------------------------------------------------
 * Every value below was copied from the team's Figma file (frames "Discovery · SalonLink"
 * and "Service selection · SalonLink"). Screens must NEVER hard-code a hex value; they use
 * MaterialTheme.colorScheme.* or SalonTheme.colors.* instead. That way a colour change is
 * made once, here, and the whole app follows.
 * =====================================================================================
 */

// --- Base palette (raw values, private to the design system) ---
internal val Cream = Color(0xFFFFFDF8)          // Discovery background
internal val CreamAlt = Color(0xFFFAF8F5)       // Booking-flow background
internal val Ink = Color(0xFF252333)            // Primary text
internal val InkMuted = Color(0xFF807C8C)       // Secondary text
internal val Indigo = Color(0xFF5263DE)         // Primary accent (links, active step)
internal val Violet = Color(0xFF6D4AB7)         // Active nav label, concierge headings
internal val Coral = Color(0xFFC96B4A)          // Greeting / eyebrow text
internal val Teal = Color(0xFF3E8799)           // "Picked for your style" tag
internal val Hairline = Color(0xFFE9E4EE)       // Dividers, inactive progress, card borders
internal val HairlineStrong = Color(0xFFD6D1DE) // Unchecked selection circle
internal val SelectedBorder = Color(0xFFAAB4EE) // Border of a selected service card
internal val ErrorRed = Color(0xFFC6414C)

internal val GradientStart = Color(0xFF7E91F0)
internal val GradientMid = Color(0xFF596DE1)
internal val GradientEnd = Color(0xFF8060D3)

/**
 * Extra colours that Material 3's [androidx.compose.material3.ColorScheme] has no slot for
 * (glass surfaces, pastel icon bubbles, gradients). Read them with `SalonTheme.colors`.
 *
 * WHY @Immutable: tells Compose these values never change after creation, so composables
 * that read them can skip recomposition.
 */
@Immutable
data class SalonColors(
    val backgroundAlt: Color = CreamAlt,
    val coral: Color = Coral,
    val violet: Color = Violet,
    val teal: Color = Teal,
    val hairline: Color = Hairline,
    val hairlineStrong: Color = HairlineStrong,
    val selectedBorder: Color = SelectedBorder,

    // "Glass" surfaces: semi-transparent white over the cream background.
    val glass: Color = Color.White.copy(alpha = 0.72f),
    val glassBorder: Color = Color.White.copy(alpha = 0.88f),

    // Pastel containers used behind icons and chips.
    val blueContainer: Color = Color(0xFFEAF3FF),
    val peachContainer: Color = Color(0xFFFFF1E8),
    val lavenderContainer: Color = Color(0xFFF7E8FF),
    val tealContainer: Color = Color(0xFFE6F3F4),
    val navIndicator: Color = Color(0xFFF0EAFB),

    /**
     * Specialty chips on "Barber profile" (Fade, Colour, Curly Hair, Beard Styling): each chip
     * takes the next (background, text) pair in turn, so neighbours never share a colour.
     */
    val tagPalette: List<Pair<Color, Color>> = listOf(
        Color(0xFFF7E8FF) to Color(0xFF7A4B8D), // lavender / plum
        Color(0xFFFFF1E8) to Coral,             // peach / coral
        Color(0xFFEAF3FF) to Color(0xFF5B7C9A), // sky / slate blue
        Color(0xFFFFF7E8) to Coral,             // butter / coral
    ),

    // Soft coloured shadows from Figma (0 5 16 rgba(75,59,87,.06) and 0 6 16 rgba(100,86,170,.13)).
    val shadowSoft: Color = Color(0x0F4B3B57),
    val shadowPrimary: Color = Color(0x216456AA),

    /** Primary call-to-action gradient (top → bottom). */
    val brandGradient: Brush = Brush.verticalGradient(listOf(GradientStart, GradientMid, GradientEnd)),

    /** The concierge "orb" on Discovery (Figma "Concierge invitation"). */
    val conciergeOrb: Brush = Brush.verticalGradient(
        listOf(Color(0xFF9F9AF2), Color(0xFF8065CF), Color(0xFFBF81C2)),
    ),

    /** Background of a selected option card (lavender → periwinkle → mint). */
    val selectedGradient: Brush = Brush.verticalGradient(
        0f to Color(0xFFF0E7FA),
        0.55f to Color(0xFFEBEEFF),
        1f to Color(0xFFE1F1F6),
    ),
)

/** Provides [SalonColors] down the composition tree; set up by [SalonLinkTheme]. */
val LocalSalonColors = staticCompositionLocalOf { SalonColors() }
