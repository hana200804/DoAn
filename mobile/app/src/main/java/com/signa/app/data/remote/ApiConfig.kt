package com.signa.app.data.remote

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class ApiConfig(baseUrl: String) {
    val baseUrl: HttpUrl = baseUrl.toHttpUrl().let { url ->
        val path = if (url.encodedPath == "/") "/api/v1/" else url.encodedPath.trimEnd('/') + "/"
        require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null)
        url.newBuilder().encodedPath(path).build()
    }

    fun contains(url: HttpUrl): Boolean = url.scheme == baseUrl.scheme && url.host == baseUrl.host &&
        url.port == baseUrl.port && url.encodedPath.startsWith(baseUrl.encodedPath)
}
