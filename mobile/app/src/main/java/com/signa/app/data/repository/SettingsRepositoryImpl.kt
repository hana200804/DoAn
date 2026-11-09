package com.signa.app.data.repository

import com.signa.app.data.local.preferences.SettingsDataStore
import com.signa.app.domain.model.VehicleType
import com.signa.app.domain.repository.SettingsRepository
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(private val local: SettingsDataStore) : SettingsRepository {
    override val settings = local.settings
    override suspend fun setVehicleType(value: VehicleType) = local.setVehicleType(value)
    override suspend fun setVoiceEnabled(value: Boolean) = local.setVoiceEnabled(value)
    override suspend fun setContributionEnabled(value: Boolean) = local.setContributionEnabled(value)
    override suspend fun setWifiOnly(value: Boolean) = local.setWifiOnly(value)
}
