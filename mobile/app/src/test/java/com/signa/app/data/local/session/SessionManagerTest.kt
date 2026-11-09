package com.signa.app.data.local.session

import com.signa.app.domain.model.Session
import com.signa.app.domain.model.SessionSource
import com.signa.app.domain.model.User
import com.signa.app.support.MemorySessionStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {
    private val base = 1_800_000_000_000L
    private fun session(id: String, expires: Long, source: SessionSource = SessionSource.FAKE) =
        Session(id, User("u", "An", "an@example.com"), expires, source)

    @Test fun restoresAndAutomaticallyExpiresSession() = runTest {
        val clock = object : Clock() {
            override fun getZone(): ZoneId = ZoneOffset.UTC
            override fun withZone(zone: ZoneId): Clock = this
            override fun instant(): Instant = Instant.ofEpochMilli(millis())
            override fun millis(): Long = base + testScheduler.currentTime
        }
        val stored = session("a", base + 1_000)
        val store = MemorySessionStore(stored)
        val manager = SessionManager(store, clock, SessionSource.FAKE, backgroundScope)
        runCurrent()
        assertEquals(stored, manager.session.first())
        advanceTimeBy(1_001)
        runCurrent()
        assertNull(manager.current)
        assertNull(store.current)
    }

    @Test fun expiredOrWrongBackendSessionIsDiscarded() = runTest {
        val clock = Clock.fixed(Instant.ofEpochMilli(base), ZoneOffset.UTC)
        val expired = MemorySessionStore(session("old", base - 1))
        val manager = SessionManager(expired, clock, SessionSource.FAKE, backgroundScope)
        runCurrent()
        assertNull(manager.session.first())
        assertNull(expired.current)
        val wrongSource = MemorySessionStore(session("remote", base + 60_000, SessionSource.REMOTE))
        val other = SessionManager(wrongSource, clock, SessionSource.FAKE, backgroundScope)
        runCurrent()
        assertNull(other.session.first())
        assertNull(wrongSource.current)
    }

    @Test fun staleUnauthorizedResponseCannotClearNewLogin() = runTest {
        val clock = Clock.fixed(Instant.ofEpochMilli(base), ZoneOffset.UTC)
        val store = MemorySessionStore(session("old", base + 60_000))
        val manager = SessionManager(store, clock, SessionSource.FAKE, backgroundScope)
        runCurrent()
        val replacement = session("new", base + 90_000)
        manager.save(replacement)
        manager.clear("old")
        runCurrent()
        assertEquals(replacement, manager.current)
        assertEquals(replacement, store.current)
    }

    @Test fun failedExpiryCleanupReturnsToGuestAndCanObserveNextSession() = runTest {
        val state = MutableStateFlow<Session?>(session("expired", base - 1))
        val store = object : SessionStore {
            override val sessions = state
            override suspend fun save(session: Session) { state.value = session }
            override suspend fun clear(expectedSessionId: String?) { throw IOException("Storage unavailable") }
        }
        val manager = SessionManager(store, Clock.fixed(Instant.ofEpochMilli(base), ZoneOffset.UTC), SessionSource.FAKE, backgroundScope)
        runCurrent()
        assertNull(manager.session.first())
        manager.revalidate()
        runCurrent()
        assertNull(manager.current)
        val replacement = session("new", base + 60_000)
        manager.save(replacement)
        runCurrent()
        assertEquals(replacement, manager.current)
    }
}
