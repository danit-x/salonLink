package com.group12.salonlink.data.firestore

import com.group12.salonlink.data.firestore.FirestoreSchema.InviteFields
import com.group12.salonlink.data.firestore.FirestoreSchema.UserFields
import com.group12.salonlink.domain.model.Invite
import com.group12.salonlink.domain.model.Role
import com.group12.salonlink.domain.model.UserProfile

/*
 * Conversions between Firestore documents (Map<String, Any?>) and domain models.
 *
 * WHY explicit maps instead of Firestore's automatic toObject():
 *  - field names are visible here and match the security rules exactly;
 *  - no reflection, so R8 shrinking in release builds can't break it;
 *  - unknown or corrupt values fall back to safe defaults instead of crashing.
 * These functions are pure, so they are unit-tested (UserMappersTest).
 */

/** Domain → Firestore. The server adds `createdAt`; it is not part of the domain model. */
fun UserProfile.toFirestoreMap(): Map<String, Any?> = mapOf(
    UserFields.NAME to name,
    UserFields.EMAIL to email,
    UserFields.PHONE to phone,
    UserFields.ROLE to role.name,
    UserFields.SALON_ID to salonId,
    UserFields.STAFF_ID to staffId,
)

/** Firestore → domain. [uid] is the document id. */
fun userProfileFromMap(uid: String, data: Map<String, Any?>): UserProfile = UserProfile(
    uid = uid,
    name = data[UserFields.NAME] as? String ?: "",
    email = data[UserFields.EMAIL] as? String ?: "",
    phone = data[UserFields.PHONE] as? String,
    role = Role.fromValue(data[UserFields.ROLE] as? String),
    salonId = data[UserFields.SALON_ID] as? String,
    staffId = data[UserFields.STAFF_ID] as? String,
)

/** Firestore → domain. Returns null if the invite is incomplete (it then grants nothing). */
fun inviteFromMap(email: String, data: Map<String, Any?>): Invite? {
    val salonId = data[InviteFields.SALON_ID] as? String ?: return null
    val role = when (data[InviteFields.ROLE] as? String) {
        Role.STAFF.name -> Role.STAFF
        Role.OWNER.name -> Role.OWNER
        else -> return null // an invite can never grant CUSTOMER (pointless) or unknown roles
    }
    return Invite(email = email, role = role, salonId = salonId, staffId = data[InviteFields.STAFF_ID] as? String)
}
