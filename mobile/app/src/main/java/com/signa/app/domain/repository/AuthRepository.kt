package com.signa.app.domain.repository

import com.signa.app.domain.model.PasswordResetChallenge
import com.signa.app.domain.model.Session
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val session: Flow<Session?>
    suspend fun register(displayName: String, email: String, password: String)
    suspend fun login(email: String, password: String)
    suspend fun logout()
    suspend fun requestPasswordReset(email: String): PasswordResetChallenge
    suspend fun resetPassword(email: String, otp: String, newPassword: String)
    suspend fun changePassword(oldPassword: String, newPassword: String)
}
