package com.signa.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RegisterRequest(val displayName: String, val email: String, val password: String)

@Serializable
data class ForgotPasswordRequest(val email: String)

@Serializable
data class ResetPasswordRequest(val email: String, val otp: String, val newPassword: String)

@Serializable
data class ChangePasswordRequest(val oldPassword: String, val newPassword: String)

@Serializable
data class UserDto(
    val id: String,
    val displayName: String,
    val email: String? = null,
    val role: String = "DRIVER",
    val vehicleType: String = "CAR"
)

@Serializable
data class LoginResponse(val sessionId: String, val expiresAt: String, val user: UserDto)

@Serializable
data class ApiFieldError(val field: String, val issue: String)

@Serializable
data class ApiErrorDto(
    val code: String,
    val message: String,
    val details: List<ApiFieldError> = emptyList(),
    val requestId: String? = null
)
