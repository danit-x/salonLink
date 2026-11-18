package com.group12.salonlink.data.firestore

import com.google.firebase.Timestamp
import com.group12.salonlink.domain.model.TimeRange
import com.group12.salonlink.domain.model.WeeklyHours
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeParseException
import java.util.Date

/*
 * Small, forgiving converters for raw Firestore values.
 *
 * WHY forgiving: documents can be edited by hand in the Firebase console, where it's easy to
 * type 1500 as a string or leave a field out. Each converter returns a safe default (or null)
 * instead of throwing, so one bad document can't crash a whole list screen.
 */

/** Firestore stores whole numbers as Long, but console edits can produce Double. Accept both. */
internal fun Any?.asLong(): Long? = when (this) {
    is Long -> this
    is Int -> toLong()
    is Double -> toLong()
    is String -> toLongOrNull()
    else -> null
}

internal fun Any?.asInt(): Int? = asLong()?.toInt()

internal fun Any?.asDouble(): Double? = when (this) {
    is Number -> toDouble()
    is String -> toDoubleOrNull()
    else -> null
}

/** A list of strings, skipping anything that isn't a string. */
internal fun Any?.asStringList(): List<String> = (this as? List<*>)?.filterIsInstance<String>().orEmpty()

/** Firestore Timestamp (or a java.util.Date from older data) → Instant. */
internal fun Any?.asInstant(): Instant? = when (this) {
    is Timestamp -> Instant.ofEpochSecond(seconds, nanoseconds.toLong())
    is Date -> toInstant()
    else -> null
}

/** Instant → Firestore Timestamp, keeping full precision. */
internal fun Instant.toTimestamp(): Timestamp = Timestamp(epochSecond, nano)

// ---------------------------------------------------------------------------------------
// Weekly hours: stored as { "MON": ["09:00", "19:00"], "TUE": [...], ... }
// WHY "HH:mm" strings and not numbers: they read naturally in the Firebase console, so the
// team can check or fix opening hours by eye.
// ---------------------------------------------------------------------------------------

/** MONDAY → "MON" */
internal fun DayOfWeek.key(): String = name.take(3)

internal fun WeeklyHours.toFirestoreMap(): Map<String, List<String>> =
    days.entries
        .sortedBy { it.key }
        .associate { (day, range) -> day.key() to listOf(range.start.toString(), range.end.toString()) }

/** Parses weekly hours. Unknown days and invalid ranges are skipped (that day counts as closed). */
internal fun weeklyHoursFromMap(raw: Any?): WeeklyHours {
    val map = raw as? Map<*, *> ?: return WeeklyHours.CLOSED
    val days = buildMap {
        for (day in DayOfWeek.entries) {
            val pair = (map[day.key()] as? List<*>)?.filterIsInstance<String>() ?: continue
            if (pair.size != 2) continue
            val range = parseRange(pair[0], pair[1]) ?: continue
            put(day, range)
        }
    }
    return WeeklyHours(days)
}

private fun parseRange(start: String, end: String): TimeRange? = try {
    val s = LocalTime.parse(start)
    val e = LocalTime.parse(end)
    if (s < e) TimeRange(s, e) else null
} catch (_: DateTimeParseException) {
    null
}
