package com.signa.app.data.local.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.signa.app.data.local.database.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM current_session WHERE slot = 0")
    fun observe(): Flow<SessionEntity?>

    @Upsert
    suspend fun save(session: SessionEntity)

    @Query("DELETE FROM current_session WHERE slot = 0")
    suspend fun clear()

    @Query("DELETE FROM current_session WHERE slot = 0 AND tokenHash = :tokenHash")
    suspend fun clearIfTokenHash(tokenHash: String)

    @Query("DELETE FROM current_session WHERE userId = :userId AND source = :source")
    suspend fun clearForUser(userId: String, source: String)
}
