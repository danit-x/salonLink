// Covers: US-01, FR-01 (log in), T01 · Design: new screen in the Figma visual language
package com.group12.salonlink.feature.auth.signin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.group12.salonlink.R
import com.group12.salonlink.core.designsystem.component.GradientButton
import com.group12.salonlink.core.designsystem.component.SalonTextField
import com.group12.salonlink.core.designsystem.theme.SalonLinkTheme
import com.group12.salonlink.core.designsystem.theme.Spacing
import com.group12.salonlink.domain.model.AuthError
import com.group12.salonlink.feature.auth.AuthScaffold
import com.group12.salonlink.feature.auth.AuthSwitchPrompt
import com.group12.salonlink.feature.auth.BannerTone
import com.group12.salonlink.feature.auth.FormBanner
import com.group12.salonlink.feature.auth.PasswordVisibilityToggle
import com.group12.salonlink.feature.auth.messageRes

/**
 * "Route" composable: connects the screen to its ViewModel.
 * Kept separate from [SignInScreen] so the screen itself can be previewed and UI-tested
 * with any state, without Hilt or Firebase.
 */
@Composable
fun SignInRoute(
    onBack: () -> Unit,
    onCreateAccount: () -> Unit,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    // Lifecycle-aware: stops collecting while the app is in the background.
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SignInScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onCreateAccount = onCreateAccount,
    )
}

@Composable
fun SignInScreen(
    uiState: SignInUiState,
    onEvent: (SignInEvent) -> Unit,
    onBack: () -> Unit,
    onCreateAccount: () -> Unit,
) {
    val focusManager = LocalFocusManager.current

    AuthScaffold(
        eyebrow = stringResource(R.string.signin_eyebrow),
        headline = stringResource(R.string.signin_headline),
        body = stringResource(R.string.signin_body),
        onBack = onBack,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SalonTextField(
                value = uiState.email,
                onValueChange = { onEvent(SignInEvent.EmailChanged(it)) },
                label = stringResource(R.string.auth_email_label),
                placeholder = stringResource(R.string.auth_email_placeholder),
                error = uiState.emailError?.let { stringResource(it.messageRes()) },
                enabled = !uiState.isSubmitting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                // Lets password managers / Google autofill offer saved logins.
                modifier = Modifier.semantics { contentType = ContentType.Username + ContentType.EmailAddress },
            )
            SalonTextField(
                value = uiState.password,
                onValueChange = { onEvent(SignInEvent.PasswordChanged(it)) },
                label = stringResource(R.string.auth_password_label),
                error = uiState.passwordError?.let { stringResource(it.messageRes()) },
                enabled = !uiState.isSubmitting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    onEvent(SignInEvent.Submit)
                }),
                visualTransformation = if (uiState.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingContent = {
                    PasswordVisibilityToggle(uiState.passwordVisible) { onEvent(SignInEvent.TogglePasswordVisibility) }
                },
                modifier = Modifier.semantics { contentType = ContentType.Password },
            )
        }

        // "Forgot password?" aligned right, under the password field.
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            TextButton(onClick = { onEvent(SignInEvent.ForgotPassword) }, enabled = !uiState.isSubmitting) {
                Text(
                    stringResource(R.string.signin_forgot_password),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // Error or confirmation message (only one at a time).
        when {
            uiState.formError != null ->
                FormBanner(stringResource(uiState.formError.messageRes()), BannerTone.ERROR)
            uiState.resetEmailSentTo != null ->
                FormBanner(stringResource(R.string.signin_reset_sent, uiState.resetEmailSentTo), BannerTone.INFO)
        }

        Spacer(Modifier.height(Spacing.lg))
        GradientButton(
            text = stringResource(R.string.signin_submit),
            onClick = {
                focusManager.clearFocus()
                onEvent(SignInEvent.Submit)
            },
            loading = uiState.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.sm))
        AuthSwitchPrompt(
            prompt = stringResource(R.string.signin_no_account),
            action = stringResource(R.string.signin_create_account),
            onClick = onCreateAccount,
        )
    }
}

@Preview(showSystemUi = true)
@Composable
private fun SignInScreenPreview() {
    SalonLinkTheme {
        SignInScreen(
            uiState = SignInUiState(email = "kavindu@example.com", formError = AuthError.INVALID_CREDENTIALS),
            onEvent = {},
            onBack = {},
            onCreateAccount = {},
        )
    }
}
