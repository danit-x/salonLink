// Covers: customer navigation · Figma: bottom navigation (Explore / Bookings / Concierge / My style)
package com.group12.salonlink.feature.customer

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.composable
import com.group12.salonlink.R
import com.group12.salonlink.core.designsystem.component.BottomBarItem
import com.group12.salonlink.core.designsystem.component.PlaceholderScreen
import com.group12.salonlink.core.navigation.BookingsRoute
import com.group12.salonlink.core.navigation.ConciergeRoute
import com.group12.salonlink.core.navigation.ExploreRoute
import com.group12.salonlink.core.navigation.MyStyleRoute
import com.group12.salonlink.core.navigation.RoleShell
import com.group12.salonlink.domain.model.UserProfile
import com.group12.salonlink.feature.account.AccountSummary

/** The four customer tabs, in the same order and with the same icons as Figma. */
private val customerTabs = listOf(
    BottomBarItem(ExploreRoute, R.string.tab_explore, R.drawable.ic_compass),
    BottomBarItem(BookingsRoute, R.string.tab_bookings, R.drawable.ic_calendar_days),
    BottomBarItem(ConciergeRoute, R.string.tab_concierge, R.drawable.ic_sparkles),
    BottomBarItem(MyStyleRoute, R.string.tab_my_style, R.drawable.ic_user_round),
)

/**
 * Customer home with its bottom navigation.
 * Each placeholder is swapped for the real screen in the phase shown on its badge.
 *
 * @param user the signed-in customer (always role CUSTOMER here, guaranteed by SalonLinkRoot).
 */
@Composable
fun CustomerShell(user: UserProfile, onSignOut: () -> Unit) {
    RoleShell(tabs = customerTabs) {
        composable<ExploreRoute> {
            PlaceholderScreen(
                title = stringResource(R.string.explore_placeholder_title),
                message = stringResource(R.string.explore_placeholder_message),
                badge = stringResource(R.string.badge_phase, 4),
                icon = R.drawable.ic_compass,
            )
        }
        composable<BookingsRoute> {
            PlaceholderScreen(
                title = stringResource(R.string.bookings_placeholder_title),
                message = stringResource(R.string.bookings_placeholder_message),
                badge = stringResource(R.string.badge_phase, 5),
                icon = R.drawable.ic_calendar_days,
            )
        }
        composable<ConciergeRoute> {
            // AI concierge is ON HOLD (decided 18 Nov 2026). The tab stays so the navigation
            // matches the approved Figma design.
            PlaceholderScreen(
                title = stringResource(R.string.concierge_on_hold_title),
                message = stringResource(R.string.concierge_on_hold_message),
                badge = stringResource(R.string.badge_on_hold),
                icon = R.drawable.ic_sparkles,
            )
        }
        composable<MyStyleRoute> {
            AccountSummary(
                user = user,
                nextUpNote = stringResource(R.string.style_placeholder_message),
                onSignOut = onSignOut,
            )
        }
    }
}
