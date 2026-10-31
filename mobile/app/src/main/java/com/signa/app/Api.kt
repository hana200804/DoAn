package com.signa.app

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ApiException(val status: Int, val code: String, message: String) : IOException(message)

/** Gọi backend qua nginx (docs/07-api.md). Chạy trên luồng nền. */
class Api(private val baseUrl: String, private val prefs: Prefs) {

    fun get(path: String): String = request("GET", path)

    fun post(path: String, body: JSONObject? = null, headers: Map<String, String> = emptyMap()): String =
        request("POST", path, body, headers)

    fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        headers: Map<String, String> = emptyMap(),
    ): String {
        val conn = URL("$baseUrl/api/v1$path").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            prefs.session?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val status = conn.responseCode
            val text = (if (status < 400) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status == 401) prefs.session = null
            if (status >= 400) {
                val err = runCatching { JSONObject(text) }.getOrNull()
                throw ApiException(status, err?.optString("code") ?: "HTTP_$status", err?.optString("message") ?: "Lỗi $status")
            }
            return text
        } finally {
            conn.disconnect()
        }
    }

    /** Tải ảnh lên presigned URL của MinIO. */
    fun putFile(url: String, file: File) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "PUT"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "image/jpeg")
            conn.setFixedLengthStreamingMode(file.length())
            file.inputStream().use { it.copyTo(conn.outputStream) }
            if (conn.responseCode >= 300) throw IOException("Tải ảnh lỗi ${conn.responseCode}")
        } finally {
            conn.disconnect()
        }
    }
}
