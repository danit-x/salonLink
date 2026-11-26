package com.group12.salonlink

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.firebase.appdistribution.FirebaseAppDistribution
import com.group12.salonlink.core.designsystem.theme.SalonLinkTheme
import com.group12.salonlink.domain.model.AuthState
import dagger.hilt.android.AndroidEntryPoint

/**
 * The app's only Activity ("single-activity architecture"). Every screen is a
 * Composable chosen by [SalonLinkRoot].
 *
 * @AndroidEntryPoint lets Hilt inject dependencies into this Activity and the
 * ViewModels created inside it.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val rootViewModel: RootViewModel by viewModels()

    /** Applies the language picked on "My style" (see [LanguageStore]). */
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.group12.salonlink.core.locale.LanguageStore.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate(): shows the branded splash screen.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Keep the splash up until we know whether someone is signed in. This avoids a
        // flash of the Welcome screen for users who are already logged in.
        splash.setKeepOnScreenCondition { rootViewModel.authState.value is AuthState.Loading }

        // Draw behind the status and navigation bars (the Figma screens are full-bleed).
        // The light style keeps the system bar icons dark on our cream background.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )

        // Tester builds only: ask Firebase App Distribution whether a newer build exists. If so,
        // the SDK shows its own "New version available → Update" dialog, then downloads and
        // installs the APK. Checked once per launch, not on every rotation.
        if (savedInstanceState == null && BuildConfig.BUILD_TYPE == "beta") checkForTesterUpdate()

        setContent {
            SalonLinkTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    SalonLinkRoot(viewModel = rootViewModel)
                }
            }
        }
    }

    /**
     * Shows App Distribution's update dialog when a newer tester build is available.
     * The first time, the SDK asks the tester to sign in with the Google account that was invited.
     *
     * WHY failures are only logged: an update check must never block the app. The usual causes
     * are "no network" or "tester cancelled sign-in", and the next launch simply tries again.
     */
    private fun checkForTesterUpdate() {
        FirebaseAppDistribution.getInstance()
            .updateIfNewReleaseAvailable()
            .addOnFailureListener { e -> Log.w(TAG, "Tester update check failed", e) }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
