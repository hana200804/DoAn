package com.signa.app

import android.app.Application
import android.graphics.Bitmap
import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.net.InetSocketAddress
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** Đổi tài khoản giữa lúc đang gọi mạng không làm lẫn phiên giữa hai người. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class AuthSnapshotTest {
    private val context = RuntimeEnvironment.getApplication()
    private val prefs = Prefs(context)
    private val a = Prefs.Auth("A", "token-A")
    private val b = Prefs.Auth("B", "token-B")
    private val seenTokens = CopyOnWriteArrayList<String>()
    private lateinit var server: HttpServer
    private lateinit var api: Api

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            seenTokens += ex.requestHeaders.getFirst("Authorization").orEmpty()
            val (status, body) = when {
                ex.requestURI.path.endsWith("/expired") -> 401 to """{"code":"UNAUTHENTICATED"}"""
                ex.requestURI.path.endsWith("/observations") -> {
                    prefs.auth = b // B đăng nhập đúng lúc lượt gửi của A đang chạy
                    201 to """{"batchId":"b1","uploads":[]}"""
                }
                else -> 202 to ""
            }
            val bytes = body.toByteArray()
            ex.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) ex.responseBody.use { it.write(bytes) }
            ex.close()
        }
        server.start()
        api = Api("http://127.0.0.1:${server.address.port}", prefs)
        prefs.contribute = true
    }

    @After
    fun tearDown() = server.stop(0)

    @Test
    fun `401 of an old session does not log out the new one`() {
        prefs.auth = b
        runCatching { api.request("GET", "/expired", auth = a) }
        assertEquals(b, prefs.auth)

        runCatching { api.request("GET", "/expired", auth = b) }
        assertEquals(null, prefs.auth)
    }

    @Test
    fun `upload keeps the owner's token even if another account logs in midway`() {
        prefs.auth = a
        val queue = ObservationQueue(File(context.filesDir, "queue-auth"), api, prefs, context)
        queue.add(
            JSONObject().put("clientId", UUID.randomUUID().toString()).put("kind", "POSITIVE")
                .put("observedAt", Instant.now().toString()),
            Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888),
            null,
        )

        queue.uploadOnce("v1", 1)

        assertEquals(listOf("Bearer token-A", "Bearer token-A"), seenTokens)
    }
}
