package com.group12.salonlink

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.group12.salonlink.core.notifications.SystemNotifier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application class: the first SalonLink code that runs when the process starts.
 *
 * @HiltAndroidApp makes Hilt generate the app-wide dependency container. Every
 * @AndroidEntryPoint activity and @HiltViewModel can then receive dependencies (Firebase,
 * repositories...) through constructor injection instead of creating them by hand.
 *
 * Firebase needs no code here: the google-services plugin turns app/google-services.json
 * into resources, and Firebase initialises itself automatically at startup.
 *
 * WHY Configuration.Provider: WorkManager must create our reminder Worker through Hilt so it
 * gets the same (emulator-aware) Firestore as the rest of the app. The default WorkManager
 * initializer is switched off in AndroidManifest.xml for that reason.
 */
@HiltAndroidApp
class SalonLinkApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notifier: SystemNotifier

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    /** Same language for text made outside screens (phone notifications, reminders). */
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(com.group12.salonlink.core.locale.LanguageStore.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        notifier.createChannels()
    }
}
