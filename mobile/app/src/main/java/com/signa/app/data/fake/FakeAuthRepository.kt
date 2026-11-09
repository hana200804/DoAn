package com.signa.app.data.fake

import androidx.room.withTransaction
import android.database.sqlite.SQLiteException
import com.signa.app.data.local.database.SignaDatabase
import com.signa.app.data.local.database.entity.FakeAccountEntity
import com.signa.app.data.local.database.entity.PasswordResetEntity
import com.signa.app.data.local.session.PasswordHasher
import com.signa.app.data.local.session.SessionManager
import com.signa.app.domain.model.AuthError
import com.signa.app.domain.model.AuthException
import com.signa.app.domain.model.PasswordResetChallenge
import com.signa.app.domain.model.Session
import com.signa.app.domain.model.SessionSource
import com.signa.app.domain.model.User
import com.signa.app.domain.model.VehicleType
import com.signa.app.domain.repository.AuthRepository
import java.security.SecureRandom
import java.time.Clock
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Persistent local auth simulator. The UI explicitly labels the displayed OTP as a test code. */
@Singleton
class FakeAuthRepository @Inject constructor(
    private val database: SignaDatabase,
    private val sessions: SessionManager,
    private val passwords: PasswordHasher,
    private val clock: Clock
) : AuthRepository {
    private val accounts = database.accounts()
    private val operations = Mutex()
    private val random = SecureRandom()
    override val session = sessions.session

    override suspend fun register(displayName: String, email: String, password: String) = localOperation {
        require(displayName.isNotBlank() && password.length >= 8)
        val normalized = normalize(email)
        val salt = passwords.newSalt()
        val hash = withContext(Dispatchers.Default) { passwords.hash(password, salt) }
        database.withTransaction {
            if (accounts.findByEmail(normalized) != null) throw AuthException(AuthError.EmailExists)
            accounts.insert(FakeAccountEntity(UUID.randomUUID().toString(), normalized, displayName.trim(), VehicleType.CAR.name, salt, hash))
        }
    }

    override suspend fun login(email: String, password: String) = localOperation {
        val account = accounts.findByEmail(normalize(email)) ?: throw AuthException(AuthError.InvalidCredentials)
        if (!withContext(Dispatchers.Default) { passwords.matches(password, account.passwordSalt, account.passwordHash) }) {
            throw AuthException(AuthError.InvalidCredentials)
        }
        sessions.save(Session(
            id = UUID.randomUUID().toString(),
            user = User(account.id, account.displayName, account.email, VehicleType.valueOf(account.vehicleType)),
            expiresAtEpochMillis = clock.millis() + SESSION_DURATION_MS,
            source = SessionSource.FAKE
        ))
    }

    override suspend fun logout() = localOperation { sessions.clear() }

    override suspend fun requestPasswordReset(email: String): PasswordResetChallenge = localOperation {
        val normalized = normalize(email)
        if (accounts.findByEmail(normalized) == null) throw AuthException(AuthError.AccountMissing)
        val now = clock.millis()
        val previous = accounts.findReset(normalized)
        if (previous != null && now - previous.issuedAtEpochMillis < OTP_COOLDOWN_MS) throw AuthException(AuthError.OtpCooldown)
        val code = random.nextInt(1_000_000).toString().padStart(6, '0')
        val salt = passwords.newSalt()
        val hash = withContext(Dispatchers.Default) { passwords.hash(code, salt) }
        accounts.saveReset(PasswordResetEntity(normalized, salt, hash, now, now + OTP_DURATION_MS))
        PasswordResetChallenge(normalized, now + OTP_DURATION_MS, demoCode = code)
    }

    override suspend fun resetPassword(email: String, otp: String, newPassword: String) = localOperation {
        require(newPassword.length >= 8)
        val normalized = normalize(email)
        val reset = accounts.findReset(normalized) ?: throw AuthException(AuthError.InvalidOtp)
        if (reset.attempts >= MAX_OTP_ATTEMPTS) throw AuthException(AuthError.TooManyAttempts)
        if (clock.millis() >= reset.expiresAtEpochMillis) throw AuthException(AuthError.ExpiredOtp)
        val matches = withContext(Dispatchers.Default) { passwords.matches(otp, reset.codeSalt, reset.codeHash) }
        val newSalt = passwords.newSalt()
        val newHash = if (matches) withContext(Dispatchers.Default) { passwords.hash(newPassword, newSalt) } else ""
        val failure = database.withTransaction {
            val current = accounts.findReset(normalized)
            when {
                clock.millis() >= reset.expiresAtEpochMillis -> AuthError.ExpiredOtp
                current != reset -> AuthError.InvalidOtp
                !matches -> {
                    accounts.saveReset(reset.copy(attempts = reset.attempts + 1))
                    if (reset.attempts + 1 >= MAX_OTP_ATTEMPTS) AuthError.TooManyAttempts else AuthError.InvalidOtp
                }
                else -> {
                    val account = accounts.findByEmail(normalized) ?: throw AuthException(AuthError.AccountMissing)
                    accounts.updatePassword(account.id, newSalt, newHash)
                    accounts.deleteReset(normalized)
                    database.sessions().clearForUser(account.id, SessionSource.FAKE.name)
                    null
                }
            }
        }
        if (failure != null) throw AuthException(failure)
    }

    override suspend fun changePassword(oldPassword: String, newPassword: String) = localOperation {
        require(newPassword.length >= 8)
        val current = sessions.current ?: throw AuthException(AuthError.NotSignedIn)
        val account = accounts.findById(current.user.id) ?: throw AuthException(AuthError.NotSignedIn)
        if (!withContext(Dispatchers.Default) { passwords.matches(oldPassword, account.passwordSalt, account.passwordHash) }) {
            throw AuthException(AuthError.WrongPassword)
        }
        val salt = passwords.newSalt()
        val hash = withContext(Dispatchers.Default) { passwords.hash(newPassword, salt) }
        database.withTransaction {
            accounts.updatePassword(account.id, salt, hash)
            accounts.deleteReset(account.email)
            database.sessions().clearForUser(account.id, SessionSource.FAKE.name)
        }
    }

    private suspend fun <T> localOperation(block: suspend () -> T): T = operations.withLock {
        try {
            block()
        } catch (error: SQLiteException) {
            throw AuthException(AuthError.Storage, error)
        }
    }

    private fun normalize(email: String): String {
        val normalized = email.trim().lowercase(Locale.ROOT)
        require(normalized.contains('@') && !normalized.contains(' '))
        return normalized
    }

    companion object {
        const val SESSION_DURATION_MS = 24 * 60 * 60 * 1_000L
        const val OTP_DURATION_MS = 5 * 60 * 1_000L
        const val OTP_COOLDOWN_MS = 30 * 1_000L
        const val MAX_OTP_ATTEMPTS = 5
    }
}
