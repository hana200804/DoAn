package com.signa.app.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "current_session")
data class SessionEntity(
    @PrimaryKey val slot: Int = 0,
    val userId: String,
    val tokenHash: String,
    val source: String,
    val encryptedPayload: String
)
