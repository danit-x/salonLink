package com.group12.salonlink.feature.auth.signin

import com.group12.salonlink.domain.model.AuthError
import com.group12.salonlink.domain.model.AuthState
import com.group12.salonlink.domain.validation.FieldError
import com.group12.salonlink.testing.FakeAuthRepository
import com.group12.salonlink.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Covers T01 (log in) at the ViewModel level, with no network or device. */
class SignInViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repo: FakeAuthRepository
    private lateinit var viewModel: SignInViewModel

    @Before
    fun setUp() {
        repo = FakeAuthRepository().apply { accounts["kavindu@example.com"] = "correct-horse" }
        viewModel = SignInViewModel(repo)
    }

    private fun typeCredentials(email: String, password: String) {
        viewModel.onEvent(SignInEvent.EmailChanged(email))
        viewModel.onEvent(SignInEvent.PasswordChanged(password))
    }

    @Test
    fun `invalid input is caught locally without calling Firebase`() {
        typeCredentials("kavindu@", "")
        viewModel.onEvent(SignInEvent.Submit)

        val state = viewModel.uiState.value
        assertEquals(FieldError.INVALID_EMAIL, state.emailError)
        assertEquals(FieldError.REQUIRED, state.passwordError)
        assertEquals(0, repo.signInCalls)
    }

    @Test
    fun `correct credentials sign the user in`() {
        typeCredentials("kavindu@example.com", "correct-horse")
        viewModel.onEvent(SignInEvent.Submit)

        assertTrue(repo.authState.value is AuthState.SignedIn)
        assertFalse(viewModel.uiState.value.isSubmitting)
        assertNull(viewModel.uiState.value.formError)
    }

    @Test
    fun `a wrong password shows a friendly error`() {
        typeCredentials("kavindu@example.com", "wrong")
        viewModel.onEvent(SignInEvent.Submit)

        assertEquals(AuthError.INVALID_CREDENTIALS, viewModel.uiState.value.formError)
        assertTrue(repo.authState.value is AuthState.SignedOut)
    }

    @Test
    fun `network failures are reported, not swallowed (NFR-10)`() {
        repo.nextError = AuthError.NETWORK
        typeCredentials("kavindu@example.com", "correct-horse")
        viewModel.onEvent(SignInEvent.Submit)

        assertEquals(AuthError.NETWORK, viewModel.uiState.value.formError)
    }

    @Test
    fun `double-tapping Sign in sends only one request`() {
        repo.gate = CompletableDeferred() // keep the first request "in flight"
        typeCredentials("kavindu@example.com", "correct-horse")

        viewModel.onEvent(SignInEvent.Submit)
        assertTrue(viewModel.uiState.value.isSubmitting)
        viewModel.onEvent(SignInEvent.Submit)

        repo.gate!!.complete(Unit)
        assertEquals(1, repo.signInCalls)
    }

    @Test
    fun `typing again clears the old error`() {
        typeCredentials("kavindu@example.com", "wrong")
        viewModel.onEvent(SignInEvent.Submit)
        viewModel.onEvent(SignInEvent.PasswordChanged("wrong2"))

        assertNull(viewModel.uiState.value.formError)
    }

    @Test
    fun `forgot password needs a valid email, then confirms without revealing if it exists`() {
        viewModel.onEvent(SignInEvent.ForgotPassword)
        assertEquals(FieldError.REQUIRED, viewModel.uiState.value.emailError)

        viewModel.onEvent(SignInEvent.EmailChanged("nobody@example.com"))
        viewModel.onEvent(SignInEvent.ForgotPassword)
        assertEquals("nobody@example.com", viewModel.uiState.value.resetEmailSentTo)
        assertEquals(listOf("nobody@example.com"), repo.resetEmailsSent)
    }
}
