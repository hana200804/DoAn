package com.signa.app

import android.app.Application
import android.graphics.Bitmap
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.util.UUID

/** Hàng đợi quan sát: tách theo tài khoản và không kẹt vì quan sát hỏng. */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class ObservationQueueTest {
    private val context = RuntimeEnvironment.getApplication()
    private val prefs = Prefs(context)
    private val dir = File(context.filesDir, "queue-test")

    // Cổng không ai nghe: nếu hàng đợi gọi mạng thì test sẽ ném lỗi
    private val api = Api("http://127.0.0.1:1", prefs)

    @Before
    fun setUp() {
        dir.deleteRecursively()
        prefs.contribute = true
    }

    private fun login(userId: String) {
        prefs.userId = userId
        prefs.session = "session-$userId"
    }

    private fun observation() = JSONObject()
        .put("clientId", UUID.randomUUID().toString())
        .put("kind", "POSITIVE")
        .put("observedAt", Instant.now().toString())

    private fun crop() = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)

    @Test
    fun `observations stay with the account that made them`() {
        login("A")
        val queue = ObservationQueue(dir, api, prefs, context)
        queue.add(observation(), crop(), null)
        assertEquals(1, queue.size())

        login("B")
        assertEquals(0, queue.size())
        queue.uploadOnce("v1", 1) // không có gì của B để gửi, không gọi mạng

        login("A")
        assertEquals(1, queue.size())
    }

    @Test
    fun `half-written and broken observations do not block the queue`() {
        login("A")
        val user = File(dir, "A")
        File(user, "x.tmp").mkdirs() // app bị dừng giữa lúc ghi
        val broken = File(user, "batch-broken/o1").apply { mkdirs() }
        File(broken, "obs.json").writeText(observation().toString()) // thiếu crop.jpg
        File(user, "batch-broken/o2").mkdirs()
        File(user, "batch-broken/o2/obs.json").writeText("") // ghi dở khi mất điện

        val queue = ObservationQueue(dir, api, prefs, context)
        assertFalse(File(user, "x.tmp").exists())

        queue.uploadOnce("v1", 1)
        assertFalse(File(user, "batch-broken").exists())
    }
}
