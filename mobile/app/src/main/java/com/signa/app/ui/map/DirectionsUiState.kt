package com.signa.app.ui.map

import com.signa.app.domain.model.*

enum class MapSearchMode { ADDRESSES, SIGNS }

data class DirectionsUiState(
    val searchMode: MapSearchMode = MapSearchMode.ADDRESSES,
    val query: String = "",
    val suggestions: List<PlaceSuggestion> = emptyList(),
    val isSearching: Boolean = false,
    val isResolving: Boolean = false,
    val placeProblem: MapServiceProblem? = null,
    val lastRequestedPlace: PlaceSuggestion? = null,
    val destination: MapDestination? = null,
    val originFix: DeviceLocation? = null,
    val locationProblem: LocationProblem? = null,
    val route: RoadRoute? = null,
    val isRouting: Boolean = false,
    val routeProblem: MapServiceProblem? = null,
    val vehicle: RoutingVehicle? = null,
    val vehicleReady: Boolean = false,
    val truckWeight: String = "",
    val cameraTarget: MapCameraTarget? = null,
    val fitRouteRequest: Long = 0
)

data class DirectionsActions(
    val onMode: (MapSearchMode) -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onSelectPlace: (PlaceSuggestion) -> Unit = {},
    val onRetryPlace: () -> Unit = {},
    val onClear: () -> Unit = {},
    val onPlanRoute: () -> Unit = {},
    val onVehicle: (RoutingVehicle) -> Unit = {},
    val onWeight: (String) -> Unit = {},
    val onFitRoute: () -> Unit = {}
)

data class MapViewportInsets(val top: Int = 0, val bottom: Int = 0)
