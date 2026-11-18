package com.group12.salonlink.core.designsystem.theme

import android.content.res.AssetManager
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/*
 * =====================================================================================
 * Typography — the Figma design uses Inter everywhere.
 * -------------------------------------------------------------------------------------
 * assets/fonts/inter.ttf is Google's *variable* Inter font: one file containing every
 * weight from Thin to Black, selected through the "wght" axis.
 *
 * WHY assets and not res/font: Android caches a res/font typeface by resource id, so
 * asking for the same variable font at four weights returned the *same* (regular)
 * typeface, and every heading rendered thin. Loading from assets builds a fresh
 * Typeface per weight with the "wght" axis applied, so SemiBold really is SemiBold.
 *
 * Licence: SIL Open Font License (free to bundle in apps).
 * =====================================================================================
 */
private const val INTER_PATH = "fonts/inter.ttf"

/** The four Inter weights used by the design: 400, 500, 600 and 700. */
fun interFontFamily(assets: AssetManager): FontFamily = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
        Font(
            path = INTER_PATH,
            assetManager = assets,
            weight = weight,
            // Explicitly set the variable-font axis to the requested weight.
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    },
)

/**
 * Material 3 type scale mapped to the sizes used in Figma.
 * Comment on each line = where that style appears in the design.
 */
fun salonTypography(inter: FontFamily): Typography {
    /** Small helper so each style below stays one readable line. */
    fun style(size: Int, weight: FontWeight, lineHeight: Float? = null) = TextStyle(
        fontFamily = inter,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight?.em ?: TextStyle.Default.lineHeight,
    )

    return Typography(
        displaySmall = style(32, FontWeight.SemiBold, 1.12f),   // "Make it your own." page heroes
        headlineMedium = style(30, FontWeight.SemiBold, 1.12f), // "Good hair. Good energy."
        headlineSmall = style(23, FontWeight.SemiBold),         // Name on the featured barber photo
        titleLarge = style(18, FontWeight.SemiBold),            // Section headings ("Quick actions")
        titleMedium = style(15, FontWeight.SemiBold),           // App-bar titles, card titles
        titleSmall = style(14, FontWeight.SemiBold),            // List item titles ("Haircut")
        bodyLarge = style(13, FontWeight.Normal, 1.45f),        // Inputs, longer body text
        bodyMedium = style(12, FontWeight.Normal, 1.5f),        // Page sub-headings
        bodySmall = style(11, FontWeight.Normal, 1.4f),         // Secondary details (address, time)
        labelLarge = style(14, FontWeight.SemiBold),            // Primary buttons
        labelMedium = style(11, FontWeight.SemiBold),           // Eyebrows + links ("View all")
        labelSmall = style(10, FontWeight.Medium),              // Captions, step labels, nav labels
    )
}
