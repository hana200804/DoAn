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
 * Hàng đợi quan sát trên máy (FR-14): mỗi quan sát là một cặp file JSON + JPEG. Khi gửi, tối đa 20
 * quan sát được chuyển vào thư mục `batch-<Idempotency-Key>` và chỉ xoá khi server xác nhận, nên gửi
 * lại sau lỗi mạng không tạo bản ghi trùng.
 */
class ObservationQueue(
    private val dir: File,
    private val api: Api,
    private val prefs: Prefs,
    private val context: Context,
) {
    init {
        dir.mkdirs()
    }

    fun add(observation: JSONObject, crop: Bitmap) {
        if (prefs.session == null || !prefs.contribute) return
        val id = observation.getString("clientId")
        File(dir, "$id.jpg").outputStream().use { crop.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        File(dir, "$id.json").writeText(observation.toString())
        trim()
    }

    fun size(): Int = dir.listFiles { f -> f.extension == "json" }?.size ?: 0

    fun canUpload(): Boolean {
        if (prefs.session == null || !prefs.contribute) return false
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            (!prefs.wifiOnly || caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
    }

    /** Gửi một lô. Chạy trên luồng nền. */
    fun uploadOnce(modelVersion: String, labelMapVersion: Int) {
        val batch = pendingBatch() ?: return
        val key = batch.name.removePrefix("batch-")
        val files = batch.listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
        if (files.isEmpty()) {
            batch.deleteRecursively()
            return
        }
        val body = JSONObject()
            .put("modelVersion", modelVersion)
            .put("labelMapVersion", labelMapVersion)
            .put("deviceModel", Build.MODEL)
            .put("observations", JSONArray(files.map { JSONObject(it.readText()) }))
        val res = try {
            JSONObject(api.post("/observations", body, mapOf("Idempotency-Key" to key)))
        } catch (e: ApiException) {
            // Dữ liệu sai (ví dụ mã biển không có trong danh mục server) thì gửi lại cũng vô ích
            if (e.status == 400 || e.status == 422) batch.deleteRecursively()
            throw e
        }
        val uploads = res.getJSONArray("uploads")
        for (i in 0 until uploads.length()) {
            val u = uploads.getJSONObject(i)
            api.putFile(u.getString("cropUploadUrl"), File(batch, u.getString("clientId") + ".jpg"))
        }
        api.post("/observations/${res.getString("batchId")}/complete")
        batch.deleteRecursively()
    }

    private fun pendingBatch(): File? {
        dir.listFiles { f -> f.isDirectory && f.name.startsWith("batch-") }?.firstOrNull()?.let { return it }
        val next = dir.listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.lastModified() }.take(BATCH_SIZE)
        if (next.isEmpty()) return null
        val batch = File(dir, "batch-${UUID.randomUUID()}").apply { mkdirs() }
        for (json in next) {
            json.renameTo(File(batch, json.name))
            File(dir, json.nameWithoutExtension + ".jpg").renameTo(File(batch, json.nameWithoutExtension + ".jpg"))
        }
        return batch
    }

    /** Hàng đợi có giới hạn; khi đầy bỏ quan sát cũ nhất. */
    private fun trim() {
        val jsons = dir.listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.lastModified() }
        jsons.dropLast(MAX_QUEUED).forEach {
            it.delete()
            File(dir, it.nameWithoutExtension + ".jpg").delete()
        }
    }

    companion object {
        const val BATCH_SIZE = 20
        const val MAX_QUEUED = 500
    }
}
