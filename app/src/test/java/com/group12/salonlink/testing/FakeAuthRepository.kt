package com.group12.salonlink.testing

import com.group12.salonlink.domain.model.AuthError
import com.group12.salonlink.domain.model.AuthException
import com.group12.salonlink.domain.model.AuthState
import com.group12.salonlink.domain.model.Role
import com.group12.salonlink.domain.model.UserProfile
import com.group12.salonlink.domain.repository.AuthRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory stand-in for Firebase, used by ViewModel tests.
 *
 * WHY a hand-written fake instead of a mock: it behaves like a tiny real backend
 * (registered users, wrong passwords...), so tests read like real user stories.
 */
class FakeAuthRepository : AuthRepository {

    private val _authState = MutableStateFlow<AuthState>(AuthState.SignedOut)
    override val authState: StateFlow<AuthState> = _authState

    /** email → password of "existing" accounts. */
    val accounts = mutableMapOf<String, String>()

    /** When set, the next call fails with this error (e.g. NETWORK). */
    var nextError: AuthError? = null

    /** When set, calls wait for it to complete. Lets tests observe the "loading" state. */
    var gate: CompletableDeferred<Unit>? = null

    var signInCalls = 0
        private set
    var lastRegistered: Triple<String, String, String?>? = null // name, email, phone
        private set
    var resetEmailsSent = mutableListOf<String>()
        private set

    override suspend fun signIn(email: String, password: String) {
        signInCalls++
        gate?.await()
        nextError?.let { nextError = null; throw AuthException(it) }
        if (accounts[email.lowercase()] != password) throw AuthException(AuthError.INVALID_CREDENTIALS)
        _authState.value = AuthState.SignedIn(UserProfile("uid-1", "Test User", email, null, Role.CUSTOMER))
    }

    override suspend fun register(name: String, email: String, phone: String?, password: String) {
        gate?.await()
        nextError?.let { nextError = null; throw AuthException(it) }
        if (email.lowercase() in accounts) throw AuthException(AuthError.EMAIL_ALREADY_IN_USE)
        accounts[email.lowercase()] = password
        lastRegistered = Triple(name, email, phone)
        _authState.value = AuthState.SignedIn(UserProfile("uid-2", name, email, phone, Role.CUSTOMER))
    }

    override suspend fun sendPasswordReset(email: String) {
        nextError?.let { nextError = null; throw AuthException(it) }
        resetEmailsSent += email
    }

    override fun signOut() {
        _authState.value = AuthState.SignedOut
    }
}
