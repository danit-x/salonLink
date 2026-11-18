package com.group12.salonlink.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Qualifier for the background thread pool used for disk / network / heavy CPU work.
 *
 * Usage: `class FooRepository @Inject constructor(@IoDispatcher private val io: CoroutineDispatcher)`
 *
 * WHY inject dispatchers: in unit tests we swap in a test dispatcher so coroutines run
 * instantly and deterministically, instead of on real background threads.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** Qualifier for CPU-heavy work (e.g. computing free slots for a whole month). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
