// Covers: staff navigation (US-07, US-08) · Design: new, in the Figma visual language
package com.group12.salonlink.feature.staff

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.composable
import com.group12.salonlink.R
import com.group12.salonlink.core.designsystem.component.BottomBarItem
import com.group12.salonlink.core.designsystem.component.PlaceholderScreen
import com.group12.salonlink.core.navigation.RoleShell
import com.group12.salonlink.core.navigation.StaffSalonRoute
import com.group12.salonlink.core.navigation.StaffTodayRoute
import com.group12.salonlink.core.navigation.StaffWalkInsRoute

private val staffTabs = listOf(
    BottomBarItem(StaffTodayRoute, R.string.tab_today, R.drawable.ic_calendar_days),
    BottomBarItem(StaffWalkInsRoute, R.string.tab_walk_ins, R.drawable.ic_user_round),
    BottomBarItem(StaffSalonRoute, R.string.tab_salon, R.drawable.ic_map_pin),
)

/** Staff (barber / stylist) home: today's schedule, walk-ins and salon info. */
@Composable
fun StaffShell(onSignOut: () -> Unit) {
    RoleShell(tabs = staffTabs) {
        composable<StaffTodayRoute> {
            PlaceholderScreen(
                title = stringResource(R.string.staff_today_placeholder_title),
                message = stringResource(R.string.staff_today_placeholder_message),
                badge = stringResource(R.string.badge_phase, 6),
                icon = R.drawable.ic_calendar_days,
            )
        }
        composable<StaffWalkInsRoute> {
            PlaceholderScreen(
                title = stringResource(R.string.staff_walkin_placeholder_title),
                message = stringResource(R.string.staff_walkin_placeholder_message),
                badge = stringResource(R.string.badge_phase, 6),
                icon = R.drawable.ic_user_round,
            )
        }
        composable<StaffSalonRoute> {
            PlaceholderScreen(
                title = stringResource(R.string.staff_salon_placeholder_title),
                message = stringResource(R.string.staff_salon_placeholder_message),
                badge = stringResource(R.string.badge_phase, 6),
                icon = R.drawable.ic_map_pin,
                actionLabel = stringResource(R.string.sign_out_preview),
                onAction = onSignOut,
            )
        }
    }
}
