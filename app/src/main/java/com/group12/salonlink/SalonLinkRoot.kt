package com.group12.salonlink

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.group12.salonlink.core.designsystem.component.PlaceholderScreen
import com.group12.salonlink.core.navigation.AuthNavHost
import com.group12.salonlink.domain.model.AuthState
import com.group12.salonlink.domain.model.Role
import com.group12.salonlink.feature.customer.CustomerShell
import com.group12.salonlink.feature.owner.OwnerShell
import com.group12.salonlink.feature.staff.StaffShell

/**
 * Top of the UI tree: picks which world to show from the [AuthState].
 *
 *   SignedOut       → Welcome / Sign in / Register
 *   SignedIn(role)  → that role's home (Customer, Staff or Owner)
 *   ProfileMissing  → explanation + sign out
 *
 * WHY switch here instead of navigating: after sign-out, the old role's screens are simply
 * removed from the tree. Back can never return to a signed-in screen, and one role can
 * never reach another role's screens (FR-01).
 */
@Composable
fun SalonLinkRoot(viewModel: RootViewModel) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()

    // The key only changes when the *kind* of screen changes, so the cross-fade runs on
    // sign-in/out but not on small profile edits.
    Crossfade(targetState = authState.screenKey(), label = "root") { key ->
        when (key) {
            ScreenKey.Loading -> Box(Modifier.fillMaxSize()) // splash screen is still covering this
            ScreenKey.Auth -> AuthNavHost()
            is ScreenKey.Home -> {
                val user = (authState as? AuthState.SignedIn)?.user ?: return@Crossfade
                when (key.role) {
                    Role.CUSTOMER -> CustomerShell(user = user, onSignOut = viewModel::signOut)
                    Role.STAFF -> StaffShell(user = user, onSignOut = viewModel::signOut)
                    Role.OWNER -> OwnerShell(user = user, onSignOut = viewModel::signOut)
                }
            }
            ScreenKey.ProfileMissing -> PlaceholderScreen(
                title = stringResource(R.string.profile_missing_title),
                message = stringResource(R.string.profile_missing_message),
                badge = stringResource(R.string.profile_missing_badge),
                icon = R.drawable.ic_user_round,
                actionLabel = stringResource(R.string.sign_out),
                onAction = viewModel::signOut,
            )
        }
    }
}

/** Which screen tree to show. A different user with the same role keeps the same key. */
private sealed interface ScreenKey {
    data object Loading : ScreenKey
    data object Auth : ScreenKey
    data class Home(val role: Role, val uid: String) : ScreenKey
    data object ProfileMissing : ScreenKey
}

private fun AuthState.screenKey(): ScreenKey = when (this) {
    AuthState.Loading -> ScreenKey.Loading
    AuthState.SignedOut -> ScreenKey.Auth
    is AuthState.SignedIn -> ScreenKey.Home(user.role, user.uid)
    is AuthState.ProfileMissing -> ScreenKey.ProfileMissing
}
