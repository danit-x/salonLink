package com.group12.salonlink.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.group12.salonlink.R
import com.group12.salonlink.core.designsystem.theme.SalonLinkTheme
import com.group12.salonlink.core.designsystem.theme.SalonTheme

/**
 * One tab in a bottom bar.
 *
 * @param route the type-safe navigation route this tab opens (any @Serializable object).
 */
data class BottomBarItem(
    val route: Any,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
)

/**
 * Bottom navigation matching Figma's "Bottom navigation": white 80 % bar with a hairline on
 * top. The active tab gets a lavender pill behind its icon and a violet label.
 *
 * The bar is generic: Customer, Staff and Owner each pass their own list of [items].
 */
@Composable
fun SalonBottomBar(
    items: List<BottomBarItem>,
    isSelected: (BottomBarItem) -> Boolean,
    onItemClick: (BottomBarItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.8f))
            .navigationBarsPadding(), // keep tabs above the system gesture bar
    ) {
        HorizontalDivider(color = SalonTheme.colors.hairline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 17.dp)
                // Tells TalkBack these tabs are one group where only one is selected.
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                BottomBarTab(
                    item = item,
                    selected = isSelected(item),
                    onClick = { onItemClick(item) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BottomBarTab(
    item: BottomBarItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SalonTheme.colors
    // Animate the colour change so switching tabs feels smooth.
    val indicator by animateColorAsState(if (selected) colors.navIndicator else Color.Transparent, label = "indicator")
    val contentColor = if (selected) colors.violet else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .width(46.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(indicator),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(item.icon),
                contentDescription = null, // the label below is read instead
                tint = contentColor,
                modifier = Modifier.size(19.dp),
            )
        }
        Text(
            text = stringResource(item.label),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = contentColor,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFDF8)
@Composable
private fun SalonBottomBarPreview() {
    val items = listOf(
        BottomBarItem("explore", R.string.tab_explore, R.drawable.ic_compass),
        BottomBarItem("bookings", R.string.tab_bookings, R.drawable.ic_calendar_days),
        BottomBarItem("concierge", R.string.tab_concierge, R.drawable.ic_sparkles),
        BottomBarItem("style", R.string.tab_my_style, R.drawable.ic_user_round),
    )
    SalonLinkTheme {
        SalonBottomBar(items = items, isSelected = { it.route == "explore" }, onItemClick = {})
    }
}
