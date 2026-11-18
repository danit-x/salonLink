package com.group12.salonlink.domain.model

/**
 * Every way sign-in / registration can fail, in words the UI can explain.
 *
 * WHY our own type instead of passing Firebase exceptions up: ViewModels stay independent
 * of the Firebase SDK, and each case maps to one friendly, translatable message.
 */
enum class AuthError {
    INVALID_CREDENTIALS,   // wrong email or password
    EMAIL_ALREADY_IN_USE,
    WEAK_PASSWORD,
    NETWORK,               // offline or timed out
    TOO_MANY_ATTEMPTS,     // Firebase temporarily blocked this device
    PERMISSION_DENIED,     // Firestore security rules rejected the write
    UNKNOWN,
}

/** Exception wrapper so a repository can `throw AuthException(AuthError.X)` and callers can `catch` it. */
class AuthException(val error: AuthError, cause: Throwable? = null) : Exception(error.name, cause)
