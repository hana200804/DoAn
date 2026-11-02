package com.signa.app

import android.app.Application
import android.graphics.Bitmap
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
import java.net.InetAddress
import java.net.ServerSocket
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/** Đổi tài khoản giữa lúc đang gọi mạng không làm lẫn phiên giữa hai người. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class AuthSnapshotTest {
    private val context = RuntimeEnvironment.getApplication()
    private val prefs = Prefs(context)
    private val a = Prefs.Auth("A", "token-A")
    private val b = Prefs.Auth("B", "token-B")
    private val seenTokens = CopyOnWriteArrayList<String>()
    private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    private lateinit var api: Api

    // Server HTTP tối thiểu (unit test Android không có com.sun.net.httpserver)
    private fun serve() = thread(isDaemon = true) {
        while (!server.isClosed) {
            val socket = runCatching { server.accept() }.getOrNull() ?: break
            socket.use {
                val input = it.getInputStream().bufferedReader()
                val path = input.readLine().split(' ')[1]
                var length = 0
                generateSequence { input.readLine() }.takeWhile { line -> line.isNotEmpty() }.forEach { line ->
                    val (name, value) = line.split(':', limit = 2).map(String::trim)
                    if (name.equals("Authorization", true)) seenTokens += value
                    if (name.equals("Content-Length", true)) length = value.toInt()
                }
                repeat(length) { input.read() }
                val (status, body) = when {
                    path.endsWith("/expired") -> 401 to """{"code":"UNAUTHENTICATED"}"""
                    path.endsWith("/observations") -> {
                        prefs.auth = b // B đăng nhập đúng lúc lượt gửi của A đang chạy
                        201 to """{"batchId":"b1","uploads":[]}"""
                    }
                    else -> 202 to ""
                }
                val bytes = body.toByteArray()
                it.getOutputStream().apply {
                    write("HTTP/1.1 $status X\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    write(bytes)
                    flush()
                }
            }
        }
    }

    @Before
    fun setUp() {
        serve()
        api = Api("http://127.0.0.1:${server.localPort}", prefs)
        prefs.contribute = true
    }

    @After
    fun tearDown() = server.close()

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
