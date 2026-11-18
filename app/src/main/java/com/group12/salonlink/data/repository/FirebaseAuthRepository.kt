package com.group12.salonlink.data.repository

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.group12.salonlink.data.firestore.FirestoreSchema
import com.group12.salonlink.data.firestore.FirestoreSchema.UserFields
import com.group12.salonlink.data.firestore.inviteFromMap
import com.group12.salonlink.data.firestore.toFirestoreMap
import com.group12.salonlink.data.firestore.userProfileFromMap
import com.group12.salonlink.domain.model.AuthError
import com.group12.salonlink.domain.model.AuthException
import com.group12.salonlink.domain.model.AuthState
import com.group12.salonlink.domain.model.Invite
import com.group12.salonlink.domain.model.Role
import com.group12.salonlink.domain.model.UserProfile
import com.group12.salonlink.domain.repository.AuthRepository
import com.group12.salonlink.domain.validation.FormValidators
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

/**
 * [AuthRepository] backed by Firebase Authentication (accounts and passwords) and Cloud
 * Firestore (`users/{uid}` profiles holding the role).
 *
 * Passwords are never stored or logged by the app: they go straight to Firebase Auth.
 */
@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) : AuthRepository {

    /**
     * True while [register] runs. Between "account created" and "profile saved" there's a
     * moment when the user is signed in but has no profile yet. Without this flag the UI
     * would flash the "profile missing" screen during every registration.
     */
    private val registering = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val authState: Flow<AuthState> =
        firebaseUserFlow()
            // When the signed-in user changes, stop watching the old profile and watch the new one.
            .flatMapLatest { user -> if (user == null) flowOf(AuthState.SignedOut) else profileFlow(user.uid) }
            .combine(registering) { state, isRegistering ->
                if (isRegistering && state is AuthState.ProfileMissing) AuthState.Loading else state
            }
            .distinctUntilChanged()

    // ---------------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------------

    override suspend fun signIn(email: String, password: String) = runAuth {
        auth.signInWithEmailAndPassword(FormValidators.normaliseEmail(email), password).await()
        Unit
    }

    override suspend fun register(name: String, email: String, phone: String?, password: String): Unit = runAuth {
        val emailKey = FormValidators.normaliseEmail(email)
        registering.value = true
        try {
            // 1) Create the Firebase Auth account (this also signs the user in).
            val user = auth.createUserWithEmailAndPassword(emailKey, password).await().user
                ?: throw AuthException(AuthError.UNKNOWN)

            try {
                // 2) Staff and owners are pre-invited by email. Everyone else is a customer.
                val invite = findInvite(emailKey)
                val profile = UserProfile(
                    uid = user.uid,
                    name = name.trim(),
                    email = emailKey,
                    phone = phone?.let(FormValidators::normalisePhone)?.ifBlank { null },
                    role = invite?.role ?: Role.CUSTOMER,
                    salonId = invite?.salonId,
                    staffId = invite?.staffId,
                )
                // 3) Save the profile. Security rules re-check that the role matches the invite.
                val data = profile.toFirestoreMap() + (UserFields.CREATED_AT to FieldValue.serverTimestamp())
                db.collection(FirestoreSchema.USERS).document(user.uid).set(data).await()

                // 4) Nice-to-have: show the name in the Firebase console's user list.
                user.updateProfile(userProfileChangeRequest { displayName = profile.name }).await()
            } catch (e: Exception) {
                // WHY roll back: an account without a profile has no role and can't use the app.
                // Deleting it lets the user simply try registering again.
                rollBack(user)
                throw e
            }
        } finally {
            registering.value = false
        }
    }

    override suspend fun sendPasswordReset(email: String) = runAuth {
        auth.sendPasswordResetEmail(FormValidators.normaliseEmail(email)).await()
        Unit
    }

    override fun signOut() = auth.signOut()

    // ---------------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------------

    /** Turns Firebase's listener callback into a Flow of the current user (null = signed out). */
    private fun firebaseUserFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) } // fires once immediately
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) } // no leaks when nobody listens
    }

    /** Live view of `users/{uid}`: role changes made in the console apply without restarting. */
    private fun profileFlow(uid: String): Flow<AuthState> = callbackFlow {
        val registration = db.collection(FirestoreSchema.USERS).document(uid)
            .addSnapshotListener { snapshot, error ->
                val state = when {
                    // Errors also happen during sign-out (access is revoked first), so check who's signed in.
                    error != null -> if (auth.currentUser == null) AuthState.SignedOut else AuthState.ProfileMissing(uid)
                    // Cached "doesn't exist" may be stale on a fresh install, so wait for the server.
                    snapshot == null || (!snapshot.exists() && snapshot.metadata.isFromCache) -> AuthState.Loading
                    !snapshot.exists() -> AuthState.ProfileMissing(uid)
                    else -> AuthState.SignedIn(userProfileFromMap(uid, snapshot.data.orEmpty()))
                }
                trySend(state)
            }
        awaitClose { registration.remove() }
    }

    private suspend fun findInvite(emailKey: String): Invite? {
        val snapshot = db.collection(FirestoreSchema.INVITES).document(emailKey).get().await()
        return if (snapshot.exists()) inviteFromMap(emailKey, snapshot.data.orEmpty()) else null
    }

    private suspend fun rollBack(user: FirebaseUser) {
        try {
            user.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            auth.signOut() // could not delete: at least don't leave a half-registered session
        }
    }

    /** Runs [block] and converts any Firebase exception into an [AuthException]. */
    private suspend fun <T> runAuth(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e // never swallow coroutine cancellation
    } catch (e: AuthException) {
        throw e
    } catch (e: Exception) {
        throw AuthException(e.toAuthError(), e)
    }

    private fun Exception.toAuthError(): AuthError = when (this) {
        // Order matters: WeakPassword is a subclass of InvalidCredentials.
        is FirebaseAuthWeakPasswordException -> AuthError.WEAK_PASSWORD
        is FirebaseAuthUserCollisionException -> AuthError.EMAIL_ALREADY_IN_USE
        is FirebaseAuthInvalidCredentialsException, is FirebaseAuthInvalidUserException -> AuthError.INVALID_CREDENTIALS
        is FirebaseNetworkException -> AuthError.NETWORK
        is FirebaseTooManyRequestsException -> AuthError.TOO_MANY_ATTEMPTS
        is FirebaseFirestoreException -> when (code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> AuthError.PERMISSION_DENIED
            FirebaseFirestoreException.Code.UNAVAILABLE -> AuthError.NETWORK
            else -> AuthError.UNKNOWN
        }
        else -> AuthError.UNKNOWN
    }
}
