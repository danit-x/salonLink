package com.group12.salonlink.testing

import com.group12.salonlink.domain.model.PhotoLabel
import com.group12.salonlink.domain.model.StylePhoto
import com.group12.salonlink.domain.model.StyleProfile
import com.group12.salonlink.domain.repository.StyleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory style profiles and photos, keyed by uid. */
class FakeStyleRepository : StyleRepository {
    val profiles = MutableStateFlow<Map<String, StyleProfile>>(emptyMap())
    val photos = MutableStateFlow<Map<String, List<StylePhoto>>>(emptyMap())
    private var nextId = 1

    override fun observeProfile(uid: String): Flow<StyleProfile> = profiles.map { it[uid] ?: StyleProfile() }

    override suspend fun saveProfile(uid: String, profile: StyleProfile) {
        profiles.value = profiles.value + (uid to profile)
    }

    override fun observePhotos(uid: String): Flow<List<StylePhoto>> = photos.map { it[uid].orEmpty() }

    override suspend fun addPhoto(uid: String, jpeg: ByteArray, label: PhotoLabel): String {
        val id = "p${nextId++}"
        photos.value = photos.value + (uid to listOf(StylePhoto(id, jpeg, label, null)) + photos.value[uid].orEmpty())
        return id
    }

    override suspend fun deletePhoto(uid: String, photoId: String) {
        photos.value = photos.value + (uid to photos.value[uid].orEmpty().filter { it.id != photoId })
    }
}
