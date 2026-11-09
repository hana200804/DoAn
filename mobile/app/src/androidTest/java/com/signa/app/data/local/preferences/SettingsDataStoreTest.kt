package com.signa.app.data.local.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.signa.app.domain.model.AppSettings
import com.signa.app.domain.model.VehicleType
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class SettingsDataStoreTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun settingsSurviveStoreRecreationAndIndependentWrites() = runTest {
        val file = File(temporaryFolder.root, "settings.preferences_pb")
        val firstJob = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(firstJob + StandardTestDispatcher(testScheduler)),
            produceFile = { file }
        )
        try {
            val source = SettingsDataStore(store)
            assertEquals(AppSettings(), source.settings.first())
            source.setVehicleType(VehicleType.TRUCK)
            source.setVoiceEnabled(false)
            source.setContributionEnabled(true)
            source.setWifiOnly(false)
            assertEquals(AppSettings(VehicleType.TRUCK, false, true, false), source.settings.first())
        } finally {
            firstJob.cancelAndJoin()
        }

        val secondJob = SupervisorJob()
        val reopened = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(secondJob + StandardTestDispatcher(testScheduler)),
            produceFile = { file }
        )
        try {
            assertEquals(AppSettings(VehicleType.TRUCK, false, true, false), SettingsDataStore(reopened).settings.first())
            reopened.edit { it[stringPreferencesKey("vehicle_type")] = "FUTURE_VEHICLE" }
            assertEquals(VehicleType.CAR, SettingsDataStore(reopened).settings.first().vehicleType)
        } finally {
            secondJob.cancelAndJoin()
        }
    }
}
