package com.signa.app.ui.auth

import com.signa.app.domain.model.AuthError
import com.signa.app.domain.model.PasswordResetChallenge

/** Kept in memory by AuthViewModel. Passwords must not be placed in saved instance state. */
data class AuthFormState(val values: Map<AuthField, String> = emptyMap()) {
    operator fun get(field: AuthField): String = values[field].orEmpty()

    fun withValue(field: AuthField, value: String): AuthFormState {
        val input = if (field == AuthField.Otp) {
            value.filter { it in '0'..'9' }.take(6)
        } else {
            value
        }
        return copy(values = values + (field to input))
    }
}

enum class AuthFieldError {
    Required,
    InvalidEmail,
    PasswordTooShort,
    PasswordMismatch,
    InvalidOtp
}

data class AuthUiState(
    val form: AuthFormState = AuthFormState(),
    val errors: Map<AuthField, AuthFieldError> = emptyMap(),
    val isSubmitting: Boolean = false,
    val requestError: AuthError? = null,
    val resetChallenge: PasswordResetChallenge? = null
)
