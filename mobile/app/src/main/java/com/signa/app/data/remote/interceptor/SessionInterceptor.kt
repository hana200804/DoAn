package com.signa.app.data.remote.interceptor

import com.signa.app.data.local.session.SessionManager
import com.signa.app.data.remote.ApiConfig
import com.signa.app.domain.model.SessionSource
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class SessionInterceptor @Inject constructor(
    private val config: ApiConfig,
    private val sessions: SessionManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (!config.contains(original.url)) return chain.proceed(original)
        val relativePath = original.url.encodedPath.removePrefix(config.baseUrl.encodedPath)
        val anonymous = relativePath in ANONYMOUS_PATHS
        val session = if (anonymous) null else sessions.current?.takeIf { it.source == SessionSource.REMOTE }
        val builder = original.newBuilder()
            .header("Accept", "application/json")
            .header("X-Request-Id", UUID.randomUUID().toString())
        if (session != null) builder.header("Authorization", "Bearer ${session.id}")
        val response = chain.proceed(builder.build())
        if (response.code == 401 && session != null) {
            try {
                runBlocking { sessions.clear(session.id) }
            } catch (_: java.io.IOException) {
                // The response still reaches the repository and is handled as unauthorized.
            }
        }
        return response
    }

    private companion object {
        val ANONYMOUS_PATHS = setOf("auth/login", "auth/register", "auth/password/forgot", "auth/password/reset")
    }
}
