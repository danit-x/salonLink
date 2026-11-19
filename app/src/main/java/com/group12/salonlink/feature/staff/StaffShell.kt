// Covers: staff navigation (US-07, US-08) · Design: new, in the app's visual language
package com.group12.salonlink.feature.staff

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.composable
import com.group12.salonlink.R
import com.group12.salonlink.core.designsystem.component.BottomBarItem
import com.group12.salonlink.core.navigation.RoleShell
import com.group12.salonlink.core.navigation.StaffSalonRoute
import com.group12.salonlink.core.navigation.StaffTodayRoute
import com.group12.salonlink.core.navigation.StaffWalkInsRoute
import com.group12.salonlink.domain.model.UserProfile
import com.group12.salonlink.feature.account.AccountSummary
import com.group12.salonlink.feature.staff.schedule.ScheduleScreen
import com.group12.salonlink.feature.staff.schedule.ScheduleViewModel
import com.group12.salonlink.feature.staff.walkin.WalkInScreen
import com.group12.salonlink.feature.staff.walkin.WalkInViewModel

private val staffTabs = listOf(
    BottomBarItem(StaffTodayRoute, R.string.tab_today, R.drawable.ic_calendar_days),
    BottomBarItem(StaffWalkInsRoute, R.string.tab_walk_ins, R.drawable.ic_user_round),
    BottomBarItem(StaffSalonRoute, R.string.tab_salon, R.drawable.ic_map_pin),
)

/** Staff (barber / stylist) home: their own day with status buttons, walk-ins, and account. */
@Composable
fun StaffShell(user: UserProfile, onSignOut: () -> Unit) {
    RoleShell(tabs = staffTabs) {
        composable<StaffTodayRoute> {
            val viewModel: ScheduleViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            ScheduleScreen(state, viewModel::onEvent)
        }
        composable<StaffWalkInsRoute> {
            val viewModel: WalkInViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            WalkInScreen(state, viewModel::onEvent)
        }
        composable<StaffSalonRoute> {
            AccountSummary(user = user, nextUpNote = stringResource(R.string.staff_salon_note), onSignOut = onSignOut)
        }
    }
}
