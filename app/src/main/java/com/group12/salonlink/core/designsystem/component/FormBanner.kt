package com.group12.salonlink.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.group12.salonlink.core.designsystem.theme.SalonTheme

/** Kind of [FormBanner]: an error (coral) or a neutral confirmation (lavender). */
enum class BannerTone { ERROR, INFO }

/**
 * Message box above the submit button, styled like the Figma live-queue notice
 * (peach gradient). `liveRegion` makes TalkBack read it out as soon as it appears.
 */
@Composable
fun FormBanner(message: String, tone: BannerTone, modifier: Modifier = Modifier) {
    val colors = SalonTheme.colors
    val (brush, textColor) = when (tone) {
        BannerTone.ERROR -> Brush.horizontalGradient(listOf(Color(0xFFFFF1E8), Color(0xFFFBE3EA))) to colors.coral
        BannerTone.INFO -> colors.selectedGradient to colors.violet
    }
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = textColor,
        modifier = modifier
            .fillMaxWidth()
            .background(brush, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}
