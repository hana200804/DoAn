package com.signa.app.data.repository

import com.signa.app.data.local.session.SessionManager
import com.signa.app.data.remote.ApiException
import com.signa.app.data.remote.api.AuthApi
import com.signa.app.data.remote.apiCall
import com.signa.app.data.remote.dto.ChangePasswordRequest
import com.signa.app.data.remote.dto.ForgotPasswordRequest
import com.signa.app.data.remote.dto.LoginRequest
import com.signa.app.data.remote.dto.RegisterRequest
import com.signa.app.data.remote.dto.ResetPasswordRequest
import com.signa.app.domain.model.AuthError
import com.signa.app.domain.model.AuthException
import com.signa.app.domain.model.PasswordResetChallenge
import com.signa.app.domain.model.Session
import com.signa.app.domain.model.SessionSource
import com.signa.app.domain.model.User
import com.signa.app.domain.model.VehicleType
import com.signa.app.domain.repository.AuthRepository
import java.io.IOException
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class RemoteAuthRepository @Inject constructor(
    private val api: AuthApi,
    private val sessions: SessionManager,
    private val json: Json,
    private val clock: Clock
) : AuthRepository {
    override val session = sessions.session

    override suspend fun register(displayName: String, email: String, password: String) = request {
        api.register(RegisterRequest(displayName.trim(), email.trim(), password))
    }

    override suspend fun login(email: String, password: String) {
        val response = try {
            request { api.login(LoginRequest(email.trim(), password)) }
        } catch (error: AuthException) {
            if (error.error == AuthError.Unauthorized) throw AuthException(AuthError.InvalidCredentials, error)
            throw error
        }
        val expires = try {
            Instant.parse(response.expiresAt).toEpochMilli()
        } catch (error: java.time.format.DateTimeParseException) {
            throw AuthException(AuthError.InvalidResponse, error)
        }
        if (response.sessionId.isBlank() || response.user.id.isBlank() || expires <= clock.millis()) {
            throw AuthException(AuthError.InvalidResponse)
        }
        val user = User(
            response.user.id, response.user.displayName, response.user.email ?: email.trim(),
            VehicleType.entries.firstOrNull { it.name == response.user.vehicleType } ?: VehicleType.CAR
        )
        sessions.save(Session(response.sessionId, user, expires, SessionSource.REMOTE))
    }

    override suspend fun logout() {
        val current = sessions.current
        try {
            if (current != null) request { api.logout() }
        } finally {
            if (current != null) sessions.clear(current.id)
        }
    }

    override suspend fun requestPasswordReset(email: String): PasswordResetChallenge {
        request { api.forgotPassword(ForgotPasswordRequest(email.trim())) }
        return PasswordResetChallenge(email.trim(), expiresAtEpochMillis = 0)
    }

    override suspend fun resetPassword(email: String, otp: String, newPassword: String) = request {
        api.resetPassword(ResetPasswordRequest(email.trim(), otp, newPassword))
    }

    override suspend fun changePassword(oldPassword: String, newPassword: String) {
        val current = sessions.current ?: throw AuthException(AuthError.NotSignedIn)
        request { api.changePassword(ChangePasswordRequest(oldPassword, newPassword)) }
        sessions.clear(current.id)
    }

    private suspend fun <T> request(call: suspend () -> T): T {
        try {
            return apiCall(json, call)
        } catch (error: ApiException) {
            val code = when (error.status) {
                400, 422 -> AuthError.Validation
                401 -> AuthError.Unauthorized
                403 -> AuthError.Forbidden
                404 -> AuthError.AccountMissing
                409 -> AuthError.EmailExists
                429 -> AuthError.RateLimited
                else -> AuthError.Server
            }
            throw AuthException(code, error)
        } catch (error: SerializationException) {
            throw AuthException(AuthError.InvalidResponse, error)
        } catch (error: IOException) {
            throw AuthException(AuthError.Network, error)
        }
    }
}
