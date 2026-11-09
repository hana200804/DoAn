package com.signa.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.signa.app.data.local.database.MIGRATION_1_2
import com.signa.app.data.local.database.SignaDatabase
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @get:Rule val helper = MigrationTestHelper(instrumentation, SignaDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @After fun cleanup() { instrumentation.targetContext.deleteDatabase(DATABASE) }

    @Test fun migrationPreservesCachedContentAndAddsNullableCoordinates() {
        helper.createDatabase(DATABASE, 1).apply {
            execSQL("INSERT INTO traffic_signs (id, code, name, meaning, locationName, speedLimitKmh) VALUES ('legacy', 'P.127_50', 'Saved sign', 'Saved meaning', 'Saved location', 50)")
            close()
        }
        helper.runMigrationsAndValidate(DATABASE, 2, true, MIGRATION_1_2).use { migrated ->
            migrated.query("SELECT name, latitude, longitude FROM traffic_signs WHERE id = 'legacy'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Saved sign", cursor.getString(0))
                assertTrue(cursor.isNull(1))
                assertTrue(cursor.isNull(2))
            }
        }
    }
    private companion object { const val DATABASE = "map-migration-test.db" }
}
