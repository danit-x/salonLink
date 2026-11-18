package com.group12.salonlink.feature.auth.register

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

/** Snapshot of the Register form. */
data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val nameError: FieldError? = null,
    val emailError: FieldError? = null,
    val phoneError: FieldError? = null,
    val passwordError: FieldError? = null,
    val isSubmitting: Boolean = false,
    val formError: AuthError? = null,
)

sealed interface RegisterEvent {
    data class NameChanged(val value: String) : RegisterEvent
    data class EmailChanged(val value: String) : RegisterEvent
    data class PhoneChanged(val value: String) : RegisterEvent
    data class PasswordChanged(val value: String) : RegisterEvent
    data object TogglePasswordVisibility : RegisterEvent
    data object Submit : RegisterEvent
}

/**
 * Covers US-01 / FR-01 (register). Same UiState + onEvent pattern as [com.group12.salonlink.feature.auth.signin.SignInViewModel].
 *
 * The role is NOT chosen here. Customers self-register, and staff/owners get their role from
 * an invite created by their salon. A modified app therefore can't make itself an owner, and
 * the Firestore rules enforce the same thing on the server.
 */
@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onEvent(event: RegisterEvent) {
        when (event) {
            is RegisterEvent.NameChanged -> _uiState.update { it.copy(name = event.value, nameError = null, formError = null) }
            is RegisterEvent.EmailChanged -> _uiState.update { it.copy(email = event.value, emailError = null, formError = null) }
            is RegisterEvent.PhoneChanged -> _uiState.update { it.copy(phone = event.value, phoneError = null, formError = null) }
            is RegisterEvent.PasswordChanged -> _uiState.update { it.copy(password = event.value, passwordError = null, formError = null) }
            RegisterEvent.TogglePasswordVisibility -> _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }
            RegisterEvent.Submit -> submit()
        }
    }

    private fun submit() {
        val s = _uiState.value
        if (s.isSubmitting) return

        // Validate every field at once, so all problems show together.
        val validated = s.copy(
            nameError = FormValidators.name(s.name),
            emailError = FormValidators.email(s.email),
            phoneError = FormValidators.phone(s.phone),
            passwordError = FormValidators.password(s.password),
        )
        if (listOf(validated.nameError, validated.emailError, validated.phoneError, validated.passwordError).any { it != null }) {
            _uiState.value = validated
            return
        }

        _uiState.update { it.copy(isSubmitting = true, formError = null) }
        viewModelScope.launch {
            try {
                authRepository.register(
                    name = s.name,
                    email = s.email,
                    phone = s.phone.ifBlank { null },
                    password = s.password,
                )
                // Success: the root screen switches to the new user's home automatically.
                _uiState.update { it.copy(isSubmitting = false) }
            } catch (e: AuthException) {
                _uiState.update { it.copy(isSubmitting = false, formError = e.error) }
            }
        }
    }
}
