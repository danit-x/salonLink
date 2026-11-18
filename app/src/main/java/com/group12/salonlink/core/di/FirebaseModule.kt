package com.group12.salonlink.core.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    /**
     * Firestore keeps an offline cache on the device by default, so screens still show the
     * last known data without a network connection (proposal NFR-10).
     */
    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()
}
