// Covers: US-09, FR-10, P2 blocked time (owner manages staff, hours, time off, invites)
package com.group12.salonlink.feature.owner.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group12.salonlink.core.navigation.OwnerStaffEditorRoute
import com.group12.salonlink.core.util.SalonTime
import com.group12.salonlink.domain.model.AuthState
import com.group12.salonlink.domain.model.DataError
import com.group12.salonlink.domain.model.DataException
import com.group12.salonlink.domain.model.Staff
import com.group12.salonlink.domain.model.StaffBlock
import com.group12.salonlink.domain.model.WeeklyHours
import com.group12.salonlink.domain.repository.AuthRepository
import com.group12.salonlink.domain.repository.SalonAdminRepository
import com.group12.salonlink.domain.repository.SalonRepository
import com.group12.salonlink.domain.validation.FormValidators
import com.group12.salonlink.feature.owner.HoursDraft
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The staff member as being edited. */
data class StaffDraft(
    val name: String = "",
    val bio: String = "",
    val specialties: String = "",
    val years: String = "0",
    val active: Boolean = true,
    val hours: HoursDraft = HoursDraft.from(WeeklyHours.CLOSED),
) {
    val nameValid: Boolean get() = name.trim().length in 1..60
    val isValid: Boolean get() = nameValid && hours.isValid
}

/** The "block time" mini form. */
data class BlockDraft(
    val date: LocalDate,
    val from: LocalTime = LocalTime.of(13, 0),
    val to: LocalTime = LocalTime.of(13, 30),
    val reason: String = "",
) {
    val isValid: Boolean get() = from < to
}

data class StaffEditorUiState(
    val loading: Boolean = true,
    val staffId: String = "",
    val draft: StaffDraft = StaffDraft(),
    val showErrors: Boolean = false,
    val blocks: List<StaffBlock> = emptyList(),
    val blockDraft: BlockDraft = BlockDraft(LocalDate.MIN),
    val inviteEmail: String = "",
    val inviteError: Boolean = false,
    val busy: Boolean = false,
    val message: StaffEditorMessage? = null,
)

sealed interface StaffEditorMessage {
    data object Saved : StaffEditorMessage
    data class InviteSent(val email: String) : StaffEditorMessage
    data class Failed(val error: DataError) : StaffEditorMessage
}

sealed interface StaffEditorEvent {
    data class Change(val draft: StaffDraft) : StaffEditorEvent
    data object Save : StaffEditorEvent
    data class ChangeBlock(val draft: BlockDraft) : StaffEditorEvent
    data object AddBlock : StaffEditorEvent
    data class DeleteBlock(val blockId: String) : StaffEditorEvent
    data class InviteEmailChanged(val value: String) : StaffEditorEvent
    data object SendInvite : StaffEditorEvent
    data object MessageShown : StaffEditorEvent
}

/**
 * Add or edit one staff member. Time off and the app invite need a saved staff record, so
 * they appear once the person has been saved (a new person gets their id on first save).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = StaffEditorViewModel.Factory::class)
class StaffEditorViewModel @AssistedInject constructor(
    @Assisted args: OwnerStaffEditorRoute,
    private val authRepository: AuthRepository,
    private val salonRepository: SalonRepository,
    private val admin: SalonAdminRepository,
    private val clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(args: OwnerStaffEditorRoute): StaffEditorViewModel
    }

    private val today = LocalDate.now(clock.withZone(SalonTime.SALON_ZONE))
    private val staffId = MutableStateFlow(args.staffId)
    private val form = MutableStateFlow(StaffEditorUiState(staffId = args.staffId, blockDraft = BlockDraft(today.plusDays(1))))
    private var salonId: String? = null
    /** The stored record, so saving keeps fields this screen doesn't edit (rating, photos). */
    private var original: Staff? = null

    init {
        viewModelScope.launch {
            try {
                val sid = authRepository.authState.filterIsInstance<AuthState.SignedIn>().first().user.salonId
                    ?: throw DataException(DataError.PERMISSION_DENIED)
                salonId = sid
                salonIdFlow.value = sid
                val staff = args.staffId.takeIf { it.isNotBlank() }?.let { salonRepository.observeStaffMember(sid, it).first() }
                original = staff
                val draft = if (staff != null) {
                    StaffDraft(staff.name, staff.bio, staff.specialties.joinToString(", "), staff.yearsExperience.toString(), staff.active, HoursDraft.from(staff.workingHours))
                } else {
                    // New people start with the salon's opening hours; the owner adjusts.
                    StaffDraft(hours = HoursDraft.from(salonRepository.observeSalon(sid).first()?.openingHours ?: WeeklyHours.CLOSED))
                }
                form.update { it.copy(loading = false, draft = draft) }
            } catch (e: DataException) {
                form.update { it.copy(loading = false, message = StaffEditorMessage.Failed(e.error)) }
            }
        }
    }

    private val salonIdFlow = MutableStateFlow<String?>(null)

    private val blocks = combine(staffId, salonIdFlow) { id, sid -> id to sid }.flatMapLatest { (id, sid) ->
        if (id.isBlank() || sid == null) flowOf(emptyList())
        else {
            val now = clock.instant()
            salonRepository.observeBlocks(sid, id, now, now.plus(Duration.ofDays(90)))
        }
    }.catch { emit(emptyList()) }

    val uiState: StateFlow<StaffEditorUiState> = combine(form, staffId, blocks) { f, id, b -> f.copy(staffId = id, blocks = b) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), form.value)

    fun onEvent(event: StaffEditorEvent) {
        when (event) {
            is StaffEditorEvent.Change -> form.update { it.copy(draft = event.draft) }
            is StaffEditorEvent.ChangeBlock -> form.update { it.copy(blockDraft = event.draft) }
            is StaffEditorEvent.InviteEmailChanged -> form.update { it.copy(inviteEmail = event.value, inviteError = false) }
            StaffEditorEvent.MessageShown -> form.update { it.copy(message = null) }
            StaffEditorEvent.Save -> save()
            StaffEditorEvent.AddBlock -> addBlock()
            is StaffEditorEvent.DeleteBlock -> run { salonId?.let { sid -> launchBusy { admin.deleteBlock(sid, staffId.value, event.blockId); null } } }
            StaffEditorEvent.SendInvite -> invite()
        }
    }

    private fun save() {
        val d = form.value.draft
        val sid = salonId ?: return
        if (!d.isValid) {
            form.update { it.copy(showErrors = true) }
            return
        }
        val base = original
        val staff = Staff(
            id = staffId.value,
            name = d.name.trim(),
            bio = d.bio.trim().take(300),
            specialties = d.specialties.split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(10),
            rating = base?.rating ?: 0.0,
            reviewCount = base?.reviewCount ?: 0,
            yearsExperience = d.years.toIntOrNull()?.coerceIn(0, 80) ?: 0,
            photoUrl = base?.photoUrl,
            portfolioUrls = base?.portfolioUrls.orEmpty(),
            workingHours = d.hours.toWeeklyHours(),
            active = d.active,
        )
        launchBusy {
            val id = admin.saveStaff(sid, staff)
            original = staff.copy(id = id)
            staffId.value = id
            StaffEditorMessage.Saved
        }
    }

    private fun addBlock() {
        val b = form.value.blockDraft
        val sid = salonId ?: return
        if (!b.isValid || staffId.value.isBlank()) return
        launchBusy {
            admin.addBlock(
                sid, staffId.value,
                StaffBlock("", SalonTime.toInstant(b.date, b.from), SalonTime.toInstant(b.date, b.to), b.reason.trim().take(60)),
            )
            form.update { it.copy(blockDraft = b.copy(reason = "")) }
            StaffEditorMessage.Saved
        }
    }

    private fun invite() {
        val email = form.value.inviteEmail
        val sid = salonId ?: return
        if (FormValidators.email(email) != null || staffId.value.isBlank()) {
            form.update { it.copy(inviteError = true) }
            return
        }
        launchBusy {
            admin.inviteStaff(sid, staffId.value, email)
            form.update { it.copy(inviteEmail = "") }
            StaffEditorMessage.InviteSent(FormValidators.normaliseEmail(email))
        }
    }

    /** Runs a write with the spinner on, then shows its message (or the error). */
    private fun launchBusy(block: suspend () -> StaffEditorMessage?) {
        if (form.value.busy) return
        form.update { it.copy(busy = true) }
        viewModelScope.launch {
            val message = try {
                block()
            } catch (e: DataException) {
                StaffEditorMessage.Failed(e.error)
            }
            form.update { it.copy(busy = false, message = message) }
        }
    }
}
