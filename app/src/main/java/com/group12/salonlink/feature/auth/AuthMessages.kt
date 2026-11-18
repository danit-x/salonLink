package com.group12.salonlink.feature.auth

import androidx.annotation.StringRes
import com.group12.salonlink.R
import com.group12.salonlink.domain.model.AuthError
import com.group12.salonlink.domain.validation.FieldError

/*
 * Maps domain errors to user-facing text.
 *
 * WHY here (UI layer): the domain layer says *what* went wrong, and the UI decides *how*
 * to say it. That keeps strings in resources, so they can be translated, and keeps
 * ViewModels free of Android Context.
 */

@StringRes
fun AuthError.messageRes(): Int = when (this) {
    AuthError.INVALID_CREDENTIALS -> R.string.auth_error_invalid_credentials
    AuthError.EMAIL_ALREADY_IN_USE -> R.string.auth_error_email_in_use
    AuthError.WEAK_PASSWORD -> R.string.auth_error_weak_password
    AuthError.NETWORK -> R.string.auth_error_network
    AuthError.TOO_MANY_ATTEMPTS -> R.string.auth_error_too_many_attempts
    AuthError.PERMISSION_DENIED -> R.string.auth_error_permission_denied
    AuthError.UNKNOWN -> R.string.auth_error_unknown
}

@StringRes
fun FieldError.messageRes(): Int = when (this) {
    FieldError.REQUIRED -> R.string.field_error_required
    FieldError.TOO_LONG -> R.string.field_error_too_long
    FieldError.INVALID_EMAIL -> R.string.field_error_invalid_email
    FieldError.INVALID_PHONE -> R.string.field_error_invalid_phone
    FieldError.PASSWORD_TOO_SHORT -> R.string.field_error_password_too_short
}
