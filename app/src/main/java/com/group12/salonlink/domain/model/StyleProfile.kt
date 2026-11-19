package com.group12.salonlink.domain.model

import java.time.Instant

/**
 * Hair-style words a customer can pick on "My style" (proposal FR-12, US-10).
 *
 * @property keywords lower-case words that, found in a barber's specialties, mean the barber
 *   does this style ("Curly Hair" matches CURLY). Used by [com.group12.salonlink.domain.style.BarberMatcher].
 */
enum class StyleTag(val keywords: List<String>) {
    FADE(listOf("fade", "skin")),
    CURLY(listOf("curly", "texture", "wave")),
    COLOUR(listOf("colour", "color", "highlight")),
    BEARD(listOf("beard", "shave")),
    LONG_HAIR(listOf("long")),
    TREATMENT(listOf("treatment", "care", "scalp")),
    CLASSIC(listOf("classic", "scissor", "crop")),
    ;

    companion object {
        /** Unknown names (e.g. a tag removed in a later version) are skipped, never a crash. */
        fun parse(names: Collection<String>): Set<StyleTag> =
            names.mapNotNull { n -> entries.firstOrNull { it.name == n } }.toSet()
    }
}

/**
 * The customer's style profile, stored at `users/{uid}/style/profile` (readable only by them).
 *
 * @property note free text for the barber ("keep the length on top, low fade").
 * @property shareWithBarber consent: when true, new bookings carry the tags and note, so the
 *   barber sees them on the day's schedule. Photos are never shared.
 * @property photoConsent the customer agreed that their photos are stored in their account.
 */
data class StyleProfile(
    val tags: Set<StyleTag> = emptySet(),
    val note: String = "",
    val preferredStaffId: String? = null,
    val shareWithBarber: Boolean = false,
    val photoConsent: Boolean = false,
) {
    val isEmpty: Boolean get() = tags.isEmpty() && note.isBlank()

    companion object {
        const val MAX_NOTE = 150 // fits in a booking note (200) together with the tags
    }
}

/** Why a moodboard photo was added. */
enum class PhotoLabel { PREVIOUS_RESULT, INSPIRATION }

/**
 * One moodboard photo, stored as a small JPEG inside Firestore (`users/{uid}/photos/{id}`).
 * WHY in Firestore: Cloud Storage needs the Blaze plan for new projects; photos are compressed
 * to ~200 KB so they fit well under Firestore's 1 MB document limit.
 */
class StylePhoto(
    val id: String,
    val jpeg: ByteArray,
    val label: PhotoLabel,
    val createdAt: Instant?,
)
