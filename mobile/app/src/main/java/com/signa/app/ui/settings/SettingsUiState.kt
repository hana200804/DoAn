package com.signa.app.ui.settings

import com.signa.app.domain.model.VehicleType

data class SettingsUiState(
    val vehicleType: VehicleType = VehicleType.CAR,
    val voiceEnabled: Boolean = true,
    val contributionEnabled: Boolean = false,
    val wifiOnly: Boolean = true,
    val isLoading: Boolean = false,
    val error: SettingsError? = null
)

enum class SettingsError { Read, Write }
