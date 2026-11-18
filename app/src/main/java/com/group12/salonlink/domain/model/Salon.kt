package com.group12.salonlink.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/*
 * Domain models for the salon catalogue: the salon, its services, its staff and their
 * blocked times. Stored under `salons/{salonId}` in Firestore (see PLAN.md §4).
 *
 * Plain Kotlin only, with no Firebase types, so ViewModels and unit tests never need the SDK.
 */

/**
 * A wall-clock time window inside one day, e.g. 09:00–19:00.
 *
 * WHY LocalTime (no date, no zone): opening hours repeat every week and are always meant in
 * the salon's own time zone. They become real instants only when combined with a date via
 * [com.group12.salonlink.core.util.SalonTime.toInstant].
 */
data class TimeRange(val start: LocalTime, val end: LocalTime) {
    init {
        require(start < end) { "TimeRange start ($start) must be before end ($end)" }
    }

    /** True if [time] falls inside the window. The end is exclusive: a 19:00 close means no 19:00 start. */
    operator fun contains(time: LocalTime): Boolean = time >= start && time < end
}

/**
 * Opening or working hours for each day of the week. A day missing from [days] is closed.
 *
 * Example: `WeeklyHours(mapOf(DayOfWeek.MONDAY to TimeRange(9:00, 19:00)))` = open Mondays only.
 */
data class WeeklyHours(val days: Map<DayOfWeek, TimeRange> = emptyMap()) {

    /** The hours that apply on [date], or null if closed that day. */
    fun hoursOn(date: LocalDate): TimeRange? = days[date.dayOfWeek]

    fun isOpenOn(date: LocalDate): Boolean = hoursOn(date) != null

    companion object {
        /** Closed every day. Used as a safe default when the stored hours are missing or corrupt. */
        val CLOSED = WeeklyHours()
    }
}

/**
 * A salon (proposal FR-02). The demo has one, "Aura Salon", but every query is scoped by
 * [id] so more salons work without code changes (NFR-09).
 *
 * @property area short locality shown under names, e.g. "Colombo 05".
 */
data class Salon(
    val id: String,
    val name: String,
    val address: String,
    val area: String,
    val phone: String?,
    val openingHours: WeeklyHours,
)

/**
 * Broad kind of service. The UI picks an icon from this (Figma "Service selection":
 * scissors, beard, palette, droplets).
 */
enum class ServiceCategory {
    HAIRCUT,
    BEARD,
    COLOUR,
    TREATMENT,
    OTHER;

    companion object {
        /** Unknown values fall back to OTHER instead of crashing. */
        fun fromValue(value: String?): ServiceCategory = entries.firstOrNull { it.name == value } ?: OTHER
    }
}

/**
 * Something a customer can book, e.g. "Haircut · 30 min · Rs. 1,500".
 *
 * Named `SalonService` (not `Service`) so it never clashes with `android.app.Service`.
 *
 * @property price whole rupees (see [com.group12.salonlink.core.util.Money] for why not Double).
 * @property staffIds the staff who can perform it. Empty = any staff member.
 * @property active false = hidden from customers but kept so old bookings still make sense.
 */
data class SalonService(
    val id: String,
    val name: String,
    val description: String,
    val category: ServiceCategory,
    val durationMin: Int,
    val price: Long,
    val staffIds: List<String> = emptyList(),
    val active: Boolean = true,
) {
    /** True if staff member [staffId] can perform this service. */
    fun isOfferedBy(staffId: String): Boolean = staffIds.isEmpty() || staffId in staffIds
}

/**
 * A barber / stylist (Figma "Barber profile").
 *
 * @property rating average star rating, 0.0–5.0. Shown as "★ 4.9".
 * @property photoUrl optional portrait. Null = the UI shows a placeholder.
 * @property workingHours when this person takes bookings. Always inside the salon's hours.
 */
data class Staff(
    val id: String,
    val name: String,
    val bio: String,
    val specialties: List<String>,
    val rating: Double,
    val reviewCount: Int,
    val yearsExperience: Int,
    val photoUrl: String?,
    val portfolioUrls: List<String>,
    val workingHours: WeeklyHours,
    val active: Boolean = true,
) {
    val firstName: String get() = name.trim().substringBefore(' ')
}

/**
 * Time a staff member is unavailable (lunch, leave, training…), proposal P2.
 * Stored as UTC instants like bookings, so it can span midnight or several days.
 */
data class StaffBlock(
    val id: String,
    val start: Instant,
    val end: Instant,
    val reason: String,
) {
    /** True if this block overlaps the half-open interval [from, to). */
    fun overlaps(from: Instant, to: Instant): Boolean = start < to && end > from
}
