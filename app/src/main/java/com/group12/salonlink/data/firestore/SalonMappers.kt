package com.group12.salonlink.data.firestore

import com.group12.salonlink.data.firestore.FirestoreSchema.BlockFields
import com.group12.salonlink.data.firestore.FirestoreSchema.SalonFields
import com.group12.salonlink.data.firestore.FirestoreSchema.ServiceFields
import com.group12.salonlink.data.firestore.FirestoreSchema.StaffFields
import com.group12.salonlink.domain.model.Salon
import com.group12.salonlink.domain.model.SalonService
import com.group12.salonlink.domain.model.ServiceCategory
import com.group12.salonlink.domain.model.Staff
import com.group12.salonlink.domain.model.StaffBlock

/*
 * Firestore ↔ domain conversions for the salon catalogue.
 * Same approach as UserMappers.kt: explicit field names, no reflection, safe defaults.
 * Pure functions, unit-tested in SalonMappersTest.
 */

// ----- Salon -----

fun Salon.toFirestoreMap(): Map<String, Any?> = mapOf(
    SalonFields.NAME to name,
    SalonFields.ADDRESS to address,
    SalonFields.AREA to area,
    SalonFields.PHONE to phone,
    SalonFields.OPENING_HOURS to openingHours.toFirestoreMap(),
)

fun salonFromMap(id: String, data: Map<String, Any?>): Salon = Salon(
    id = id,
    name = data[SalonFields.NAME] as? String ?: "",
    address = data[SalonFields.ADDRESS] as? String ?: "",
    area = data[SalonFields.AREA] as? String ?: "",
    phone = data[SalonFields.PHONE] as? String,
    openingHours = weeklyHoursFromMap(data[SalonFields.OPENING_HOURS]),
)

// ----- Service -----

fun SalonService.toFirestoreMap(): Map<String, Any?> = mapOf(
    ServiceFields.NAME to name,
    ServiceFields.DESCRIPTION to description,
    ServiceFields.CATEGORY to category.name,
    ServiceFields.DURATION_MIN to durationMin.toLong(),
    ServiceFields.PRICE to price,
    ServiceFields.STAFF_IDS to staffIds,
    ServiceFields.ACTIVE to active,
)

/**
 * Returns null for a service that can't be booked safely (no positive duration), so it is
 * left out of lists instead of producing a zero-minute booking.
 */
fun salonServiceFromMap(id: String, data: Map<String, Any?>): SalonService? {
    val duration = data[ServiceFields.DURATION_MIN].asInt()?.takeIf { it > 0 } ?: return null
    return SalonService(
        id = id,
        name = data[ServiceFields.NAME] as? String ?: "",
        description = data[ServiceFields.DESCRIPTION] as? String ?: "",
        category = ServiceCategory.fromValue(data[ServiceFields.CATEGORY] as? String),
        durationMin = duration,
        price = data[ServiceFields.PRICE].asLong()?.coerceAtLeast(0) ?: 0,
        staffIds = data[ServiceFields.STAFF_IDS].asStringList(),
        // WHY default true: a service added in the console without the flag should be visible.
        active = data[ServiceFields.ACTIVE] as? Boolean ?: true,
    )
}

// ----- Staff -----

fun Staff.toFirestoreMap(): Map<String, Any?> = mapOf(
    StaffFields.NAME to name,
    StaffFields.BIO to bio,
    StaffFields.SPECIALTIES to specialties,
    StaffFields.RATING to rating,
    StaffFields.REVIEW_COUNT to reviewCount.toLong(),
    StaffFields.YEARS_EXPERIENCE to yearsExperience.toLong(),
    StaffFields.PHOTO_URL to photoUrl,
    StaffFields.PORTFOLIO_URLS to portfolioUrls,
    StaffFields.WORKING_HOURS to workingHours.toFirestoreMap(),
    StaffFields.ACTIVE to active,
)

fun staffFromMap(id: String, data: Map<String, Any?>): Staff = Staff(
    id = id,
    name = data[StaffFields.NAME] as? String ?: "",
    bio = data[StaffFields.BIO] as? String ?: "",
    specialties = data[StaffFields.SPECIALTIES].asStringList(),
    rating = data[StaffFields.RATING].asDouble()?.coerceIn(0.0, 5.0) ?: 0.0,
    reviewCount = data[StaffFields.REVIEW_COUNT].asInt()?.coerceAtLeast(0) ?: 0,
    yearsExperience = data[StaffFields.YEARS_EXPERIENCE].asInt()?.coerceAtLeast(0) ?: 0,
    photoUrl = data[StaffFields.PHOTO_URL] as? String,
    portfolioUrls = data[StaffFields.PORTFOLIO_URLS].asStringList(),
    workingHours = weeklyHoursFromMap(data[StaffFields.WORKING_HOURS]),
    active = data[StaffFields.ACTIVE] as? Boolean ?: true,
)

// ----- Staff block -----

fun StaffBlock.toFirestoreMap(): Map<String, Any?> = mapOf(
    BlockFields.START to start.toTimestamp(),
    BlockFields.END to end.toTimestamp(),
    BlockFields.REASON to reason,
)

/** Returns null if the times are missing or reversed: such a block can't mean anything. */
fun staffBlockFromMap(id: String, data: Map<String, Any?>): StaffBlock? {
    val start = data[BlockFields.START].asInstant() ?: return null
    val end = data[BlockFields.END].asInstant() ?: return null
    if (end <= start) return null
    return StaffBlock(id = id, start = start, end = end, reason = data[BlockFields.REASON] as? String ?: "")
}
