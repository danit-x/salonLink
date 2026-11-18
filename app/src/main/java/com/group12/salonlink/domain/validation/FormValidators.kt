package com.group12.salonlink.domain.validation

/**
 * Input checks for the auth forms, written as pure functions.
 *
 * WHY pure Kotlin (no android.util.Patterns): they run in plain JVM unit tests, and the
 * rules are written down in one place the examiner can read.
 *
 * Each function returns null when the input is valid, or the [FieldError] to show.
 */
object FormValidators {

    const val MIN_PASSWORD_LENGTH = 8
    private const val MAX_NAME_LENGTH = 60

    // Practical email check: something@something.tld, no spaces. Firebase does the final check.
    private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")

    // Sri Lankan mobile numbers: 07XXXXXXXX or +947XXXXXXXX (spaces and dashes are ignored).
    private val SL_MOBILE = Regex("^(?:\\+94|0)7\\d{8}$")

    fun name(value: String): FieldError? {
        val trimmed = value.trim()
        return when {
            trimmed.isEmpty() -> FieldError.REQUIRED
            trimmed.length > MAX_NAME_LENGTH -> FieldError.TOO_LONG
            else -> null
        }
    }

    fun email(value: String): FieldError? = when {
        value.isBlank() -> FieldError.REQUIRED
        !EMAIL.matches(value.trim()) -> FieldError.INVALID_EMAIL
        else -> null
    }

    /** Phone is optional; only a non-empty value is checked. */
    fun phone(value: String): FieldError? {
        val digits = normalisePhone(value)
        return if (digits.isEmpty() || SL_MOBILE.matches(digits)) null else FieldError.INVALID_PHONE
    }

    fun password(value: String): FieldError? = when {
        value.isEmpty() -> FieldError.REQUIRED
        value.length < MIN_PASSWORD_LENGTH -> FieldError.PASSWORD_TOO_SHORT
        else -> null
    }

    /** Sign-in only checks that a password was typed: the length rule is for new passwords. */
    fun passwordPresent(value: String): FieldError? = if (value.isEmpty()) FieldError.REQUIRED else null

    /** "077 123-4567" → "0771234567" */
    fun normalisePhone(value: String): String = value.filter { it.isDigit() || it == '+' }

    /** Emails are compared case-insensitively everywhere (invites are stored lower-case). */
    fun normaliseEmail(value: String): String = value.trim().lowercase()
}

/** Why a single form field is invalid. The UI maps each one to a message. */
enum class FieldError {
    REQUIRED,
    TOO_LONG,
    INVALID_EMAIL,
    INVALID_PHONE,
    PASSWORD_TOO_SHORT,
}
