// Covers: FR-12 (barber matching from the style profile)
package com.group12.salonlink.domain.style

import com.group12.salonlink.domain.model.StyleProfile
import com.group12.salonlink.domain.model.StyleTag
import com.group12.salonlink.domain.model.Staff
import java.util.Locale
import kotlin.math.roundToInt

/**
 * How well a barber suits a customer's style profile, as a 0–100 "match" score.
 *
 * Formula (simple on purpose, so it can be explained in one sentence):
 *   80 points × (share of the customer's tags the barber's specialties cover)
 * + 20 points × (rating ÷ 5).
 * Picking a favourite barber doesn't change the score; it only puts them first in the list.
 *
 * Example: tags {Fade, Beard}, Kasun ("Fade", "Colour", "Curly Hair", "Beard Styling", 4.9 ★)
 * covers 2 of 2 → 80 + 19.6 = 100 (rounded). Nadeesha ("Colour", "Treatments", "Long Hair", 4.8 ★)
 * covers 0 of 2 → 0 + 19.2 = 19.
 */
object BarberMatcher {

    /** null when the customer hasn't picked any tags yet (nothing to match against). */
    fun score(profile: StyleProfile, staff: Staff): Int? {
        if (profile.tags.isEmpty()) return null
        val covered = profile.tags.count { covers(staff, it) }
        val coverage = covered.toDouble() / profile.tags.size
        val rating = (staff.rating / 5.0).coerceIn(0.0, 1.0)
        return (80 * coverage + 20 * rating).roundToInt().coerceIn(0, 100)
    }

    /** True if one of the barber's specialties mentions a keyword of [tag]. */
    fun covers(staff: Staff, tag: StyleTag): Boolean {
        val specialties = staff.specialties.map { it.lowercase(Locale.ENGLISH) }
        return tag.keywords.any { word -> specialties.any { it.contains(word) } }
    }

    /**
     * Text added to a booking when the customer agreed to share their profile, e.g.
     * "Style: FADE, BEARD · Keep the length on top". The barber's schedule shows it as the note.
     * Tag names are kept in English on purpose: the salon's staff app reads them.
     */
    fun bookingNote(profile: StyleProfile): String? {
        if (!profile.shareWithBarber || profile.isEmpty) return null
        val tags = profile.tags.sortedBy { it.ordinal }.joinToString(", ") { it.englishLabel() }
        return listOf(
            tags.takeIf { it.isNotEmpty() }?.let { "Style: $it" },
            profile.note.trim().takeIf { it.isNotEmpty() },
        ).filterNotNull().joinToString(" · ")
    }

    private fun StyleTag.englishLabel(): String = when (this) {
        StyleTag.FADE -> "Fade"
        StyleTag.CURLY -> "Curly / textured"
        StyleTag.COLOUR -> "Colour"
        StyleTag.BEARD -> "Beard"
        StyleTag.LONG_HAIR -> "Long hair"
        StyleTag.TREATMENT -> "Treatment"
        StyleTag.CLASSIC -> "Classic cut"
    }
}
