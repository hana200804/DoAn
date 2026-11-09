package com.signa.app.domain.repository

import com.signa.app.domain.model.AppSettings
import com.signa.app.domain.model.VehicleType
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setVehicleType(value: VehicleType)
    suspend fun setVoiceEnabled(value: Boolean)
    suspend fun setContributionEnabled(value: Boolean)
    suspend fun setWifiOnly(value: Boolean)
}
