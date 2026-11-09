package com.signa.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.signa.app.data.fake.FakeAuthRepository
import com.signa.app.data.fake.FakeData
import com.signa.app.data.local.database.SignaDatabase
import com.signa.app.data.local.session.AndroidSessionCipher
import com.signa.app.data.local.session.PasswordHasher
import com.signa.app.data.local.session.RoomSessionStore
import com.signa.app.data.local.session.SessionManager
import com.signa.app.data.mapper.toEntity
import com.signa.app.data.repository.CachedSignRepository
import com.signa.app.domain.model.AuthError
import com.signa.app.domain.model.AuthException
import com.signa.app.domain.model.Session
import com.signa.app.domain.model.SessionSource
import com.signa.app.domain.model.TrafficSign
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Step2AcceptanceTest {
    private lateinit var context: Context
    private lateinit var database: SignaDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var sessions: SessionManager
    private lateinit var repository: FakeAuthRepository
    private val clock = MutableClock()
    private val cipher = AndroidSessionCipher()
    private val json = Json { ignoreUnknownKeys = true }

    @Before fun prepare() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DATABASE)
        open()
    }

    private fun open(source: SessionSource = SessionSource.FAKE) {
        database = Room.databaseBuilder(context, SignaDatabase::class.java, DATABASE).build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        sessions = SessionManager(RoomSessionStore(database.sessions(), cipher, json), clock, source, scope)
        repository = FakeAuthRepository(database, sessions, PasswordHasher(), clock)
    }

    private suspend fun close() {
        scope.coroutineContext[Job]?.cancelAndJoin()
        database.close()
    }

    @After fun cleanup() = runBlocking {
        close()
        context.deleteDatabase(DATABASE)
        Unit
    }

    private suspend fun current(predicate: (Session?) -> Boolean): Session? =
        withTimeout(10_000) { repository.session.first(predicate) }

    private suspend fun expectError(error: AuthError, call: suspend () -> Unit) {
        try {
            call()
            fail("Expected $error")
        } catch (failure: AuthException) {
            assertEquals(error, failure.error)
        }
    }

    private suspend fun registerAndLogin() {
        repository.register("Test Driver", EMAIL, PASSWORD)
        repository.login(EMAIL, PASSWORD)
        current { it != null }
    }

    @Test fun accountAndEncryptedSessionSurviveRecreationAndLogout() = runBlocking {
        repository.register(" Test Driver ", " STEP2@example.com ", PASSWORD)
        expectError(AuthError.EmailExists) { repository.register("Other", EMAIL, PASSWORD) }
        expectError(AuthError.InvalidCredentials) { repository.login(EMAIL, "incorrect") }
        repository.login(EMAIL, PASSWORD)
        val signedIn = current { it != null }!!
        assertEquals("Test Driver", signedIn.user.displayName)
        val account = database.accounts().findByEmail(EMAIL)!!
        assertNotEquals(PASSWORD, account.passwordHash)
        val row = database.sessions().observe().first()!!
        assertFalse(row.encryptedPayload.contains(signedIn.id))
        assertNotEquals(signedIn.id, row.tokenHash)
        close()
        open()
        assertEquals(signedIn, current { it != null })
        repository.logout()
        assertNull(current { it == null })
        close()
        open()
        assertNull(current { it == null })
    }

    @Test fun registrationIsUniqueUnderConcurrentRequests() = runBlocking {
        val results = coroutineScope {
            (1..2).map { async { runCatching { repository.register("Driver", EMAIL, PASSWORD) } } }.map { it.await() }
        }
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(AuthError.EmailExists, (results.single { it.isFailure }.exceptionOrNull() as AuthException).error)
    }

    @Test fun resetChangesPasswordConsumesOtpAndRevokesSession() = runBlocking {
        registerAndLogin()
        val challenge = repository.requestPasswordReset(EMAIL)
        assertNotNull(challenge.demoCode)
        repository.resetPassword(EMAIL, challenge.demoCode!!, NEW_PASSWORD)
        assertNull(current { it == null })
        expectError(AuthError.InvalidOtp) { repository.resetPassword(EMAIL, challenge.demoCode, PASSWORD) }
        expectError(AuthError.InvalidCredentials) { repository.login(EMAIL, PASSWORD) }
        repository.login(EMAIL, NEW_PASSWORD)
        assertEquals(EMAIL, current { it != null }?.user?.email)
    }

    @Test fun otpAttemptsAndResendCooldownAreEnforced() = runBlocking {
        repository.register("Driver", EMAIL, PASSWORD)
        val challenge = repository.requestPasswordReset(EMAIL)
        expectError(AuthError.OtpCooldown) { repository.requestPasswordReset(EMAIL) }
        val wrongCode = if (challenge.demoCode == "000000") "111111" else "000000"
        repeat(4) { expectError(AuthError.InvalidOtp) { repository.resetPassword(EMAIL, wrongCode, NEW_PASSWORD) } }
        expectError(AuthError.TooManyAttempts) { repository.resetPassword(EMAIL, wrongCode, NEW_PASSWORD) }
        expectError(AuthError.TooManyAttempts) { repository.resetPassword(EMAIL, challenge.demoCode!!, NEW_PASSWORD) }
        clock.now += FakeAuthRepository.OTP_COOLDOWN_MS + 1
        val replacement = repository.requestPasswordReset(EMAIL)
        repository.resetPassword(EMAIL, replacement.demoCode!!, NEW_PASSWORD)
        repository.login(EMAIL, NEW_PASSWORD)
        assertNotNull(current { it != null })
    }

    @Test fun expiredOtpIsRejected() = runBlocking {
        repository.register("Driver", EMAIL, PASSWORD)
        val challenge = repository.requestPasswordReset(EMAIL)
        clock.now = challenge.expiresAtEpochMillis
        expectError(AuthError.ExpiredOtp) { repository.resetPassword(EMAIL, challenge.demoCode!!, NEW_PASSWORD) }
    }

    @Test fun changePasswordRequiresOldPasswordAndInvalidatesSession() = runBlocking {
        registerAndLogin()
        expectError(AuthError.WrongPassword) { repository.changePassword("incorrect", NEW_PASSWORD) }
        assertNotNull(sessions.current)
        repository.changePassword(PASSWORD, NEW_PASSWORD)
        assertNull(current { it == null })
        expectError(AuthError.InvalidCredentials) { repository.login(EMAIL, PASSWORD) }
        repository.login(EMAIL, NEW_PASSWORD)
        assertNotNull(current { it != null })
    }

    @Test fun expiredAndWrongModeSessionsReturnToGuest() = runBlocking {
        registerAndLogin()
        clock.now += FakeAuthRepository.SESSION_DURATION_MS + 1
        sessions.revalidate()
        assertNull(current { it == null })
        repository.login(EMAIL, PASSWORD)
        current { it != null }
        close()
        open(SessionSource.REMOTE)
        assertNull(current { it == null })
    }

    @Test fun roomCacheSurvivesReopeningWithoutReplacingExistingData() = runBlocking {
        val cached = CachedSignRepository(database.signs())
        assertEquals(FakeData.signs.size, cached.observeSigns().first().size)
        val extra = TrafficSign("test-cache-id", "TEST", "Cached sign", "Sample meaning", "Sample location", 40)
        database.signs().insertMissing(listOf(extra.toEntity()))
        close()
        open()
        val restored = CachedSignRepository(database.signs())
        assertEquals(FakeData.signs.size + 1, restored.observeSigns().first().size)
        assertEquals(extra, restored.observeSign(extra.id).first())
        assertNull(restored.observeSign("missing-id").first())
    }

    @Test fun corruptedCiphertextIsRemovedAndReturnsToGuest() = runBlocking {
        registerAndLogin()
        val row = database.sessions().observe().first()!!
        database.sessions().save(row.copy(encryptedPayload = "invalid-payload"))
        assertNull(current { it == null })
        assertNull(database.sessions().observe().first { it == null })
    }

    @Test fun cipherUsesFreshIvAndRoundTripsPayload() {
        val first = cipher.encrypt("session-content")
        val second = cipher.encrypt("session-content")
        assertNotEquals(first, second)
        assertEquals("session-content", cipher.decrypt(first))
        assertEquals("session-content", cipher.decrypt(second))
    }

    private class MutableClock : Clock() {
        @Volatile var now = 1_800_000_000_000L
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun millis(): Long = now
        override fun instant(): Instant = Instant.ofEpochMilli(now)
    }

    private companion object {
        const val DATABASE = "step2-acceptance-test.db"
        const val EMAIL = "step2@example.com"
        const val PASSWORD = "Sample123!"
        const val NEW_PASSWORD = "Changed456!"
    }
}
