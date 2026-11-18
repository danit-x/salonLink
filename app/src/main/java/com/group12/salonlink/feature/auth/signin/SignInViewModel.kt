package com.group12.salonlink.feature.auth.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group12.salonlink.domain.model.AuthError
import com.group12.salonlink.domain.model.AuthException
import com.group12.salonlink.domain.repository.AuthRepository
import com.group12.salonlink.domain.validation.FieldError
import com.group12.salonlink.domain.validation.FormValidators
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Everything the Sign-in screen shows, as one immutable snapshot.
 * The screen just draws this. It never decides anything on its own.
 */
data class SignInUiState(
    val email: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val emailError: FieldError? = null,
    val passwordError: FieldError? = null,
    val isSubmitting: Boolean = false,
    val formError: AuthError? = null,
    /** Set after a password-reset email was requested, to show a confirmation. */
    val resetEmailSentTo: String? = null,
)

/** Everything the user can do on the Sign-in screen. */
sealed interface SignInEvent {
    data class EmailChanged(val value: String) : SignInEvent
    data class PasswordChanged(val value: String) : SignInEvent
    data object TogglePasswordVisibility : SignInEvent
    data object Submit : SignInEvent
    data object ForgotPassword : SignInEvent
}

/**
 * Covers US-01 / FR-01 (log in). Pattern used on every screen:
 *   UI ──onEvent(SignInEvent)──▶ ViewModel ──uiState (StateFlow)──▶ UI
 *
 * Note that success needs no navigation code here. Signing in changes the app-wide
 * AuthState, and the root of the UI swaps to the user's home screen by itself.
 */
@HiltViewModel
class SignInViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    fun onEvent(event: SignInEvent) {
        when (event) {
            // Typing clears that field's error so the user isn't nagged mid-correction.
            is SignInEvent.EmailChanged -> _uiState.update {
                it.copy(email = event.value, emailError = null, formError = null, resetEmailSentTo = null)
            }
            is SignInEvent.PasswordChanged -> _uiState.update {
                it.copy(password = event.value, passwordError = null, formError = null)
            }
            SignInEvent.TogglePasswordVisibility -> _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }
            SignInEvent.Submit -> submit()
            SignInEvent.ForgotPassword -> sendPasswordReset()
        }
    }

    private fun submit() {
        val state = _uiState.value
        if (state.isSubmitting) return // ignore double taps

        // 1) Validate locally first: instant feedback, no wasted network call.
        val emailError = FormValidators.email(state.email)
        val passwordError = FormValidators.passwordPresent(state.password)
        if (emailError != null || passwordError != null) {
            _uiState.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }

        // 2) Ask Firebase.
        _uiState.update { it.copy(isSubmitting = true, formError = null) }
        viewModelScope.launch {
            try {
                authRepository.signIn(state.email, state.password)
                _uiState.update { it.copy(isSubmitting = false) }
            } catch (e: AuthException) {
                _uiState.update { it.copy(isSubmitting = false, formError = e.error) }
            }
        }
    }

    private fun sendPasswordReset() {
        val state = _uiState.value
        val emailError = FormValidators.email(state.email)
        if (emailError != null) {
            _uiState.update { it.copy(emailError = emailError) }
            return
        }
        _uiState.update { it.copy(isSubmitting = true, formError = null) }
        viewModelScope.launch {
            try {
                authRepository.sendPasswordReset(state.email)
                // WHY no "account not found" message: revealing which emails are registered helps attackers.
                _uiState.update { it.copy(isSubmitting = false, resetEmailSentTo = state.email.trim()) }
            } catch (e: AuthException) {
                _uiState.update { it.copy(isSubmitting = false, formError = e.error) }
            }
        }
    }
}
