package com.signa.app.data.remote

import com.signa.app.data.local.session.SessionManager
import com.signa.app.data.remote.api.AuthApi
import com.signa.app.data.remote.dto.LoginRequest
import com.signa.app.data.remote.interceptor.SessionInterceptor
import com.signa.app.data.repository.RemoteAuthRepository
import com.signa.app.domain.model.AuthError
import com.signa.app.domain.model.AuthException
import com.signa.app.domain.model.Session
import com.signa.app.domain.model.SessionSource
import com.signa.app.domain.model.User
import com.signa.app.support.MemorySessionStore
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ApiClientTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val now = 1_800_000_000_000L
    private val clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)
    private fun session(id: String, source: SessionSource = SessionSource.REMOTE) =
        Session(id, User("u", "An", "an@example.com"), now + 60_000, source)

    @Test fun normalizesBaseUrlAndSerializesLoginDto() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val config = ApiConfig(server.url("/").toString())
            val manager = SessionManager(MemorySessionStore(), clock, SessionSource.REMOTE, backgroundScope)
            runCurrent()
            val client = ApiClientFactory.client(SessionInterceptor(config, manager))
            val api = ApiClientFactory.retrofit(config, client, json).create(AuthApi::class.java)
            server.enqueue(MockResponse().setBody("""{"sessionId":"s","expiresAt":"2030-01-01T00:00:00Z","user":{"id":"u","displayName":"An","unknown":true},"extra":1}"""))
            val response = api.login(LoginRequest("an@example.com", "example-password"))
            assertEquals("s", response.sessionId)
            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("/api/v1/auth/login", request.path)
            assertEquals("POST", request.method)
            assertNull(request.getHeader("Authorization"))
            assertNotNull(request.getHeader("X-Request-Id"))
            assertTrue(request.body.readUtf8().contains("\"email\":\"an@example.com\""))
        } finally { server.shutdown() }
    }

    @Test fun parsesStructuredHttpErrorAndMalformedErrorBody() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val config = ApiConfig(server.url("/api/v1/").toString())
            val manager = SessionManager(MemorySessionStore(), clock, SessionSource.REMOTE, backgroundScope)
            runCurrent()
            val api = ApiClientFactory.retrofit(config, ApiClientFactory.client(SessionInterceptor(config, manager)), json).create(AuthApi::class.java)
            server.enqueue(MockResponse().setResponseCode(409).setBody("""{"code":"CONFLICT","message":"Email exists","requestId":"r1","details":[{"field":"email","issue":"duplicate"}]}"""))
            try {
                apiCall(json) { api.register(com.signa.app.data.remote.dto.RegisterRequest("An", "an@example.com", "password")) }
                fail("Expected HTTP error")
            } catch (error: ApiException) {
                assertEquals(409, error.status)
                assertEquals("r1", error.error?.requestId)
                assertEquals("email", error.error?.details?.single()?.field)
            }
            server.enqueue(MockResponse().setResponseCode(503).setBody("not json"))
            try {
                apiCall(json) { api.currentUser() }
                fail("Expected server error")
            } catch (error: ApiException) {
                assertEquals(503, error.status)
                assertNull(error.error)
            }
        } finally { server.shutdown() }
    }

    @Test fun authenticated401ClearsSessionButAnonymous401DoesNot() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val store = MemorySessionStore(session("token-a"))
            val manager = SessionManager(store, clock, SessionSource.REMOTE, backgroundScope)
            runCurrent()
            val config = ApiConfig(server.url("/api/v1/").toString())
            val client = ApiClientFactory.client(SessionInterceptor(config, manager))
            server.enqueue(MockResponse().setResponseCode(401))
            client.newCall(Request.Builder().url(server.url("/api/v1/auth/login")).build()).execute().use { assertEquals(401, it.code) }
            assertEquals("token-a", store.current?.id)
            assertNull(server.takeRequest().getHeader("Authorization"))
            server.enqueue(MockResponse().setResponseCode(401))
            client.newCall(Request.Builder().url(server.url("/api/v1/users/me")).build()).execute().use { assertEquals(401, it.code) }
            assertEquals("Bearer token-a", server.takeRequest().getHeader("Authorization"))
            assertNull(store.current)
        } finally { server.shutdown() }
    }

    @Test fun neverAddsFakeTokenOrTokenForAnotherOrigin() = runTest {
        val server = MockWebServer()
        val external = MockWebServer()
        server.start()
        external.start()
        try {
            val store = MemorySessionStore(session("fake-token", SessionSource.FAKE))
            val manager = SessionManager(store, clock, SessionSource.FAKE, backgroundScope)
            runCurrent()
            val config = ApiConfig(server.url("/api/v1/").toString())
            val client = ApiClientFactory.client(SessionInterceptor(config, manager))
            server.enqueue(MockResponse().setBody("ok"))
            client.newCall(Request.Builder().url(server.url("/api/v1/users/me")).build()).execute().close()
            assertNull(server.takeRequest().getHeader("Authorization"))
            external.enqueue(MockResponse().setBody("ok"))
            client.newCall(Request.Builder().url(external.url("/storage/upload")).build()).execute().close()
            val request = external.takeRequest()
            assertNull(request.getHeader("Authorization"))
            assertNull(request.getHeader("X-Request-Id"))
        } finally { server.shutdown(); external.shutdown() }
    }

    @Test fun delayed401CannotEraseReplacementSession() = runTest {
        val server = MockWebServer()
        val store = MemorySessionStore(session("old"))
        val manager = SessionManager(store, clock, SessionSource.REMOTE, backgroundScope)
        runCurrent()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                runBlocking { manager.save(session("new")) }
                return MockResponse().setResponseCode(401)
            }
        }
        server.start()
        try {
            val config = ApiConfig(server.url("/api/v1/").toString())
            val client = ApiClientFactory.client(SessionInterceptor(config, manager))
            client.newCall(Request.Builder().url(server.url("/api/v1/users/me")).build()).execute().close()
            runCurrent()
            assertEquals("new", store.current?.id)
        } finally { server.shutdown() }
    }

    @Test fun timeoutSurfacesAsIoFailure() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val config = ApiConfig(server.url("/api/v1/").toString())
            val manager = SessionManager(MemorySessionStore(), clock, SessionSource.REMOTE, backgroundScope)
            runCurrent()
            val client = ApiClientFactory.client(SessionInterceptor(config, manager)).newBuilder()
                .callTimeout(300, TimeUnit.MILLISECONDS).build()
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            try {
                client.newCall(Request.Builder().url(server.url("/api/v1/users/me")).build()).execute().close()
                fail("Expected timeout")
            } catch (_: IOException) { }
        } finally { server.shutdown() }
    }

    @Test fun remoteRepositoryRestoresServerExpiryAndMapsInvalidLogin() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val store = MemorySessionStore()
            val manager = SessionManager(store, clock, SessionSource.REMOTE, backgroundScope)
            runCurrent()
            val config = ApiConfig(server.url("/api/v1/").toString())
            val api = ApiClientFactory.retrofit(config, ApiClientFactory.client(SessionInterceptor(config, manager)), json).create(AuthApi::class.java)
            val repository = RemoteAuthRepository(api, manager, json, clock)
            server.enqueue(MockResponse().setBody("""{"sessionId":"server-session","expiresAt":"2030-01-01T00:00:00Z","user":{"id":"u","displayName":"An","vehicleType":"CAR"}}"""))
            repository.login("an@example.com", "password")
            assertEquals("server-session", store.current?.id)
            assertEquals(Instant.parse("2030-01-01T00:00:00Z").toEpochMilli(), store.current?.expiresAtEpochMillis)
            assertEquals(SessionSource.REMOTE, store.current?.source)
            server.enqueue(MockResponse().setResponseCode(401).setBody("""{"code":"UNAUTHENTICATED","message":"Wrong credentials"}"""))
            try {
                repository.login("an@example.com", "incorrect")
                fail("Expected bad credentials")
            } catch (error: AuthException) {
                assertEquals(AuthError.InvalidCredentials, error.error)
            }
            assertEquals("server-session", store.current?.id)
        } finally { server.shutdown() }
    }
}
