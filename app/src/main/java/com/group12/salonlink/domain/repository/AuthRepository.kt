package com.group12.salonlink.domain.repository

import com.group12.salonlink.domain.model.AuthState
import kotlinx.coroutines.flow.Flow

/**
 * Everything the app needs from "accounts", as an interface.
 *
 * WHY an interface: ViewModels depend on this contract, not on Firebase. In unit tests we
 * pass a small fake implementation, so tests run in milliseconds with no network.
 * The real implementation is `FirebaseAuthRepository` (data layer), wired up by Hilt.
 *
 * All suspend functions throw [com.group12.salonlink.domain.model.AuthException] on failure.
 */
interface AuthRepository {

    /** Emits the current [AuthState] immediately and again on every sign-in, sign-out or profile change. */
    val authState: Flow<AuthState>

    suspend fun signIn(email: String, password: String)

    /**
     * Creates the account and the `users/{uid}` profile. If an invite exists for [email],
     * the user gets the invited STAFF/OWNER role; otherwise they become a CUSTOMER.
     */
    suspend fun register(name: String, email: String, phone: String?, password: String)

    suspend fun sendPasswordReset(email: String)

    fun signOut()
}
