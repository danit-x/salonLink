package com.group12.salonlink.core.di

import com.group12.salonlink.data.repository.FirebaseAuthRepository
import com.group12.salonlink.data.repository.FirestoreBookingRepository
import com.group12.salonlink.data.repository.FirestoreSalonRepository
import com.group12.salonlink.domain.repository.AuthRepository
import com.group12.salonlink.domain.repository.BookingRepository
import com.group12.salonlink.domain.repository.SalonRepository
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

    @Binds
    @Singleton
    abstract fun bindSalonRepository(impl: FirestoreSalonRepository): SalonRepository

    @Binds
    @Singleton
    abstract fun bindBookingRepository(impl: FirestoreBookingRepository): BookingRepository

    @Binds
    @Singleton
    abstract fun bindNotificationRepository(impl: com.group12.salonlink.data.repository.FirestoreNotificationRepository):
        com.group12.salonlink.domain.repository.NotificationRepository

    @Binds
    @Singleton
    abstract fun bindSalonAdminRepository(impl: com.group12.salonlink.data.repository.FirestoreSalonAdminRepository):
        com.group12.salonlink.domain.repository.SalonAdminRepository

    @Binds
    @Singleton
    abstract fun bindReminderScheduler(impl: com.group12.salonlink.data.reminder.WorkManagerReminderScheduler):
        com.group12.salonlink.domain.repository.ReminderScheduler
}
