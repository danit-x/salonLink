package com.group12.salonlink.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Date / time rules for the whole app.
 *
 * GOLDEN RULE: times are *stored* as UTC instants (Firestore Timestamp) and only
 * *displayed* in the salon's time zone. A phone set to another zone, or a daylight-saving
 * change, can then never shift a booking.
 *
 * java.time works on our minSdk 26 without desugaring.
 */
object SalonTime {

    /** Sri Lanka Standard Time (UTC+05:30), shown as "IST (UTC+5:30)" in the Figma date picker. */
    val SALON_ZONE: ZoneId = ZoneId.of("Asia/Colombo")

    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val shortDateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
    private val longDateFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)

    /** Converts a salon-local date + wall-clock time into the UTC instant we store. */
    fun toInstant(date: LocalDate, time: LocalTime): Instant =
        ZonedDateTime.of(date, time, SALON_ZONE).toInstant()

    /**
     * Midnight at the start of [date] in the salon's zone, as a UTC instant.
     * A day's bookings are those with `startOfDay(d) <= start < startOfDay(d + 1)`.
     */
    fun startOfDay(date: LocalDate): Instant = date.atStartOfDay(SALON_ZONE).toInstant()

    /** The salon-local date of a stored instant (used to group bookings by day). */
    fun localDateOf(instant: Instant): LocalDate = instant.atZone(SALON_ZONE).toLocalDate()

    /** "5:30 pm". Figma uses lowercase am/pm. */
    fun formatTime(instant: Instant): String =
        timeFormatter.format(instant.atZone(SALON_ZONE)).lowercase(Locale.ENGLISH)

    /** "Thu, 8 Oct" */
    fun formatShortDate(instant: Instant): String = shortDateFormatter.format(instant.atZone(SALON_ZONE))

    /** "Thursday, 8 October 2026" */
    fun formatLongDate(instant: Instant): String = longDateFormatter.format(instant.atZone(SALON_ZONE))

    /** "5:30 pm – 6:20 pm" */
    fun formatRange(start: Instant, end: Instant): String = "${formatTime(start)} – ${formatTime(end)}"

    /** "Good morning" / "Good afternoon" / "Good evening" for the Discovery greeting. */
    fun greetingFor(time: LocalTime): Greeting = when (time.hour) {
        in 5..11 -> Greeting.MORNING
        in 12..16 -> Greeting.AFTERNOON
        else -> Greeting.EVENING
    }

    enum class Greeting { MORNING, AFTERNOON, EVENING }
}
