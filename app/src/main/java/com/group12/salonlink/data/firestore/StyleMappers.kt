package com.group12.salonlink.data.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Blob
import com.group12.salonlink.data.firestore.FirestoreSchema.PhotoFields
import com.group12.salonlink.data.firestore.FirestoreSchema.StyleFields
import com.group12.salonlink.domain.model.PhotoLabel
import com.group12.salonlink.domain.model.StyleProfile
import com.group12.salonlink.domain.model.StylePhoto
import com.group12.salonlink.domain.model.StyleTag

/** Domain → Firestore (the server timestamp is added by the repository). */
fun StyleProfile.toFirestoreMap(): Map<String, Any?> = mapOf(
    StyleFields.TAGS to tags.sortedBy { it.ordinal }.map { it.name },
    StyleFields.NOTE to note.trim().take(StyleProfile.MAX_NOTE),
    StyleFields.PREFERRED_STAFF_ID to preferredStaffId,
    StyleFields.SHARE_WITH_BARBER to shareWithBarber,
    StyleFields.PHOTO_CONSENT to photoConsent,
)

/** Firestore → domain. Missing or odd fields fall back to "not set". */
fun styleProfileFromMap(data: Map<String, Any?>?): StyleProfile {
    if (data == null) return StyleProfile()
    return StyleProfile(
        tags = StyleTag.parse((data[StyleFields.TAGS] as? List<*>).orEmpty().filterIsInstance<String>()),
        note = data[StyleFields.NOTE] as? String ?: "",
        preferredStaffId = data[StyleFields.PREFERRED_STAFF_ID] as? String,
        shareWithBarber = data[StyleFields.SHARE_WITH_BARBER] as? Boolean ?: false,
        photoConsent = data[StyleFields.PHOTO_CONSENT] as? Boolean ?: false,
    )
}

/** Firestore → domain; null if the document isn't a valid photo. */
fun stylePhotoFromMap(id: String, data: Map<String, Any?>): StylePhoto? {
    val blob = data[PhotoFields.JPEG] as? Blob ?: return null
    return StylePhoto(
        id = id,
        jpeg = blob.toBytes(),
        label = PhotoLabel.entries.firstOrNull { it.name == data[PhotoFields.LABEL] } ?: PhotoLabel.INSPIRATION,
        createdAt = (data[PhotoFields.CREATED_AT] as? Timestamp)?.toInstant(),
    )
}
