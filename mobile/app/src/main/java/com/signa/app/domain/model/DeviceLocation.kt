package com.signa.app.domain.model

data class DeviceLocation(
    val point: GeoPoint,
    val accuracyMeters: Float,
    val capturedAtEpochMillis: Long,
    val isApproximate: Boolean
)

enum class LocationProblem { PermissionDenied, Disabled, Unavailable, TimedOut }

sealed interface LocationResult {
    data class Available(val location: DeviceLocation) : LocationResult
    data class Unavailable(val problem: LocationProblem) : LocationResult
}
