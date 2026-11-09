package com.signa.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.signa.app.data.local.database.dao.FakeAccountDao
import com.signa.app.data.local.database.dao.SessionDao
import com.signa.app.data.local.database.dao.SignDao
import com.signa.app.data.local.database.entity.FakeAccountEntity
import com.signa.app.data.local.database.entity.PasswordResetEntity
import com.signa.app.data.local.database.entity.SessionEntity
import com.signa.app.data.local.database.entity.TrafficSignEntity

@Database(
    entities = [TrafficSignEntity::class, FakeAccountEntity::class, PasswordResetEntity::class, SessionEntity::class],
    version = 2,
    exportSchema = true
)
abstract class SignaDatabase : RoomDatabase() {
    abstract fun signs(): SignDao
    abstract fun accounts(): FakeAccountDao
    abstract fun sessions(): SessionDao
}
