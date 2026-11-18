package com.group12.salonlink

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group12.salonlink.domain.model.AuthState
import com.group12.salonlink.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn

/**
 * App-level state holder: "who is signed in?". MainActivity reads it to keep the splash
 * screen up, and [SalonLinkRoot] reads it to pick the screen tree.
 */
@HiltViewModel
class RootViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    /**
     * Starts as [AuthState.Loading] and never goes back to it once a real state arrived.
     *
     * WHY filter out later Loading values: while signing in or registering, the repository
     * briefly reports Loading as it fetches the profile. If the root reacted, the Sign-in
     * form would vanish mid-request. Instead the form keeps showing its spinner until
     * SignedIn arrives.
     *
     * Eagerly = start listening now, so the answer is ready before the splash ends.
     */
    val authState: StateFlow<AuthState> = authRepository.authState
        .filter { it !is AuthState.Loading }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Loading)

    fun signOut() = authRepository.signOut()
}
