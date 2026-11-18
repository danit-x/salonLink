package com.group12.salonlink.core.di

import com.group12.salonlink.data.repository.FirebaseAuthRepository
import com.group12.salonlink.domain.repository.AuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Tells Hilt which implementation to use for each repository interface.
 *
 * WHY @Binds: whoever asks for an [AuthRepository] gets the [FirebaseAuthRepository].
 * Swapping the backend later (or using a fake in tests) changes only this one line.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository
}
