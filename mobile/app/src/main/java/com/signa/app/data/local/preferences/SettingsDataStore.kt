package com.signa.app.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.signa.app.domain.model.AppSettings
import com.signa.app.domain.model.VehicleType
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsDataStore @Inject constructor(private val store: DataStore<Preferences>) {
    val settings: Flow<AppSettings> = store.data.map { preferences ->
        val vehicle = VehicleType.entries.firstOrNull { it.name == preferences[VEHICLE] }
        AppSettings(
            vehicleType = vehicle ?: VehicleType.CAR,
            voiceEnabled = preferences[VOICE] ?: true,
            contributionEnabled = preferences[CONTRIBUTION] ?: false,
            wifiOnly = preferences[WIFI_ONLY] ?: true
        )
    }

    suspend fun setVehicleType(value: VehicleType) {
        store.edit { it[VEHICLE] = value.name }
    }

    suspend fun setVoiceEnabled(value: Boolean) {
        store.edit { it[VOICE] = value }
    }

    suspend fun setContributionEnabled(value: Boolean) {
        store.edit { it[CONTRIBUTION] = value }
    }

    suspend fun setWifiOnly(value: Boolean) {
        store.edit { it[WIFI_ONLY] = value }
    }

    private companion object {
        val VEHICLE = stringPreferencesKey("vehicle_type")
        val VOICE = booleanPreferencesKey("voice_enabled")
        val CONTRIBUTION = booleanPreferencesKey("contribution_enabled")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
    }
}
