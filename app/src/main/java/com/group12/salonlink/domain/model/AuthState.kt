package com.group12.salonlink.domain.model

/**
 * Who is using the app right now. The root of the UI switches on this value
 * (see `SalonLinkRoot`), so every screen change after sign-in or sign-out follows
 * automatically from one source of truth.
 */
sealed interface AuthState {

    /** Still finding out (app start). The splash screen stays visible meanwhile. */
    data object Loading : AuthState

    /** Nobody is signed in: show Welcome / Sign in / Register. */
    data object SignedOut : AuthState

    /** Signed in and the Firestore profile is loaded: open the home screen for [user]'s role. */
    data class SignedIn(val user: UserProfile) : AuthState

    /**
     * Signed in to Firebase Auth, but no `users/{uid}` document exists (e.g. it was deleted
     * in the console). The UI explains this and offers to sign out instead of crashing.
     */
    data class ProfileMissing(val uid: String) : AuthState
}
