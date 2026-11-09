package com.signa.app.domain.model

data class AppSettings(
    val vehicleType: VehicleType = VehicleType.CAR,
    val voiceEnabled: Boolean = true,
    val contributionEnabled: Boolean = false,
    val wifiOnly: Boolean = true
)
