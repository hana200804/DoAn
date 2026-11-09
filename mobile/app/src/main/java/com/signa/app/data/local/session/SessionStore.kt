package com.signa.app.data.local.session

import com.signa.app.domain.model.Session
import kotlinx.coroutines.flow.Flow

interface SessionStore {
    val sessions: Flow<Session?>
    suspend fun save(session: Session)
    suspend fun clear(expectedSessionId: String? = null)
}
