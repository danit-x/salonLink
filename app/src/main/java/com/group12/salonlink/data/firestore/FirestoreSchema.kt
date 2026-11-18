package com.group12.salonlink.data.firestore

/**
 * Names of Firestore collections and fields, all in one place.
 *
 * WHY constants: a typo like "user" vs "users" would compile fine and fail silently at
 * runtime. Keep these in sync with `firebase/firestore.rules`.
 */
object FirestoreSchema {

    // --- Collections ---
    const val USERS = "users"
    const val INVITES = "invites"

    // --- users/{uid} fields ---
    object UserFields {
        const val NAME = "name"
        const val EMAIL = "email"
        const val PHONE = "phone"
        const val ROLE = "role"
        const val SALON_ID = "salonId"
        const val STAFF_ID = "staffId"
        const val CREATED_AT = "createdAt"
    }

    // --- invites/{email} fields ---
    object InviteFields {
        const val ROLE = "role"
        const val SALON_ID = "salonId"
        const val STAFF_ID = "staffId"
    }
}
