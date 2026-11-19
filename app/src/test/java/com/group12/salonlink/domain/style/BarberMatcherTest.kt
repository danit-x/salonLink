package com.group12.salonlink.domain.style

import com.group12.salonlink.domain.model.Staff
import com.group12.salonlink.domain.model.StyleProfile
import com.group12.salonlink.domain.model.StyleTag
import com.group12.salonlink.domain.model.WeeklyHours
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Barber matching score and the note shared with the barber (FR-12). */
class BarberMatcherTest {

    private fun barber(rating: Double, vararg specialties: String) =
        Staff("id", "Name", "", specialties.toList(), rating, 0, 0, null, emptyList(), WeeklyHours(emptyMap()))

    private val kasun = barber(4.9, "Fade", "Colour", "Curly Hair", "Beard Styling")
    private val nadeesha = barber(4.8, "Colour", "Treatments", "Long Hair")

    @Test
    fun `no tags means no score`() {
        assertNull(BarberMatcher.score(StyleProfile(), kasun))
    }

    @Test
    fun `full cover scores high, no cover only gets the rating part`() {
        val profile = StyleProfile(tags = setOf(StyleTag.FADE, StyleTag.BEARD))
        assertEquals(100, BarberMatcher.score(profile, kasun)) // 80 + 19.6
        assertEquals(19, BarberMatcher.score(profile, nadeesha)) // 0 + 19.2
    }

    @Test
    fun `half the tags covered gives about half the style points`() {
        val profile = StyleProfile(tags = setOf(StyleTag.FADE, StyleTag.LONG_HAIR))
        assertEquals(60, BarberMatcher.score(profile, kasun)) // 40 + 19.6
    }

    @Test
    fun `keywords match inside longer specialty names, any case`() {
        assertTrue(BarberMatcher.covers(kasun, StyleTag.CURLY)) // "Curly Hair"
        assertTrue(BarberMatcher.covers(nadeesha, StyleTag.TREATMENT)) // "Treatments"
        assertTrue(BarberMatcher.covers(barber(4.0, "HAIR COLOR"), StyleTag.COLOUR))
    }

    @Test
    fun `the booking note needs consent and something to say`() {
        val profile = StyleProfile(tags = setOf(StyleTag.BEARD, StyleTag.FADE), note = " Keep the top ")
        assertNull(BarberMatcher.bookingNote(profile))
        assertEquals("Style: Fade, Beard · Keep the top", BarberMatcher.bookingNote(profile.copy(shareWithBarber = true)))
        assertEquals("Keep the top", BarberMatcher.bookingNote(profile.copy(tags = emptySet(), shareWithBarber = true)))
        assertNull(BarberMatcher.bookingNote(StyleProfile(shareWithBarber = true)))
    }
}
