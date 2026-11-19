package com.group12.salonlink.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.group12.salonlink.core.designsystem.theme.SalonLinkTheme
import com.group12.salonlink.core.designsystem.theme.SalonTheme

/**
 * A photo (barber portrait, portfolio shot) loaded with Coil, clipped to [shape].
 *
 * [url] may be an https link or a bundled asset (`file:///android_asset/demo/…`, used by the
 * demo seed because Cloud Storage needs the Blaze plan).
 *
 * WHY the initials underneath: when there is no photo, or it fails to load (offline, bad
 * link), the gradient with "KP" stays visible, so the layout never shows an empty hole.
 *
 * @param name used for the initials fallback.
 * @param alignment which part of the photo to keep when cropping. Portraits use TopCenter so
 *   faces are not cut off (the Figma frames crop from the top).
 */
@Composable
fun SalonPhoto(
    url: String?,
    name: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    alignment: Alignment = Alignment.TopCenter,
    initialsSize: TextUnit = 18.sp,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(SalonTheme.colors.brandGradient),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initialsOf(name),
            color = Color.White,
            fontSize = initialsSize,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium,
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                alignment = alignment,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** "Kasun Perera" → "KP", "Nadeesha" → "N". */
internal fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercase() }

@Preview(showBackground = true, backgroundColor = 0xFFFFFDF8)
@Composable
private fun SalonPhotoPreview() {
    SalonLinkTheme {
        Row(Modifier.padding(16.dp)) {
            SalonPhoto(url = null, name = "Nadeesha Fernando", contentDescription = null, modifier = Modifier.size(48.dp))
        }
    }
}
