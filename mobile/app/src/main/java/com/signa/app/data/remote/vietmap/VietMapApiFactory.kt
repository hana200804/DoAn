package com.signa.app.data.remote.vietmap

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class VietMapApiKeys(val search: String, val route: String) {
    override fun toString() = "VietMapApiKeys(searchConfigured=${search.isNotBlank()}, routeConfigured=${route.isNotBlank()})"
}

object VietMapApiFactory {
    /** Dedicated client: no app session/Bearer header and no request logging or cross-origin redirects. */
    fun create(baseUrl: HttpUrl, keys: VietMapApiKeys, json: Json): VietMapDirectionsApi {
        val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false)
            .addInterceptor { chain ->
                val request = chain.request()
                val url = request.url
                if (url.scheme != baseUrl.scheme || url.host != baseUrl.host || url.port != baseUrl.port || !url.encodedPath.startsWith(baseUrl.encodedPath)) {
                    throw IOException("Unexpected map service origin")
                }
                val key = if (url.encodedPath.endsWith("/route/v4")) keys.route else keys.search
                chain.proceed(request.newBuilder().url(url.newBuilder().setQueryParameter("apikey", key).build())
                    .header("Accept", "application/json").removeHeader("Authorization").build())
            }.build()
        return Retrofit.Builder().baseUrl(baseUrl).client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(VietMapDirectionsApi::class.java)
    }
}
