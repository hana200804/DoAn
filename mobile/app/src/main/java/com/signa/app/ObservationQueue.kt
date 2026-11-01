package com.signa.app

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Hàng đợi quan sát trên máy (FR-14), tách theo tài khoản: `<dir>/<userId>/<clientId>/` chứa
 * obs.json, crop.jpg (quan sát dương) và sub.jpg khi có biển phụ. Mỗi quan sát được ghi vào thư mục `.tmp` rồi đổi
 * tên một lần nên không bao giờ thiếu file. Khi gửi, tối đa 20 quan sát được chuyển vào
 * `batch-<Idempotency-Key>` và chỉ xoá khi server xác nhận, nên gửi lại sau lỗi mạng không tạo bản
 * ghi trùng.
 */
class ObservationQueue(
    private val dir: File,
    private val api: Api,
    private val prefs: Prefs,
    private val context: Context,
) {
    init {
        dir.mkdirs()
        // Quan sát ghi dở khi app bị dừng
        dir.walkTopDown().filter { it.isDirectory && it.name.endsWith(".tmp") }.toList().forEach { it.deleteRecursively() }
    }

    private fun userDir(): File? = prefs.userId?.let { File(dir, it) }

    /** [crop] null với quan sát âm. */
    fun add(observation: JSONObject, crop: Bitmap?, subCrop: Bitmap?) {
        val user = userDir() ?: return
        if (prefs.session == null || !prefs.contribute) return
        val id = observation.getString("clientId")
        val tmp = File(user, "$id.tmp").apply { mkdirs() }
        crop?.let { c -> File(tmp, CROP).outputStream().use { c.compress(Bitmap.CompressFormat.JPEG, 85, it) } }
        subCrop?.let { s -> File(tmp, SUB).outputStream().use { s.compress(Bitmap.CompressFormat.JPEG, 85, it) } }
        File(tmp, OBS).writeText(observation.toString())
        tmp.renameTo(File(user, id))
        trim(user)
    }

    fun size(): Int = userDir()?.let { observations(it).size } ?: 0

    fun canUpload(): Boolean {
        if (prefs.session == null || prefs.userId == null || !prefs.contribute) return false
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            (!prefs.wifiOnly || caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
    }

    /** Gửi một lô của tài khoản đang đăng nhập. Chạy trên luồng nền. */
    fun uploadOnce(modelVersion: String, labelMapVersion: Int) {
        val user = userDir() ?: return
        val token = prefs.session ?: return
        val batch = pendingBatch(user) ?: return
        val key = batch.name.removePrefix("batch-")
        // Quan sát thiếu file không thể gửi; bỏ lại để xoá cùng lô thay vì kẹt hàng đợi
        val items = observations(batch).sortedBy { it.name }.mapNotNull { dir ->
            // File hỏng (máy mất điện ngay sau khi ghi) bị bỏ, xoá cùng lô
            val obs = File(dir, OBS).takeIf { it.exists() }?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
            obs?.takeIf { it.getString("kind") == "NEGATIVE" || File(dir, CROP).exists() }
        }
        if (items.isEmpty()) {
            batch.deleteRecursively()
            return
        }
        // Giữ nguyên phiên của chủ hàng đợi cho cả lượt gửi
        val auth = mapOf("Authorization" to "Bearer $token")
        val body = JSONObject()
            .put("modelVersion", modelVersion)
            .put("labelMapVersion", labelMapVersion)
            .put("deviceModel", Build.MODEL)
            .put("observations", JSONArray(items))
        val res = try {
            JSONObject(api.post("/observations", body, auth + ("Idempotency-Key" to key)))
        } catch (e: ApiException) {
            // Dữ liệu sai (ví dụ mã biển không có trong danh mục server) thì gửi lại cũng vô ích
            if (e.status == 400 || e.status == 422) batch.deleteRecursively()
            throw e
        }
        val uploads = res.getJSONArray("uploads")
        for (i in 0 until uploads.length()) {
            val u = uploads.getJSONObject(i)
            val obs = File(batch, u.getString("clientId"))
            api.putFile(u.getString("cropUploadUrl"), File(obs, CROP))
            val sub = File(obs, SUB)
            if (!u.isNull("subSignUploadUrl") && sub.exists()) api.putFile(u.getString("subSignUploadUrl"), sub)
        }
        api.post("/observations/${res.getString("batchId")}/complete", headers = auth)
        batch.deleteRecursively()
    }

    private fun observations(parent: File): List<File> =
        parent.listFiles { f -> f.isDirectory && !f.name.startsWith("batch-") && !f.name.endsWith(".tmp") }.orEmpty().toList()

    private fun pendingBatch(user: File): File? {
        user.listFiles { f -> f.isDirectory && f.name.startsWith("batch-") }?.firstOrNull()?.let { return it }
        val next = observations(user).sortedBy { it.lastModified() }.take(BATCH_SIZE)
        if (next.isEmpty()) return null
        val batch = File(user, "batch-${UUID.randomUUID()}").apply { mkdirs() }
        next.forEach { it.renameTo(File(batch, it.name)) }
        return batch
    }

    /** Hàng đợi có giới hạn; khi đầy bỏ quan sát cũ nhất. */
    private fun trim(user: File) {
        observations(user).sortedBy { it.lastModified() }.dropLast(MAX_QUEUED).forEach { it.deleteRecursively() }
    }

    companion object {
        const val BATCH_SIZE = 20
        const val MAX_QUEUED = 500
        private const val OBS = "obs.json"
        private const val CROP = "crop.jpg"
        private const val SUB = "sub.jpg"
    }
}
