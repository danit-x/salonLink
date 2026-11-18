package com.group12.salonlink.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.group12.salonlink.feature.auth.SignInPlaceholderScreen
import com.group12.salonlink.feature.auth.WelcomeScreen
import com.group12.salonlink.feature.customer.CustomerShell
import com.group12.salonlink.feature.owner.OwnerShell
import com.group12.salonlink.feature.staff.StaffShell

/**
 * The app's top-level navigation graph: the map of every full-screen destination.
 *
 * Flow today (Phase 0):  Welcome → Sign in (preview) → Customer / Staff / Owner shell.
 * Phase 1 replaces the preview with real Firebase sign-in, and the role stored in the
 * user's profile decides which shell opens.
 */
@Composable
fun SalonLinkNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    /** Opens a role's home and clears auth screens, so Back doesn't return to sign-in. */
    fun openShell(shell: Any) = navController.navigate(shell) {
        popUpTo(WelcomeRoute) { inclusive = true }
    }

    /** Returns to Welcome and clears everything behind it. */
    fun signOut() = navController.navigate(WelcomeRoute) {
        popUpTo(0) { inclusive = true }
    }

    NavHost(
        navController = navController,
        startDestination = WelcomeRoute,
        modifier = modifier,
    ) {
        composable<WelcomeRoute> {
            WelcomeScreen(
                onCreateAccount = { navController.navigate(SignInRoute) },
                onSignIn = { navController.navigate(SignInRoute) },
            )
        }
        composable<SignInRoute> {
            SignInPlaceholderScreen(
                onBack = { navController.popBackStack() },
                onPreviewCustomer = { openShell(CustomerShellRoute) },
                onPreviewStaff = { openShell(StaffShellRoute) },
                onPreviewOwner = { openShell(OwnerShellRoute) },
            )
        }
        composable<CustomerShellRoute> { CustomerShell(onSignOut = ::signOut) }
        composable<StaffShellRoute> { StaffShell(onSignOut = ::signOut) }
        composable<OwnerShellRoute> { OwnerShell(onSignOut = ::signOut) }
    }
}
