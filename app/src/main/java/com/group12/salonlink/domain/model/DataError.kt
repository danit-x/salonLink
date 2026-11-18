package com.group12.salonlink.domain.model

/**
 * Ways loading or saving salon data can fail, in words the UI can explain (NFR-10).
 *
 * Same idea as [AuthError]: ViewModels never see Firebase exception classes, only these.
 */
enum class DataError {
    NETWORK,            // offline and the data isn't in the local cache
    PERMISSION_DENIED,  // security rules said no (wrong role / wrong salon)
    NOT_FOUND,
    UNKNOWN,
}

/** Thrown (or used to close a Flow) by repositories when something goes wrong. */
class DataException(val error: DataError, cause: Throwable? = null) : Exception(error.name, cause)
