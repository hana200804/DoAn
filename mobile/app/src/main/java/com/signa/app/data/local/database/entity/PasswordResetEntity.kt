package com.signa.app.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "password_resets")
data class PasswordResetEntity(
    @PrimaryKey val email: String,
    val codeSalt: String,
    val codeHash: String,
    val issuedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val attempts: Int = 0
)
