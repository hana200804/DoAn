package com.signa.app.data.local.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "fake_accounts", indices = [Index(value = ["email"], unique = true)])
data class FakeAccountEntity(
    @PrimaryKey val id: String,
    val email: String,
    val displayName: String,
    val vehicleType: String,
    val passwordSalt: String,
    val passwordHash: String
)
