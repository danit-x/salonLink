package com.group12.salonlink.domain.model

/**
 * The three kinds of user in SalonLink (proposal §6, FR-01).
 * The role decides which home screen and which data a user can reach.
 */
enum class Role {
    CUSTOMER,
    STAFF,
    OWNER;

    companion object {
        /** Safe parse: unknown or missing values fall back to the least-privileged role. */
        fun fromValue(value: String?): Role = entries.firstOrNull { it.name == value } ?: CUSTOMER
    }
}

/**
 * A signed-in person's profile, stored in Firestore at `users/{uid}`.
 *
 * This is a *domain* model: plain Kotlin with no Firebase types, so ViewModels and tests
 * never depend on the Firebase SDK.
 *
 * @property salonId the salon a STAFF/OWNER belongs to. Always null for customers.
 * @property staffId the staff record (`salons/{salonId}/staff/{staffId}`) for a STAFF user.
 */
data class UserProfile(
    val uid: String,
    val name: String,
    val email: String,
    val phone: String?,
    val role: Role,
    val salonId: String? = null,
    val staffId: String? = null,
) {
    /** "Kavindu Perera" → "Kavindu", used in greetings like "Good afternoon, Kavindu". */
    val firstName: String get() = name.trim().substringBefore(' ')
}

/**
 * An invitation that grants a STAFF or OWNER role. Stored at `invites/{lower-case email}`.
 * Owners create staff invites, and the seed script creates the first owner invite.
 */
data class Invite(
    val email: String,
    val role: Role,
    val salonId: String,
    val staffId: String? = null,
)
