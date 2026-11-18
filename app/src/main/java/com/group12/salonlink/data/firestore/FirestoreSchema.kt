package com.group12.salonlink.data.firestore

/**
 * Names of Firestore collections and fields, all in one place.
 *
 * WHY constants: a typo like "user" vs "users" would compile fine and fail silently at
 * runtime. Keep these in sync with `firebase/firestore.rules` and `firebase/seed/seed.mjs`.
 *
 * Layout (PLAN.md §4):
 * ```
 * users/{uid}
 * invites/{email}
 * salons/{salonId}
 *   ├─ services/{serviceId}
 *   ├─ staff/{staffId}
 *   │    └─ blocks/{blockId}
 *   └─ bookings/{bookingId}
 * ```
 */
object FirestoreSchema {

    // --- Collections ---
    const val USERS = "users"
    const val INVITES = "invites"
    const val SALONS = "salons"
    const val SERVICES = "services"   // sub-collection of salons/{id}
    const val STAFF = "staff"         // sub-collection of salons/{id}
    const val BLOCKS = "blocks"       // sub-collection of salons/{id}/staff/{id}
    const val BOOKINGS = "bookings"   // sub-collection of salons/{id}; also queried as a collection group

    // --- users/{uid} fields ---
    object UserFields {
        const val NAME = "name"
        const val EMAIL = "email"
        const val PHONE = "phone"
        const val ROLE = "role"
        const val SALON_ID = "salonId"
        const val STAFF_ID = "staffId"
        const val CREATED_AT = "createdAt"
    }

    // --- invites/{email} fields ---
    object InviteFields {
        const val ROLE = "role"
        const val SALON_ID = "salonId"
        const val STAFF_ID = "staffId"
    }

    // --- salons/{salonId} fields ---
    object SalonFields {
        const val NAME = "name"
        const val ADDRESS = "address"
        const val AREA = "area"
        const val PHONE = "phone"
        const val OPENING_HOURS = "openingHours"   // { "MON": ["09:00", "19:00"], ... }
    }

    // --- salons/{id}/services/{id} fields ---
    object ServiceFields {
        const val NAME = "name"
        const val DESCRIPTION = "description"
        const val CATEGORY = "category"
        const val DURATION_MIN = "durationMin"
        const val PRICE = "price"
        const val STAFF_IDS = "staffIds"
        const val ACTIVE = "active"
    }

    // --- salons/{id}/staff/{id} fields ---
    object StaffFields {
        const val NAME = "name"
        const val BIO = "bio"
        const val SPECIALTIES = "specialties"
        const val RATING = "rating"
        const val REVIEW_COUNT = "reviewCount"
        const val YEARS_EXPERIENCE = "yearsExperience"
        const val PHOTO_URL = "photoUrl"
        const val PORTFOLIO_URLS = "portfolioUrls"
        const val WORKING_HOURS = "workingHours"   // same shape as SalonFields.OPENING_HOURS
        const val ACTIVE = "active"
    }

    // --- salons/{id}/staff/{id}/blocks/{id} fields ---
    object BlockFields {
        const val START = "start"
        const val END = "end"
        const val REASON = "reason"
    }

    // --- salons/{id}/bookings/{id} fields ---
    object BookingFields {
        const val SALON_ID = "salonId"
        const val CUSTOMER_ID = "customerId"
        const val CUSTOMER_NAME = "customerName"
        const val STAFF_ID = "staffId"
        const val STAFF_NAME = "staffName"
        const val SERVICES = "services"             // list of BookedServiceFields maps
        const val START = "start"
        const val END = "end"
        const val TOTAL_PRICE = "totalPrice"
        const val STATUS = "status"
        const val TYPE = "type"
        const val NOTE = "note"
        const val CREATED_AT = "createdAt"
        const val UPDATED_AT = "updatedAt"
        const val CHECKED_IN_AT = "checkedInAt"
    }

    // --- one entry of bookings/{id}.services ---
    object BookedServiceFields {
        const val SERVICE_ID = "serviceId"
        const val NAME = "name"
        const val DURATION_MIN = "durationMin"
        const val PRICE = "price"
    }
}
