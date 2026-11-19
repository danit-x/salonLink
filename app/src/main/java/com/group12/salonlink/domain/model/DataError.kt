package com.group12.salonlink.domain.model

import com.group12.salonlink.domain.booking.SlotProblem

/**
 * Ways loading or saving salon data can fail, in words the UI can explain (NFR-10).
 *
 * Same idea as [AuthError]: ViewModels never see Firebase exception classes, only these.
 */
enum class DataError {
    NETWORK,            // offline and the data isn't in the local cache (bookings need a connection)
    PERMISSION_DENIED,  // security rules said no (wrong role / wrong salon)
    NOT_FOUND,
    SLOT_TAKEN,         // someone else booked this time a moment ago (lost the race, T03)
    SLOT_UNAVAILABLE,   // the time isn't bookable: closed, blocked, past… see SlotUnavailableException
    NOT_ALLOWED,        // e.g. cancelling a booking that is already completed
    TOO_LATE_TO_CANCEL, // less than 24 h before the start: the customer must call the salon
    INVALID_REQUEST,    // e.g. a service this barber doesn't offer, or no services chosen
    UNKNOWN,
}

/** Thrown (or used to close a Flow) by repositories and use cases when something goes wrong. */
open class DataException(val error: DataError, cause: Throwable? = null) : Exception(error.name, cause)

/**
 * The requested time can't be booked, and [problem] says why ("Kasun is on lunch then").
 * Thrown by CreateBookingUseCase BEFORE anything is written.
 */
class SlotUnavailableException(val problem: SlotProblem) :
    DataException(if (problem == SlotProblem.TAKEN) DataError.SLOT_TAKEN else DataError.SLOT_UNAVAILABLE)
