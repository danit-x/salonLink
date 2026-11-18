// Covers: owner / manager navigation (US-02, US-09) · Design: new, in the Figma visual language
package com.group12.salonlink.feature.owner

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.composable
import com.group12.salonlink.R
import com.group12.salonlink.core.designsystem.component.BottomBarItem
import com.group12.salonlink.core.designsystem.component.PlaceholderScreen
import com.group12.salonlink.core.navigation.OwnerDashboardRoute
import com.group12.salonlink.core.navigation.OwnerSalonRoute
import com.group12.salonlink.core.navigation.OwnerServicesRoute
import com.group12.salonlink.core.navigation.OwnerStaffRoute
import com.group12.salonlink.core.navigation.RoleShell
import com.group12.salonlink.domain.model.UserProfile
import com.group12.salonlink.feature.account.AccountSummary

private val ownerTabs = listOf(
    BottomBarItem(OwnerDashboardRoute, R.string.tab_dashboard, R.drawable.ic_compass),
    BottomBarItem(OwnerServicesRoute, R.string.tab_services, R.drawable.ic_scissors),
    BottomBarItem(OwnerStaffRoute, R.string.tab_staff, R.drawable.ic_user_round),
    BottomBarItem(OwnerSalonRoute, R.string.tab_salon, R.drawable.ic_map_pin),
)

/** Salon owner / manager home: daily dashboard, services, team and salon settings. */
@Composable
fun OwnerShell(user: UserProfile, onSignOut: () -> Unit) {
    RoleShell(tabs = ownerTabs) {
        composable<OwnerDashboardRoute> {
            PlaceholderScreen(
                title = stringResource(R.string.owner_dashboard_placeholder_title),
                message = stringResource(R.string.owner_dashboard_placeholder_message),
                badge = stringResource(R.string.badge_phase, 6),
                icon = R.drawable.ic_compass,
            )
        }
        composable<OwnerServicesRoute> {
            PlaceholderScreen(
                title = stringResource(R.string.owner_services_placeholder_title),
                message = stringResource(R.string.owner_services_placeholder_message),
                badge = stringResource(R.string.badge_phase, 6),
                icon = R.drawable.ic_scissors,
            )
        }
        composable<OwnerStaffRoute> {
            PlaceholderScreen(
                title = stringResource(R.string.owner_staff_placeholder_title),
                message = stringResource(R.string.owner_staff_placeholder_message),
                badge = stringResource(R.string.badge_phase, 6),
                icon = R.drawable.ic_user_round,
            )
        }
        composable<OwnerSalonRoute> {
            AccountSummary(
                user = user,
                nextUpNote = stringResource(R.string.owner_salon_placeholder_message),
                onSignOut = onSignOut,
            )
        }
    }
}
