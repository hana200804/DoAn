package com.signa.app.data.local.session

import com.signa.app.data.local.database.dao.SessionDao
import com.signa.app.data.local.database.entity.SessionEntity
import com.signa.app.domain.model.Session
import java.io.IOException
import android.database.sqlite.SQLiteException
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class RoomSessionStore @Inject constructor(
    private val dao: SessionDao,
    private val cipher: SessionCipher,
    private val json: Json
) : SessionStore {
    override val sessions = dao.observe().map { row ->
        if (row == null) return@map null
        try {
            val session = json.decodeFromString<SessionRecord>(cipher.decrypt(row.encryptedPayload)).toDomain()
            require(session.id.isNotBlank() && tokenHash(session.id) == row.tokenHash && session.user.id == row.userId && session.source.name == row.source)
            session
        } catch (_: GeneralSecurityException) {
            dao.clearIfTokenHash(row.tokenHash)
            null
        } catch (_: SerializationException) {
            dao.clearIfTokenHash(row.tokenHash)
            null
        } catch (_: IllegalArgumentException) {
            dao.clearIfTokenHash(row.tokenHash)
            null
        }
    }.catch { error ->
        if (error is SQLiteException) throw IOException("Cannot restore session", error)
        throw error
    }

    override suspend fun save(session: Session) {
        val payload = try {
            cipher.encrypt(json.encodeToString(SessionRecord.serializer(), SessionRecord.from(session)))
        } catch (error: GeneralSecurityException) {
            throw IOException("Cannot encrypt session", error)
        }
        try {
            dao.save(SessionEntity(userId = session.user.id, tokenHash = tokenHash(session.id), source = session.source.name, encryptedPayload = payload))
        } catch (error: SQLiteException) {
            throw IOException("Cannot persist session", error)
        }
    }

    override suspend fun clear(expectedSessionId: String?) {
        try {
            if (expectedSessionId == null) dao.clear() else dao.clearIfTokenHash(tokenHash(expectedSessionId))
        } catch (error: SQLiteException) {
            throw IOException("Cannot clear session", error)
        }
    }

    private fun tokenHash(id: String): String = Base64.getEncoder().encodeToString(
        MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8))
    )
}
