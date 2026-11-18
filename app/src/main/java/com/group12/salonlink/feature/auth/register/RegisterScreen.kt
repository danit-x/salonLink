// Covers: US-01, FR-01 (register), T01 · Design: new screen in the Figma visual language
package com.group12.salonlink.feature.auth.register

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import com.group12.salonlink.domain.validation.FieldError
import com.group12.salonlink.domain.validation.FormValidators
import com.group12.salonlink.feature.auth.AuthScaffold
import com.group12.salonlink.feature.auth.AuthSwitchPrompt
import com.group12.salonlink.feature.auth.BannerTone
import com.group12.salonlink.feature.auth.FormBanner
import com.group12.salonlink.feature.auth.PasswordVisibilityToggle
import com.group12.salonlink.feature.auth.messageRes

@Composable
fun RegisterRoute(
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RegisterScreen(uiState = uiState, onEvent = viewModel::onEvent, onBack = onBack, onSignIn = onSignIn)
}

@Composable
fun RegisterScreen(
    uiState: RegisterUiState,
    onEvent: (RegisterEvent) -> Unit,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        onEvent(RegisterEvent.Submit)
    }

    AuthScaffold(
        eyebrow = stringResource(R.string.register_eyebrow),
        headline = stringResource(R.string.register_headline),
        body = stringResource(R.string.register_body),
        onBack = onBack,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SalonTextField(
                value = uiState.name,
                onValueChange = { onEvent(RegisterEvent.NameChanged(it)) },
                label = stringResource(R.string.register_name_label),
                placeholder = stringResource(R.string.register_name_placeholder),
                error = uiState.nameError?.let { stringResource(it.messageRes()) },
                enabled = !uiState.isSubmitting,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.semantics { contentType = ContentType.PersonFullName },
            )
            SalonTextField(
                value = uiState.email,
                onValueChange = { onEvent(RegisterEvent.EmailChanged(it)) },
                label = stringResource(R.string.auth_email_label),
                placeholder = stringResource(R.string.auth_email_placeholder),
                error = uiState.emailError?.let { stringResource(it.messageRes()) },
                supportingText = stringResource(R.string.register_email_hint),
                enabled = !uiState.isSubmitting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.semantics { contentType = ContentType.EmailAddress },
            )
            SalonTextField(
                value = uiState.phone,
                onValueChange = { onEvent(RegisterEvent.PhoneChanged(it)) },
                label = stringResource(R.string.register_phone_label),
                placeholder = stringResource(R.string.register_phone_placeholder),
                error = uiState.phoneError?.let { stringResource(it.messageRes()) },
                enabled = !uiState.isSubmitting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                modifier = Modifier.semantics { contentType = ContentType.PhoneNumber },
            )
            SalonTextField(
                value = uiState.password,
                onValueChange = { onEvent(RegisterEvent.PasswordChanged(it)) },
                label = stringResource(R.string.auth_password_label),
                error = uiState.passwordError?.let { stringResource(it.messageRes()) },
                supportingText = stringResource(R.string.register_password_hint, FormValidators.MIN_PASSWORD_LENGTH),
                enabled = !uiState.isSubmitting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                visualTransformation = if (uiState.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingContent = {
                    PasswordVisibilityToggle(uiState.passwordVisible) { onEvent(RegisterEvent.TogglePasswordVisibility) }
                },
                // NewPassword tells password managers to offer a strong generated password.
                modifier = Modifier.semantics { contentType = ContentType.NewPassword },
            )
        }

        if (uiState.formError != null) {
            Spacer(Modifier.height(Spacing.md))
            FormBanner(stringResource(uiState.formError.messageRes()), BannerTone.ERROR)
        }

        Spacer(Modifier.height(Spacing.xl))
        GradientButton(
            text = stringResource(R.string.register_submit),
            onClick = submit,
            loading = uiState.isSubmitting,
            trailingIcon = R.drawable.ic_arrow_right,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.sm))
        AuthSwitchPrompt(
            prompt = stringResource(R.string.register_have_account),
            action = stringResource(R.string.register_sign_in),
            onClick = onSignIn,
        )
    }
}

@Preview(showSystemUi = true)
@Composable
private fun RegisterScreenPreview() {
    SalonLinkTheme {
        RegisterScreen(
            uiState = RegisterUiState(name = "Kavindu Perera", email = "kavindu@", emailError = FieldError.INVALID_EMAIL),
            onEvent = {},
            onBack = {},
            onSignIn = {},
        )
    }
}
