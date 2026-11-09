package com.signa.app.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthFormValidatorTest {
    @Test
    fun registrationReportsMissingFields() {
        val errors = AuthFormValidator.validate(AuthMode.Register, AuthFormState())
        assertEquals(AuthMode.Register.fields.toSet(), errors.keys)
        assertTrue(errors.values.all { it == AuthFieldError.Required })
    }

    @Test
    fun registrationChecksEmailAndPasswordConfirmation() {
        val form = AuthFormState(mapOf(
            AuthField.DisplayName to "An",
            AuthField.Email to "bad email",
            AuthField.Password to "abcdefgh",
            AuthField.Confirmation to "different"
        ))
        val errors = AuthFormValidator.validate(AuthMode.Register, form)
        assertEquals(AuthFieldError.InvalidEmail, errors[AuthField.Email])
        assertEquals(AuthFieldError.PasswordMismatch, errors[AuthField.Confirmation])
    }

    @Test
    fun forgotPasswordOnlyRequiresEmail() {
        val form = AuthFormState().withValue(AuthField.Email, " an@example.com ")
        assertTrue(AuthFormValidator.validate(AuthMode.ForgotPassword, form).isEmpty())
    }

    @Test
    fun otpInputFiltersNonAsciiDigitsAndLimitsLength() {
        val form = AuthFormState().withValue(AuthField.Otp, "a12٣345678")
        assertEquals("123456", form[AuthField.Otp])
    }

    @Test
    fun changePasswordRequiresCurrentPassword() {
        val form = AuthFormState(mapOf(
            AuthField.Password to "abcdefgh",
            AuthField.Confirmation to "abcdefgh"
        ))
        assertEquals(
            mapOf(AuthField.OldPassword to AuthFieldError.Required),
            AuthFormValidator.validate(AuthMode.ChangePassword, form)
        )
    }
}
