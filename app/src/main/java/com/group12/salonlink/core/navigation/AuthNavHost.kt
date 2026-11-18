package com.group12.salonlink.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.group12.salonlink.feature.auth.WelcomeScreen
import com.group12.salonlink.feature.auth.register.RegisterRoute
import com.group12.salonlink.feature.auth.signin.SignInRoute

/**
 * Navigation graph for signed-out users: Welcome → Sign in ⇄ Register.
 *
 * There is no "go to home" call anywhere in here. When sign-in or registration succeeds,
 * [com.group12.salonlink.SalonLinkRoot] sees the new AuthState and replaces this whole
 * graph with the user's role home.
 */
@Composable
fun AuthNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    /** Switch between Sign in and Register without piling them up on the back stack. */
    fun switchTo(route: Any) = navController.navigate(route) {
        popUpTo(WelcomeRoute)
        launchSingleTop = true
    }

    NavHost(
        navController = navController,
        startDestination = WelcomeRoute,
        modifier = modifier,
    ) {
        composable<WelcomeRoute> {
            WelcomeScreen(
                onCreateAccount = { switchTo(RegisterRoute) },
                onSignIn = { switchTo(SignInRoute) },
            )
        }
        composable<SignInRoute> {
            SignInRoute(
                onBack = { navController.popBackStack() },
                onCreateAccount = { switchTo(RegisterRoute) },
            )
        }
        composable<RegisterRoute> {
            RegisterRoute(
                onBack = { navController.popBackStack() },
                onSignIn = { switchTo(SignInRoute) },
            )
        }
    }
}
