// Covers: US-10, FR-12 (style profile, moodboard, favourite barber, consent) · Figma: "My Style Profile"
package com.group12.salonlink.feature.customer.style

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group12.salonlink.domain.model.AuthState
import com.group12.salonlink.domain.model.DataError
import com.group12.salonlink.domain.model.DataException
import com.group12.salonlink.domain.model.PhotoLabel
import com.group12.salonlink.domain.model.Staff
import com.group12.salonlink.domain.model.StylePhoto
import com.group12.salonlink.domain.model.StyleProfile
import com.group12.salonlink.domain.model.StyleTag
import com.group12.salonlink.domain.model.UserProfile
import com.group12.salonlink.domain.repository.AuthRepository
import com.group12.salonlink.domain.repository.PhotoCompressor
import com.group12.salonlink.domain.repository.SalonRepository
import com.group12.salonlink.domain.repository.StyleRepository
import com.group12.salonlink.domain.style.BarberMatcher
import com.group12.salonlink.feature.customer.booking.toDataError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A barber the customer can pick as favourite, with how well they match the profile. */
data class BarberChoice(val staff: Staff, val salonName: String, val match: Int?)

/** One-off messages shown in a banner. */
sealed interface StyleMessage {
    data object Saved : StyleMessage
    data object PhotoAdded : StyleMessage
    data class Failed(val error: DataError) : StyleMessage
}

/**
 * @property draft what is on screen (tags, note, sharing, favourite); saved with "Save".
 * @property dirty the draft differs from what is stored.
 * @property pickPhotoLabel set when the photo picker should open (consent already given).
 */
data class MyStyleUiState(
    val loading: Boolean = true,
    val user: UserProfile? = null,
    val draft: StyleProfile = StyleProfile(),
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val photos: List<StylePhoto> = emptyList(),
    val barbers: List<BarberChoice> = emptyList(),
    val addingPhoto: Boolean = false,
    val askConsentFor: PhotoLabel? = null,
    val pickPhotoLabel: PhotoLabel? = null,
    val message: StyleMessage? = null,
    val error: DataError? = null,
) {
    val canAddPhoto: Boolean get() = photos.size < MAX_PHOTOS && !addingPhoto

    companion object {
        const val MAX_PHOTOS = 12
    }
}

sealed interface MyStyleEvent {
    data class ToggleTag(val tag: StyleTag) : MyStyleEvent
    data class NoteChanged(val note: String) : MyStyleEvent
    data class ShareChanged(val share: Boolean) : MyStyleEvent
    /** null = no favourite. */
    data class PreferredBarber(val staffId: String?) : MyStyleEvent
    data object Save : MyStyleEvent

    /** "Add photo": asks for consent the first time, otherwise opens the picker. */
    data class AddPhoto(val label: PhotoLabel) : MyStyleEvent
    data object ConsentGiven : MyStyleEvent
    data object ConsentDismissed : MyStyleEvent
    data object PickerOpened : MyStyleEvent
    /** null = the user closed the picker without choosing. */
    data class PhotoPicked(val uri: String?, val label: PhotoLabel) : MyStyleEvent
    data class DeletePhoto(val photoId: String) : MyStyleEvent
    data object MessageShown : MyStyleEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MyStyleViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val styleRepository: StyleRepository,
    salonRepository: SalonRepository,
    private val compressor: PhotoCompressor,
) : ViewModel() {

    private val user: Flow<UserProfile> = authRepository.authState
        .filterIsInstance<AuthState.SignedIn>().map { it.user }.distinctUntilChanged()

    private data class Stored(val profile: StyleProfile?, val photos: List<StylePhoto>, val error: DataError?)

    private val stored: Flow<Stored> = user.flatMapLatest { u ->
        combine(styleRepository.observeProfile(u.uid), styleRepository.observePhotos(u.uid)) { p, photos ->
            Stored(p, photos, null)
        }.onStart { emit(Stored(null, emptyList(), null)) }
            .catch { emit(Stored(StyleProfile(), emptyList(), it.toDataError())) }
    }

    private val staff: Flow<List<Pair<Staff, String>>> = salonRepository.observeSalons()
        .flatMapLatest { salons ->
            if (salons.isEmpty()) flowOf(emptyList())
            else combine(salons.map { s -> salonRepository.observeStaff(s.id).map { list -> list.map { it to s.name } } }) { all ->
                all.flatMap { it }
            }
        }
        .catch { emit(emptyList()) } // the favourite picker is optional; the rest still works

    /** Screen-only state: the draft and in-flight operations. */
    private data class Local(
        val draft: StyleProfile? = null,
        val saving: Boolean = false,
        val addingPhoto: Boolean = false,
        val askConsentFor: PhotoLabel? = null,
        val pickPhotoLabel: PhotoLabel? = null,
        val message: StyleMessage? = null,
    )

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<MyStyleUiState> = combine(user, stored, staff, local) { u, stored, staff, local ->
        val saved = stored.profile ?: StyleProfile()
        val draft = local.draft ?: saved
        MyStyleUiState(
            loading = stored.profile == null,
            user = u,
            draft = draft,
            dirty = local.draft != null && local.draft != saved,
            saving = local.saving,
            photos = stored.photos,
            barbers = staff.filter { it.first.active }
                .map { (s, salon) -> BarberChoice(s, salon, BarberMatcher.score(draft, s)) }
                .sortedWith(compareByDescending<BarberChoice> { it.match ?: -1 }.thenByDescending { it.staff.rating }),
            addingPhoto = local.addingPhoto,
            askConsentFor = local.askConsentFor,
            pickPhotoLabel = local.pickPhotoLabel,
            message = local.message,
            error = stored.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyStyleUiState())

    fun onEvent(event: MyStyleEvent) {
        when (event) {
            is MyStyleEvent.ToggleTag -> edit { p ->
                p.copy(tags = if (event.tag in p.tags) p.tags - event.tag else p.tags + event.tag)
            }
            is MyStyleEvent.NoteChanged -> edit { it.copy(note = event.note.take(StyleProfile.MAX_NOTE)) }
            is MyStyleEvent.ShareChanged -> edit { it.copy(shareWithBarber = event.share) }
            is MyStyleEvent.PreferredBarber -> edit { it.copy(preferredStaffId = event.staffId) }
            MyStyleEvent.Save -> save()
            is MyStyleEvent.AddPhoto -> {
                if (!uiState.value.canAddPhoto) return
                if (uiState.value.draft.photoConsent) local.update { it.copy(pickPhotoLabel = event.label) }
                else local.update { it.copy(askConsentFor = event.label) }
            }
            MyStyleEvent.ConsentGiven -> giveConsent()
            MyStyleEvent.ConsentDismissed -> local.update { it.copy(askConsentFor = null) }
            MyStyleEvent.PickerOpened -> local.update { it.copy(pickPhotoLabel = null) }
            is MyStyleEvent.PhotoPicked -> event.uri?.let { addPhoto(it, event.label) }
            is MyStyleEvent.DeletePhoto -> run {
                withUser { uid -> styleRepository.deletePhoto(uid, event.photoId); null }
            }
            MyStyleEvent.MessageShown -> local.update { it.copy(message = null) }
        }
    }

    private fun edit(change: (StyleProfile) -> StyleProfile) =
        local.update { it.copy(draft = change(it.draft ?: uiState.value.draft)) }

    private fun save() {
        val state = uiState.value
        if (state.saving || !state.dirty) return
        local.update { it.copy(saving = true) }
        withUser(after = { ok -> local.update { it.copy(saving = false, draft = if (ok) null else it.draft) } }) { uid ->
            styleRepository.saveProfile(uid, state.draft)
            StyleMessage.Saved
        }
    }

    /**
     * Consent is saved together with the stored profile (not the unsaved draft), then the
     * picker opens. WHY first: the rules refuse photos until consent is stored.
     */
    private fun giveConsent() {
        val label = local.value.askConsentFor ?: return
        local.update { it.copy(askConsentFor = null) }
        withUser(after = { ok -> if (ok) local.update { it.copy(pickPhotoLabel = label) } }) { uid ->
            val stored = styleRepository.observeProfile(uid).first()
            styleRepository.saveProfile(uid, stored.copy(photoConsent = true))
            local.update { it.copy(draft = it.draft?.copy(photoConsent = true)) }
            null
        }
    }

    private fun addPhoto(uri: String, label: PhotoLabel) {
        if (local.value.addingPhoto) return
        local.update { it.copy(addingPhoto = true) }
        withUser(after = { local.update { it.copy(addingPhoto = false) } }) { uid ->
            val jpeg = compressor.compress(uri)
            styleRepository.addPhoto(uid, jpeg, label)
            StyleMessage.PhotoAdded
        }
    }

    /** Runs [block] for the signed-in user; errors become a [StyleMessage.Failed] banner. */
    private fun withUser(after: (ok: Boolean) -> Unit = {}, block: suspend (uid: String) -> StyleMessage?) {
        viewModelScope.launch {
            val ok = try {
                val uid = user.first().uid
                block(uid)?.let { msg -> local.update { it.copy(message = msg) } }
                true
            } catch (e: DataException) {
                local.update { it.copy(message = StyleMessage.Failed(e.error)) }
                false
            }
            after(ok)
        }
    }
}
