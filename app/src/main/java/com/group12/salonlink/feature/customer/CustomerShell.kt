// Covers: customer navigation · Figma: bottom navigation (Explore / Bookings / Concierge / My style)
package com.group12.salonlink.feature.customer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.group12.salonlink.R
import com.group12.salonlink.core.designsystem.component.BottomBarItem
import com.group12.salonlink.core.designsystem.component.PlaceholderScreen
import com.group12.salonlink.core.navigation.BarberProfileRoute
import com.group12.salonlink.core.navigation.BookingConfirmationRoute
import com.group12.salonlink.core.navigation.BookingFlowRoute
import com.group12.salonlink.core.navigation.BookingsRoute
import com.group12.salonlink.core.navigation.ConciergeRoute
import com.group12.salonlink.core.navigation.DateTimeRoute
import com.group12.salonlink.core.navigation.ExploreRoute
import com.group12.salonlink.core.navigation.MyStyleRoute
import com.group12.salonlink.core.navigation.RoleShell
import com.group12.salonlink.core.navigation.ServiceSelectionRoute
import com.group12.salonlink.core.navigation.navigateToTab
import com.group12.salonlink.domain.model.UserProfile
import com.group12.salonlink.feature.account.AccountSummary
import com.group12.salonlink.feature.customer.barber.BarberProfileScreen
import com.group12.salonlink.feature.customer.barber.BarberProfileViewModel
import com.group12.salonlink.feature.customer.booking.BookingFlowEvent
import com.group12.salonlink.feature.customer.booking.BookingFlowViewModel
import com.group12.salonlink.feature.customer.booking.DateTimeScreen
import com.group12.salonlink.feature.customer.booking.ServiceSelectionScreen
import com.group12.salonlink.feature.customer.booking.confirmation.BookingConfirmationScreen
import com.group12.salonlink.feature.customer.booking.confirmation.BookingConfirmationViewModel
import com.group12.salonlink.feature.customer.discovery.DiscoveryActions
import com.group12.salonlink.feature.customer.discovery.DiscoveryScreen
import com.group12.salonlink.feature.customer.discovery.DiscoveryViewModel

/** The four customer tabs, in the same order and with the same icons as Figma. */
private val customerTabs = listOf(
    BottomBarItem(ExploreRoute, R.string.tab_explore, R.drawable.ic_compass),
    BottomBarItem(BookingsRoute, R.string.tab_bookings, R.drawable.ic_calendar_days),
    BottomBarItem(ConciergeRoute, R.string.tab_concierge, R.drawable.ic_sparkles),
    BottomBarItem(MyStyleRoute, R.string.tab_my_style, R.drawable.ic_user_round),
)

/**
 * Customer home with its bottom navigation, plus the booking journey pushed on top of it:
 *
 * ```
 * Explore ─▶ Barber profile ─▶ [ Services ─▶ Date & time ] ─▶ Confirmation
 *                               └──── BookingFlowRoute ────┘
 * ```
 * Placeholders are swapped for real screens in the phase shown on their badge.
 *
 * @param user the signed-in customer (always role CUSTOMER here, guaranteed by SalonLinkRoot).
 */
@Composable
fun CustomerShell(user: UserProfile, onSignOut: () -> Unit) {
    val navController = rememberNavController()
    RoleShell(tabs = customerTabs, navController = navController) {
        composable<ExploreRoute> { DiscoveryRoute(navController) }
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
        bookingJourney(navController)
    }
}

@Composable
private fun DiscoveryRoute(navController: NavHostController, viewModel: DiscoveryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DiscoveryScreen(
        state = state,
        onEvent = viewModel::onEvent,
        actions = DiscoveryActions(
            onBarberClick = { navController.navigate(BarberProfileRoute(it.salon.id, it.staff.id)) },
            onBook = { navController.navigate(BookingFlowRoute(it.salon.id, it.staff.id)) },
            onBookAgain = { last ->
                navController.navigate(BookingFlowRoute(last.salonId, last.staffId, last.services.map { it.serviceId }))
            },
            onOpenBooking = { navController.navigate(BookingConfirmationRoute(it.salonId, it.id)) },
            onOpenConcierge = { navController.navigateToTab(ConciergeRoute) },
        ),
    )
}

/** Barber profile, the 3-step flow (nested graph) and the confirmation. */
private fun NavGraphBuilder.bookingJourney(navController: NavHostController) {
    composable<BarberProfileRoute> { entry ->
        val args = entry.toRoute<BarberProfileRoute>()
        // Assisted injection: Hilt builds the ViewModel and we hand it the route's arguments.
        val viewModel = hiltViewModel<BarberProfileViewModel, BarberProfileViewModel.Factory>(
            creationCallback = { it.create(args) },
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        BarberProfileScreen(
            state = state,
            onBack = { navController.popBackStack() },
            onBook = { navController.navigate(BookingFlowRoute(args.salonId, args.staffId)) },
        )
    }

    navigation<BookingFlowRoute>(startDestination = ServiceSelectionRoute) {
        composable<ServiceSelectionRoute> { entry ->
            val viewModel = bookingFlowViewModel(navController, entry)
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            ServiceSelectionScreen(
                state = state,
                onEvent = viewModel::onEvent,
                onBack = { navController.popBackStack() },
                onContinue = { navController.navigate(DateTimeRoute) },
            )
        }
        composable<DateTimeRoute> { entry ->
            val viewModel = bookingFlowViewModel(navController, entry)
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val flowArgs = remember(entry) { navController.getBackStackEntry<BookingFlowRoute>().toRoute<BookingFlowRoute>() }
            // Booking saved → show the confirmation. The whole flow is removed from the back
            // stack, so "back" from the confirmation can't reopen a finished booking form.
            LaunchedEffect(state.confirmedBookingId) {
                val bookingId = state.confirmedBookingId ?: return@LaunchedEffect
                viewModel.onEvent(BookingFlowEvent.ConfirmationShown)
                navController.navigate(BookingConfirmationRoute(flowArgs.salonId, bookingId, justBooked = true)) {
                    popUpTo<ExploreRoute>()
                }
            }
            DateTimeScreen(
                state = state,
                onEvent = viewModel::onEvent,
                onBack = { navController.popBackStack() },
            )
        }
    }

    composable<BookingConfirmationRoute> { entry ->
        val args = entry.toRoute<BookingConfirmationRoute>()
        val viewModel = hiltViewModel<BookingConfirmationViewModel, BookingConfirmationViewModel.Factory>(
            creationCallback = { it.create(args) },
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        BookingConfirmationScreen(
            state = state,
            onBack = { navController.popBackStack() },
            onViewBookings = { navController.navigateToTab(BookingsRoute) },
        )
    }
}

/**
 * The ONE [BookingFlowViewModel] shared by both steps. It is stored in the back-stack entry of
 * the nested [BookingFlowRoute] graph (not of each screen), so it lives exactly as long as the
 * flow: going back from Date & time keeps the ticked services; leaving the flow clears them.
 */
@Composable
private fun bookingFlowViewModel(navController: NavHostController, entry: NavBackStackEntry): BookingFlowViewModel {
    val flowEntry = remember(entry) { navController.getBackStackEntry<BookingFlowRoute>() }
    return hiltViewModel<BookingFlowViewModel, BookingFlowViewModel.Factory>(
        viewModelStoreOwner = flowEntry,
        creationCallback = { it.create(flowEntry.toRoute<BookingFlowRoute>()) },
    )
}
