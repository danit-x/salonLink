package com.group12.salonlink.core.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.group12.salonlink.core.designsystem.component.BottomBarItem
import com.group12.salonlink.core.designsystem.component.SalonBottomBar

/**
 * A role's "home": a [Scaffold] with the bottom bar and an inner [NavHost] for the tabs.
 *
 * Customer, Staff and Owner all reuse this one composable and pass different [tabs]. Each
 * role can therefore only reach its own screens, which mirrors the role-based access in
 * the proposal (FR-01).
 *
 * @param tabs bottom-bar entries. The first one is the start screen.
 * @param graph declares the composable for each tab route.
 */
@Composable
fun RoleShell(
    tabs: List<BottomBarItem>,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    graph: NavGraphBuilder.() -> Unit,
) {
    // Re-composes whenever the user moves to another tab, so the bar highlight follows.
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // The bar shows only on the tab screens themselves. Screens pushed on top (e.g. the booking
    // flow) are full-screen, like their Figma frames.
    val onTabScreen = currentDestination == null ||
        tabs.any { item -> currentDestination.hierarchy.any { it.hasRoute(item.route::class) } }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (onTabScreen) SalonBottomBar(
                items = tabs,
                isSelected = { item ->
                    currentDestination?.hierarchy?.any { it.hasRoute(item.route::class) } == true
                },
                onItemClick = { item -> navController.navigateToTab(item.route) },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = tabs.first().route,
            modifier = Modifier.padding(innerPadding),
            builder = graph,
        )
    }
}

/**
 * Standard bottom-tab navigation (Android "Navigation with Compose" guidance):
 *  - popUpTo(start) + saveState: tabs don't pile up on the back stack;
 *  - launchSingleTop: tapping the current tab again doesn't open a copy;
 *  - restoreState: returning to a tab restores its scroll position and state.
 */
fun NavHostController.navigateToTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
