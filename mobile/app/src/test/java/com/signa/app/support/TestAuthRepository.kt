package com.signa.app.support

import com.signa.app.domain.model.AuthError
import com.signa.app.domain.model.AuthException
import com.signa.app.domain.model.PasswordResetChallenge
import com.signa.app.domain.model.Session
import com.signa.app.domain.model.SessionSource
import com.signa.app.domain.model.User
import com.signa.app.domain.repository.AuthRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

class TestAuthRepository : AuthRepository {
    override val session = MutableStateFlow<Session?>(null)
    var failure: AuthError? = null
    var loginCount = 0
    var registerCount = 0
    var resetCount = 0
    var changeCount = 0

    private fun checkFailure() { failure?.let { throw AuthException(it) } }
    override suspend fun login(email: String, password: String) {
        loginCount++
        delay(100)
        checkFailure()
        session.value = Session("test-session", User("u", "An", email), Long.MAX_VALUE, SessionSource.FAKE)
    }
    override suspend fun register(displayName: String, email: String, password: String) { checkFailure(); registerCount++ }
    override suspend fun logout() { session.value = null }
    override suspend fun requestPasswordReset(email: String): PasswordResetChallenge {
        checkFailure()
        return PasswordResetChallenge(email, Long.MAX_VALUE, "123456")
    }
    override suspend fun resetPassword(email: String, otp: String, newPassword: String) { checkFailure(); resetCount++ }
    override suspend fun changePassword(oldPassword: String, newPassword: String) { checkFailure(); changeCount++ }
}
