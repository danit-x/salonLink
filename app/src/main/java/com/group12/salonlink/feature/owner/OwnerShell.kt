// Covers: owner / manager navigation (US-02, US-09) · Design: new, in the app's visual language
package com.group12.salonlink.feature.owner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.group12.salonlink.R
import com.group12.salonlink.core.designsystem.component.BottomBarItem
import com.group12.salonlink.core.navigation.OwnerDashboardRoute
import com.group12.salonlink.core.navigation.OwnerSalonRoute
import com.group12.salonlink.core.navigation.OwnerServicesRoute
import com.group12.salonlink.core.navigation.OwnerStaffEditorRoute
import com.group12.salonlink.core.navigation.OwnerStaffRoute
import com.group12.salonlink.core.navigation.OwnerWalkInRoute
import com.group12.salonlink.core.navigation.RoleShell
import com.group12.salonlink.domain.model.UserProfile
import com.group12.salonlink.feature.owner.salon.SalonSettingsScreen
import com.group12.salonlink.feature.owner.salon.SalonSettingsViewModel
import com.group12.salonlink.feature.owner.services.ServicesAdminScreen
import com.group12.salonlink.feature.owner.services.ServicesAdminViewModel
import com.group12.salonlink.feature.owner.team.StaffEditorScreen
import com.group12.salonlink.feature.owner.team.StaffEditorViewModel
import com.group12.salonlink.feature.owner.team.TeamScreen
import com.group12.salonlink.feature.owner.team.TeamViewModel
import com.group12.salonlink.feature.staff.schedule.ScheduleScreen
import com.group12.salonlink.feature.staff.schedule.ScheduleViewModel
import com.group12.salonlink.feature.staff.walkin.WalkInScreen
import com.group12.salonlink.feature.staff.walkin.WalkInViewModel

private val ownerTabs = listOf(
    BottomBarItem(OwnerDashboardRoute, R.string.tab_dashboard, R.drawable.ic_compass),
    BottomBarItem(OwnerServicesRoute, R.string.tab_services, R.drawable.ic_scissors),
    BottomBarItem(OwnerStaffRoute, R.string.tab_staff, R.drawable.ic_user_round),
    BottomBarItem(OwnerSalonRoute, R.string.tab_salon, R.drawable.ic_map_pin),
)

/**
 * Salon owner / manager home: today's numbers and schedule (with walk-ins), services and prices,
 * the team (hours, time off, invites) and the salon's own details.
 */
@Composable
fun OwnerShell(user: UserProfile, onSignOut: () -> Unit) {
    val navController = rememberNavController()
    RoleShell(tabs = ownerTabs, navController = navController) {
        composable<OwnerDashboardRoute> {
            val viewModel: ScheduleViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            Box {
                ScheduleScreen(state, viewModel::onEvent)
                ExtendedFloatingActionButton(
                    onClick = { navController.navigate(OwnerWalkInRoute) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                ) { Text(stringResource(R.string.walkin_title)) }
            }
        }
        composable<OwnerWalkInRoute> {
            val viewModel: WalkInViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            WalkInScreen(state, viewModel::onEvent, onBack = { navController.popBackStack() })
        }
        composable<OwnerServicesRoute> {
            val viewModel: ServicesAdminViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            ServicesAdminScreen(state, viewModel::onEvent)
        }
        composable<OwnerStaffRoute> {
            val viewModel: TeamViewModel = hiltViewModel()
            val staff by viewModel.staff.collectAsStateWithLifecycle()
            TeamScreen(staff, onOpen = { navController.navigate(OwnerStaffEditorRoute(it)) })
        }
        composable<OwnerStaffEditorRoute> { entry ->
            val args = entry.toRoute<OwnerStaffEditorRoute>()
            val viewModel = hiltViewModel<StaffEditorViewModel, StaffEditorViewModel.Factory>(creationCallback = { it.create(args) })
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            StaffEditorScreen(state, viewModel::onEvent, onBack = { navController.popBackStack() })
        }
        composable<OwnerSalonRoute> {
            val viewModel: SalonSettingsViewModel = hiltViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            SalonSettingsScreen(viewModel, state, onSignOut)
        }
    }
}
