package com.group12.salonlink.feature.auth.register

import com.group12.salonlink.domain.model.AuthError
import com.group12.salonlink.domain.model.AuthState
import com.group12.salonlink.domain.validation.FieldError
import com.group12.salonlink.testing.FakeAuthRepository
import com.group12.salonlink.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Covers US-01 (register) at the ViewModel level. */
class RegisterViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repo: FakeAuthRepository
    private lateinit var viewModel: RegisterViewModel

    @Before
    fun setUp() {
        repo = FakeAuthRepository()
        viewModel = RegisterViewModel(repo)
    }

    private fun fillForm(name: String, email: String, phone: String, password: String) {
        viewModel.onEvent(RegisterEvent.NameChanged(name))
        viewModel.onEvent(RegisterEvent.EmailChanged(email))
        viewModel.onEvent(RegisterEvent.PhoneChanged(phone))
        viewModel.onEvent(RegisterEvent.PasswordChanged(password))
    }

    @Test
    fun `all field problems are shown at once`() {
        fillForm(name = "", email = "bad", phone = "123", password = "short")
        viewModel.onEvent(RegisterEvent.Submit)

        val s = viewModel.uiState.value
        assertEquals(FieldError.REQUIRED, s.nameError)
        assertEquals(FieldError.INVALID_EMAIL, s.emailError)
        assertEquals(FieldError.INVALID_PHONE, s.phoneError)
        assertEquals(FieldError.PASSWORD_TOO_SHORT, s.passwordError)
        assertNull(repo.lastRegistered)
    }

    @Test
    fun `a valid form creates the account and signs in`() {
        fillForm("Kavindu Perera", "kavindu@example.com", "077 123 4567", "correct-horse")
        viewModel.onEvent(RegisterEvent.Submit)

        assertEquals(Triple("Kavindu Perera", "kavindu@example.com", "077 123 4567"), repo.lastRegistered)
        assertTrue(repo.authState.value is AuthState.SignedIn)
    }

    @Test
    fun `an empty phone is sent as null, not an empty string`() {
        fillForm("Kavindu Perera", "kavindu@example.com", "", "correct-horse")
        viewModel.onEvent(RegisterEvent.Submit)

        assertNull(repo.lastRegistered?.third)
    }

    @Test
    fun `an email that is already registered shows a helpful error`() {
        repo.accounts["kavindu@example.com"] = "x"
        fillForm("Kavindu Perera", "kavindu@example.com", "", "correct-horse")
        viewModel.onEvent(RegisterEvent.Submit)

        assertEquals(AuthError.EMAIL_ALREADY_IN_USE, viewModel.uiState.value.formError)
    }
}
