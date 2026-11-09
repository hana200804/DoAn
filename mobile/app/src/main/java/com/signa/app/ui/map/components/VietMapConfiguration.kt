package com.signa.app.ui.map.components

import okhttp3.HttpUrl.Companion.toHttpUrl

/** The SDK key is a build-time setting. It is never sent to the app backend or logged. */
data class VietMapConfiguration(val apiKey: String) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()
    val styleUrl: String get() = "https://maps.vietmap.vn/maps/styles/tm/style.json".toHttpUrl()
        .newBuilder().addQueryParameter("apikey", apiKey).build().toString()

    companion object {
        // Render native markers without requesting tiles while waiting for a VietMap key.
        const val EMPTY_STYLE = """{"version":8,"name":"Awaiting VietMap configuration","sources":{},"layers":[{"id":"background","type":"background","paint":{"background-color":"#EEF3F8"}}]}"""
    }

    override fun toString(): String = "VietMapConfiguration(configured=$isConfigured)"
}
