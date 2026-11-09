package com.signa.app.ui.map

import com.signa.app.domain.model.DeviceLocation
import com.signa.app.domain.model.GeoPoint
import com.signa.app.domain.model.LocationProblem
import com.signa.app.domain.model.TrafficSign

enum class MapLoadStatus { Loading, Ready, MissingKey, Failed }

data class MapCameraTarget(val point: GeoPoint, val requestId: Long)

data class MapUiState(
    val signs: List<TrafficSign> = emptyList(),
    val selectedSignId: String? = null,
    val query: String = "",
    val speedFilter: Int? = null,
    val availableSpeedLimits: List<Int> = emptyList(),
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
    val mapStatus: MapLoadStatus = MapLoadStatus.Loading,
    val isLocating: Boolean = false,
    val userLocation: DeviceLocation? = null,
    val locationProblem: LocationProblem? = null,
    val cameraTarget: MapCameraTarget? = null,
    val retryMapId: Int = 0
) {
    val selectedSign: TrafficSign? get() = signs.firstOrNull { it.id == selectedSignId } ?: signs.firstOrNull()
    val positionedSigns: List<TrafficSign> get() = signs.filter { it.position != null }
}
