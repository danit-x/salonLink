package com.group12.salonlink

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class: the first SalonLink code that runs when the process starts.
 *
 * @HiltAndroidApp makes Hilt generate the app-wide dependency container. Every
 * @AndroidEntryPoint activity and @HiltViewModel can then receive dependencies (Firebase,
 * repositories...) through constructor injection instead of creating them by hand.
 *
 * Firebase needs no code here: the google-services plugin turns app/google-services.json
 * into resources, and Firebase initialises itself automatically at startup.
 */
@HiltAndroidApp
class SalonLinkApp : Application()
