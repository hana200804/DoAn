package com.signa.app.ui.auth

/** Client-side feedback only. The account service remains authoritative. */
object AuthFormValidator {
    private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun validate(mode: AuthMode, form: AuthFormState): Map<AuthField, AuthFieldError> {
        val errors = mutableMapOf<AuthField, AuthFieldError>()

        mode.fields.forEach { field ->
            if (form[field].isBlank()) {
                errors[field] = AuthFieldError.Required
            }
        }

        if (AuthField.Email in mode.fields && AuthField.Email !in errors) {
            if (!emailPattern.matches(form[AuthField.Email].trim())) {
                errors[AuthField.Email] = AuthFieldError.InvalidEmail
            }
        }

        if (AuthField.Password in mode.fields && AuthField.Password !in errors) {
            if (form[AuthField.Password].length < 8) {
                errors[AuthField.Password] = AuthFieldError.PasswordTooShort
            }
        }

        if (AuthField.Confirmation in mode.fields && AuthField.Confirmation !in errors) {
            if (form[AuthField.Confirmation] != form[AuthField.Password]) {
                errors[AuthField.Confirmation] = AuthFieldError.PasswordMismatch
            }
        }

        if (AuthField.Otp in mode.fields && AuthField.Otp !in errors) {
            if (!form[AuthField.Otp].matches(Regex("[0-9]{6}"))) {
                errors[AuthField.Otp] = AuthFieldError.InvalidOtp
            }
        }

        return errors
    }
}
