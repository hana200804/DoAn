package com.signa.app.domain.model

enum class AuthError {
    InvalidCredentials, EmailExists, AccountMissing, InvalidOtp, ExpiredOtp,
    TooManyAttempts, OtpCooldown, NotSignedIn, WrongPassword,
    Network, Unauthorized, Forbidden, Validation, RateLimited, Server, InvalidResponse, Storage
}

class AuthException(val error: AuthError, cause: Throwable? = null) : Exception(error.name, cause)
