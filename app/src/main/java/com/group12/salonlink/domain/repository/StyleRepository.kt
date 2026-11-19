package com.group12.salonlink.domain.repository

import com.group12.salonlink.domain.model.PhotoLabel
import com.group12.salonlink.domain.model.StyleProfile
import com.group12.salonlink.domain.model.StylePhoto
import kotlinx.coroutines.flow.Flow

/**
 * The customer's private style profile and moodboard (Phase 9). Only the customer can read or
 * write these documents (security rules v7).
 */
interface StyleRepository {
    /** Live profile; an empty [StyleProfile] if none was saved yet. */
    fun observeProfile(uid: String): Flow<StyleProfile>

    suspend fun saveProfile(uid: String, profile: StyleProfile)

    /** Live moodboard, newest first. */
    fun observePhotos(uid: String): Flow<List<StylePhoto>>

    /** Saves an already-compressed JPEG. @return the new photo's id. */
    suspend fun addPhoto(uid: String, jpeg: ByteArray, label: PhotoLabel): String

    suspend fun deletePhoto(uid: String, photoId: String)
}

/** Shrinks a picked picture to a small JPEG (Android code lives in the data layer). */
interface PhotoCompressor {
    /**
     * @param uri the content:// address from the photo picker.
     * @throws com.group12.salonlink.domain.model.DataException INVALID_REQUEST if it can't be read.
     */
    suspend fun compress(uri: String): ByteArray
}
