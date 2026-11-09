package com.signa.app.ui.auth

/** Fields rendered by each account form. These are UI concepts, independent of API DTOs. */
enum class AuthField {
    DisplayName,
    Email,
    Password,
    Confirmation,
    Otp,
    OldPassword
}

enum class AuthMode(val fields: List<AuthField>) {
    Login(listOf(AuthField.Email, AuthField.Password)),
    Register(listOf(AuthField.DisplayName, AuthField.Email, AuthField.Password, AuthField.Confirmation)),
    ForgotPassword(listOf(AuthField.Email)),
    ResetPassword(listOf(AuthField.Email, AuthField.Otp, AuthField.Password, AuthField.Confirmation)),
    ChangePassword(listOf(AuthField.OldPassword, AuthField.Password, AuthField.Confirmation))
}
