package com.group12.salonlink.data.firestore

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.group12.salonlink.domain.model.DataError
import com.group12.salonlink.domain.model.DataException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/*
 * Turns Firestore's real-time listeners into Kotlin Flows.
 *
 * HOW: callbackFlow registers a snapshot listener when someone starts collecting, sends every
 * new snapshot into the Flow, and removes the listener (awaitClose) when the collector stops,
 * e.g. when the screen leaves. No listener leaks, no manual cleanup in ViewModels.
 *
 * Errors close the Flow with a [DataException] so the UI can show a friendly message (NFR-10).
 */

/** Live results of this query. */
fun Query.observe(): Flow<QuerySnapshot> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        when {
            error != null -> close(error.toDataException())
            snapshot != null -> trySend(snapshot)
        }
    }
    awaitClose { registration.remove() }
}

/** Live value of this document (check `snapshot.exists()`). */
fun DocumentReference.observe(): Flow<DocumentSnapshot> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        when {
            error != null -> close(error.toDataException())
            snapshot != null -> trySend(snapshot)
        }
    }
    awaitClose { registration.remove() }
}

/** Maps any Firebase exception to our own [DataException]. */
fun Throwable.toDataException(): DataException = when (this) {
    is DataException -> this
    is FirebaseNetworkException -> DataException(DataError.NETWORK, this)
    is FirebaseFirestoreException -> DataException(
        when (code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> DataError.PERMISSION_DENIED
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> DataError.NETWORK
            FirebaseFirestoreException.Code.NOT_FOUND -> DataError.NOT_FOUND
            else -> DataError.UNKNOWN
        },
        this,
    )
    else -> DataException(DataError.UNKNOWN, this)
}
