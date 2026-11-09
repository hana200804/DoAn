package com.signa.app.data.local.session

import com.signa.app.di.ApplicationScope
import com.signa.app.domain.model.Session
import com.signa.app.domain.model.SessionSource
import java.io.IOException
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private sealed interface SessionStatus {
    data object Loading : SessionStatus
    data object Guest : SessionStatus
    data class SignedIn(val session: Session) : SessionStatus
}

@Singleton
class SessionManager @Inject constructor(
    private val store: SessionStore,
    private val clock: Clock,
    private val source: SessionSource,
    @param:ApplicationScope private val scope: CoroutineScope
) {
    private val status = MutableStateFlow<SessionStatus>(SessionStatus.Loading)
    val session = status.filter { it != SessionStatus.Loading }
        .map { (it as? SessionStatus.SignedIn)?.session }
        .distinctUntilChanged()

    val current: Session?
        get() = (status.value as? SessionStatus.SignedIn)?.session
            ?.takeIf { it.expiresAtEpochMillis > clock.millis() && it.source == source }

    init {
        scope.launch {
            store.sessions.catch { status.value = SessionStatus.Guest }.collectLatest { restored ->
                try {
                    when {
                        restored == null -> status.value = SessionStatus.Guest
                        restored.source != source || restored.expiresAtEpochMillis <= clock.millis() -> {
                            status.value = SessionStatus.Guest
                            store.clear(restored.id)
                        }
                        else -> {
                            status.value = SessionStatus.SignedIn(restored)
                            var remaining = restored.expiresAtEpochMillis - clock.millis()
                            while (remaining > 0) {
                                delay(remaining)
                                remaining = restored.expiresAtEpochMillis - clock.millis()
                            }
                            status.value = SessionStatus.Guest
                            store.clear(restored.id)
                        }
                    }
                } catch (_: IOException) {
                    status.value = SessionStatus.Guest
                }
            }
        }
    }

    suspend fun save(session: Session) {
        require(session.source == source && session.expiresAtEpochMillis > clock.millis())
        store.save(session)
    }

    suspend fun clear(expectedSessionId: String? = null) = store.clear(expectedSessionId)

    /** Re-check wall-clock expiry when the app returns to the foreground. */
    fun revalidate() {
        scope.launch {
            try {
                val stored = store.sessions.first()
                if (stored != null && (stored.source != source || stored.expiresAtEpochMillis <= clock.millis())) {
                    status.value = SessionStatus.Guest
                    store.clear(stored.id)
                }
            } catch (_: IOException) {
                status.value = SessionStatus.Guest
            }
        }
    }
}
