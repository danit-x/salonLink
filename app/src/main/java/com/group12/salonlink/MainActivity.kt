package com.group12.salonlink

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.group12.salonlink.core.designsystem.theme.SalonLinkTheme
import com.group12.salonlink.core.navigation.SalonLinkNavHost
import dagger.hilt.android.AndroidEntryPoint

/**
 * The app's only Activity ("single-activity architecture"). Every screen is a
 * Composable swapped in by Navigation Compose inside [SalonLinkNavHost].
 *
 * @AndroidEntryPoint lets Hilt inject dependencies into this Activity and the
 * ViewModels created inside it.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate(): shows the branded splash, then switches theme.
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Draw behind the status and navigation bars (the Figma screens are full-bleed).
        // The light style keeps the system bar icons dark on our cream background.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )

        setContent {
            SalonLinkTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    SalonLinkNavHost()
                }
            }
        }
    }
}
