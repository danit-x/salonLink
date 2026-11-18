package com.group12.salonlink.core.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.group12.salonlink.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Tells Hilt how to create the Firebase SDK objects.
 *
 * WHY inject instead of calling FirebaseAuth.getInstance() inside classes:
 *  - one place to configure Firebase (e.g. point at the local Emulator for tests);
 *  - repositories can be unit-tested by passing a fake/mock instead of the real SDK.
 *
 * @InstallIn(SingletonComponent) + @Singleton: one shared instance for the whole app.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    /**
     * Where the local Firebase emulators are reached from the phone. Ports must match `firebase.json`.
     *
     * WHY 127.0.0.1 and not the usual 10.0.2.2: Android 17 blocks apps from reaching local-network
     * addresses (10.x.x.x, 192.168.x.x) without the ACCESS_LOCAL_NETWORK permission. Loopback is
     * exempt, so we forward the ports with `adb reverse tcp:9099 tcp:9099` and `adb reverse
     * tcp:8085 tcp:8085`. That works on the emulator AND on a real phone over USB.
     */
    private const val EMULATOR_HOST = "127.0.0.1"
    private const val AUTH_EMULATOR_PORT = 9099
    private const val FIRESTORE_EMULATOR_PORT = 8085

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance().apply {
        if (BuildConfig.USE_FIREBASE_EMULATORS) useEmulator(EMULATOR_HOST, AUTH_EMULATOR_PORT)
    }

    /**
     * Firestore keeps an offline cache on the device by default, so screens still show the
     * last known data without a network connection (proposal NFR-10).
     */
    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance().apply {
        if (BuildConfig.USE_FIREBASE_EMULATORS) useEmulator(EMULATOR_HOST, FIRESTORE_EMULATOR_PORT)
    }
}
