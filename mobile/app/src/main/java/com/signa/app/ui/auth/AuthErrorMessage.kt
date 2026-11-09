package com.signa.app.ui.auth

import androidx.annotation.StringRes
import com.signa.app.R
import com.signa.app.domain.model.AuthError

@StringRes
fun authErrorMessage(error: AuthError): Int = when (error) {
    AuthError.InvalidCredentials -> R.string.auth_invalid_credentials
    AuthError.EmailExists -> R.string.auth_email_exists
    AuthError.AccountMissing -> R.string.auth_account_missing
    AuthError.InvalidOtp -> R.string.auth_invalid_otp
    AuthError.ExpiredOtp -> R.string.auth_expired_otp
    AuthError.TooManyAttempts -> R.string.auth_otp_locked
    AuthError.OtpCooldown -> R.string.auth_otp_cooldown
    AuthError.NotSignedIn, AuthError.Unauthorized -> R.string.auth_session_expired
    AuthError.WrongPassword -> R.string.auth_wrong_password
    AuthError.Network -> R.string.auth_network_error
    AuthError.Forbidden -> R.string.auth_forbidden
    AuthError.Validation -> R.string.auth_validation_error
    AuthError.RateLimited -> R.string.auth_rate_limited
    AuthError.Server -> R.string.auth_server_error
    AuthError.InvalidResponse -> R.string.auth_invalid_response
    AuthError.Storage -> R.string.auth_storage_error
}
