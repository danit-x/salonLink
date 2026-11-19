// Covers: FR-07 (in-app notifications) · Design: new, in the app's visual language
package com.group12.salonlink.feature.customer.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group12.salonlink.domain.model.AppNotification
import com.group12.salonlink.domain.model.AuthState
import com.group12.salonlink.domain.model.DataError
import com.group12.salonlink.domain.model.DataException
import com.group12.salonlink.domain.repository.AuthRepository
import com.group12.salonlink.domain.repository.NotificationRepository
import com.group12.salonlink.feature.customer.booking.toDataError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val loading: Boolean = true,
    val items: List<AppNotification> = emptyList(),
    val error: DataError? = null,
) {
    val unreadCount: Int get() = items.count { !it.read }
}

/** The customer's notification feed, newest first, live. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val notificationRepository: NotificationRepository,
) : ViewModel() {

    private val actionError = MutableStateFlow<DataError?>(null)

    val uiState: StateFlow<NotificationsUiState> = combine(
        authRepository.authState.filterIsInstance<AuthState.SignedIn>()
            .flatMapLatest { notificationRepository.observeNotifications(it.user.uid) }
            .map { NotificationsUiState(loading = false, items = it) }
            .onStart { emit(NotificationsUiState()) }
            .catch { emit(NotificationsUiState(loading = false, error = it.toDataError())) },
        actionError,
    ) { state, actionError -> if (actionError != null) state.copy(error = actionError) else state }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationsUiState())

    /** Called when a row is opened. */
    fun markRead(notification: AppNotification) {
        if (!notification.read) markRead(listOf(notification.id))
    }

    fun markAllRead() = markRead(uiState.value.items.filter { !it.read }.map { it.id })

    private fun markRead(ids: List<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                val uid = authRepository.authState.filterIsInstance<AuthState.SignedIn>().first().user.uid
                notificationRepository.markRead(uid, ids)
                actionError.update { null }
            } catch (e: DataException) {
                actionError.update { e.error }
            }
        }
    }
}
