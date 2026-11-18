package com.group12.salonlink.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

/**
 * Provides the app's [Clock] ("what time is it now?").
 *
 * WHY inject a clock instead of calling Instant.now(): availability depends on the current
 * time ("no slots in the past"). Tests pass a fixed clock, e.g. "Thursday 3 Dec 2026, 10:00",
 * so they give the same result every day they are run.
 */
@Module
@InstallIn(SingletonComponent::class)
object TimeModule {

    @Provides
    fun provideClock(): Clock = Clock.systemUTC()
}
