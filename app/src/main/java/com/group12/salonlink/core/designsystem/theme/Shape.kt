package com.group12.salonlink.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/*
 * Corner radii from Figma. The design is soft and rounded: cards are 24–28 dp,
 * buttons and search bars are fully round "pills".
 */

/** Fully rounded ends — buttons, search bar, tags. */
val PillShape = RoundedCornerShape(percent = 50)

/** Large option / appointment cards (Figma radius 26). */
val CardShape = RoundedCornerShape(26.dp)

/** Hero photo cards (Figma radius 28). */
val HeroShape = RoundedCornerShape(28.dp)

/** Round-cornered square behind an icon (Figma radius 20–22). */
val BubbleShape = RoundedCornerShape(20.dp)

/** Material slots, used by Material components such as dialogs and text fields. */
val SalonShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Spacing scale so screens don't invent their own numbers. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 22.dp

    /** Left/right padding of every screen in Figma. */
    val screenHorizontal = 24.dp
}
