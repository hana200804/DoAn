package com.signa.app.support

import com.signa.app.data.local.session.SessionStore
import com.signa.app.domain.model.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MemorySessionStore(initial: Session? = null) : SessionStore {
    private val value = MutableStateFlow(initial)
    override val sessions = value.asStateFlow()
    val current: Session? get() = value.value
    override suspend fun save(session: Session) { value.value = session }
    override suspend fun clear(expectedSessionId: String?) {
        if (expectedSessionId == null || value.value?.id == expectedSessionId) value.value = null
    }
}
