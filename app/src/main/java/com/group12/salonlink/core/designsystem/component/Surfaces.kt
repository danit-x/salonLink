package com.group12.salonlink.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.group12.salonlink.R
import com.group12.salonlink.core.designsystem.theme.CardShape
import com.group12.salonlink.core.designsystem.theme.PillShape
import com.group12.salonlink.core.designsystem.theme.SalonLinkTheme
import com.group12.salonlink.core.designsystem.theme.SalonTheme

/**
 * Frosted "glass" card: translucent white, a thin white border and a soft shadow.
 * Used for the search bar, the appointment card, form groups, etc.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = CardShape,
    color: Color = SalonTheme.colors.glass,
    contentPadding: Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = SalonTheme.colors
    Box(
        modifier = modifier
            .shadow(10.dp, shape, ambientColor = colors.shadowSoft, spotColor = colors.shadowSoft)
            .clip(shape)
            .background(color)
            .border(1.dp, colors.glassBorder, shape)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * The SalonLink logo: white scissors on the brand gradient, in a rounded square.
 * Figma: "Brand mark" (34 dp, radius 12).
 */
@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = 34.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.35f))
            .background(SalonTheme.colors.brandGradient),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_scissors),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.56f),
        )
    }
}

/**
 * Pastel rounded square holding an icon, e.g. the category bubbles on Discovery.
 */
@Composable
fun IconBubble(
    @DrawableRes icon: Int,
    containerColor: Color,
    iconTint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 45.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 2))
            .background(containerColor)
            .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(size / 2)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(size * 0.44f),
        )
    }
}

/**
 * Small pill label, e.g. "Picked for your style", "Confirmed", specialty chips.
 */
@Composable
fun SalonTag(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    Row(
        modifier = modifier
            .clip(PillShape)
            .background(containerColor)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(13.dp),
            )
        }
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = contentColor)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFDF8)
@Composable
private fun SurfacesPreview() {
    SalonLinkTheme {
        val c = SalonTheme.colors
        Row(
            modifier = Modifier.padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandMark()
            IconBubble(R.drawable.ic_scissors, c.blueContainer, MaterialTheme.colorScheme.primary)
            SalonTag("Picked for your style", c.tealContainer, c.teal, icon = R.drawable.ic_sparkles)
        }
    }
}
