package com.group12.salonlink.data.firestore

import com.group12.salonlink.domain.model.Salon
import com.group12.salonlink.domain.model.SalonService
import com.group12.salonlink.domain.model.ServiceCategory
import com.group12.salonlink.domain.model.Staff
import com.group12.salonlink.domain.model.StaffBlock
import com.group12.salonlink.domain.model.TimeRange
import com.group12.salonlink.domain.model.WeeklyHours
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SalonMappersTest {

    private val weekdays = WeeklyHours(
        DayOfWeek.entries.take(6).associateWith { TimeRange(LocalTime.of(9, 0), LocalTime.of(19, 0)) },
    )

    private val aura = Salon("aura", "Aura Salon", "24 Havelock Road, Colombo 05", "Colombo 05", "0112345678", weekdays)

    private val haircut = SalonService(
        id = "haircut", name = "Haircut", description = "Consultation, wash, cut & finish",
        category = ServiceCategory.HAIRCUT, durationMin = 30, price = 1500, staffIds = listOf("kasun"),
    )

    private val kasun = Staff(
        id = "kasun", name = "Kasun Perera", bio = "Modern cuts", specialties = listOf("Fade", "Colour"),
        rating = 4.9, reviewCount = 128, yearsExperience = 8, photoUrl = null, portfolioUrls = emptyList(),
        workingHours = weekdays,
    )

    @Test
    fun `salon, service and staff survive a round trip`() {
        assertEquals(aura, salonFromMap("aura", aura.toFirestoreMap()))
        assertEquals(haircut, salonServiceFromMap("haircut", haircut.toFirestoreMap()))
        assertEquals(kasun, staffFromMap("kasun", kasun.toFirestoreMap()))
    }

    @Test
    fun `opening hours are stored in the seed script's readable format`() {
        val stored = aura.toFirestoreMap()["openingHours"] as Map<*, *>
        assertEquals(listOf("09:00", "19:00"), stored["MON"])
        assertNull(stored["SUN"])
    }

    @Test
    fun `broken opening hours mean closed that day, not a crash`() {
        val hours = weeklyHoursFromMap(
            mapOf(
                "MON" to listOf("09:00", "19:00"),
                "TUE" to listOf("19:00", "09:00"), // reversed
                "WED" to listOf("nine", "seven"),  // not a time
                "THU" to "09:00-19:00",           // wrong shape
            ),
        )
        assertEquals(setOf(DayOfWeek.MONDAY), hours.days.keys)
        assertEquals(WeeklyHours.CLOSED, weeklyHoursFromMap(null))
    }

    @Test
    fun `console-typed numbers are accepted`() {
        val service = salonServiceFromMap("x", mapOf("name" to "X", "durationMin" to 30.0, "price" to "1500"))
        assertEquals(30, service?.durationMin)
        assertEquals(1500L, service?.price)
    }

    @Test
    fun `a service without a positive duration is skipped`() {
        assertNull(salonServiceFromMap("x", mapOf("name" to "X", "price" to 100L)))
        assertNull(salonServiceFromMap("x", mapOf("name" to "X", "durationMin" to 0L)))
    }

    @Test
    fun `a rating outside 0 to 5 is clamped`() {
        assertEquals(5.0, staffFromMap("s", mapOf("rating" to 9.5)).rating, 0.0)
    }

    @Test
    fun `blocks round trip and reversed blocks are dropped`() {
        val lunch = StaffBlock("b", Instant.parse("2026-10-08T07:30:00Z"), Instant.parse("2026-10-08T08:00:00Z"), "Lunch")
        assertEquals(lunch, staffBlockFromMap("b", lunch.toFirestoreMap()))
        val reversed = lunch.copy(start = lunch.end, end = lunch.start).toFirestoreMap()
        assertNull(staffBlockFromMap("b", reversed))
    }
}
