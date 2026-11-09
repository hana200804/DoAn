package com.signa.app.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Add nullable coordinates while preserving existing cached signs and account/session tables. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE traffic_signs ADD COLUMN latitude REAL")
        db.execSQL("ALTER TABLE traffic_signs ADD COLUMN longitude REAL")
    }
}
