package com.group12.salonlink.data.firestore

import com.google.android.gms.tasks.Task
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
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

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

/** How long a plain write waits for the server before it counts as "queued on this phone". */
internal const val WRITE_ACK_TIMEOUT_MS = 8_000L

/**
 * Awaits a plain (non-transaction) write such as `set`, `update`, `delete` or a batch.
 *
 * WHY: offline, Firestore applies the write to the local cache at once and keeps it queued,
 * but the Task only completes when the server confirms — possibly hours later. Awaiting it
 * directly would leave a "Saving…" spinner up until the phone reconnects. After
 * [WRITE_ACK_TIMEOUT_MS] we stop waiting: the change is already visible on this phone and
 * Firestore sends it when the connection returns. Errors that do arrive in time (e.g. the
 * rules said no) are still reported. Transactions don't use this: they need the server and
 * fail fast with NETWORK when offline.
 */
suspend fun <T> Task<T>.awaitWrite() {
    try {
        withTimeoutOrNull(WRITE_ACK_TIMEOUT_MS) { await() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw e.toDataException()
    }
}
