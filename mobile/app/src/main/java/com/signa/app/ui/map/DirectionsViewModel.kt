package com.signa.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.signa.app.domain.model.*
import com.signa.app.domain.repository.DirectionsRepository
import com.signa.app.domain.repository.LocationRepository
import com.signa.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Search, place resolution and routing are separate cancellable operations, independent of sign lookup. */
@HiltViewModel
class DirectionsViewModel @Inject constructor(
    private val directions: DirectionsRepository,
    private val locations: LocationRepository,
    settings: SettingsRepository
) : ViewModel() {
    private val mutableState = MutableStateFlow(DirectionsUiState())
    val state = mutableState.asStateFlow()
    private var searchJob: Job? = null
    private var placeJob: Job? = null
    private var routeJob: Job? = null
    private var searchVersion = 0L
    private var placeVersion = 0L
    private var routeVersion = 0L

    init {
        viewModelScope.launch {
            var previous: VehicleType? = null
            settings.settings.catch { mutableState.update { it.copy(vehicleReady = true, vehicle = null) } }.collect { preferences ->
                if (previous != preferences.vehicleType) {
                    previous = preferences.vehicleType
                    invalidateRoute()
                    mutableState.update { it.copy(vehicleReady = true, vehicle = when (preferences.vehicleType) {
                        VehicleType.CAR -> RoutingVehicle.CAR
                        VehicleType.TRUCK -> RoutingVehicle.TRUCK
                        VehicleType.BUS -> null
                    }) }
                }
            }
        }
    }

    fun setMode(mode: MapSearchMode) {
        if (state.value.searchMode == mode) return
        if (mode == MapSearchMode.SIGNS) clearDestination()
        mutableState.update { it.copy(searchMode = mode) }
    }

    fun search(query: String, focus: GeoPoint? = null) {
        invalidateRoute()
        val version = ++searchVersion
        searchJob?.cancel()
        ++placeVersion
        placeJob?.cancel()
        val text = query.trim()
        mutableState.update { it.copy(query = query, destination = null, cameraTarget = null, suggestions = emptyList(),
            lastRequestedPlace = null, placeProblem = null, isResolving = false, isSearching = text.length >= 3) }
        if (text.length < 3) return
        searchJob = viewModelScope.launch {
            try {
                delay(450)
                val found = directions.search(text, focus)
                if (version == searchVersion) mutableState.update { it.copy(suggestions = found) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: MapServiceException) {
                if (version == searchVersion) mutableState.update { it.copy(placeProblem = failure.problem) }
            } finally {
                if (version == searchVersion) mutableState.update { it.copy(isSearching = false) }
            }
        }
    }

    fun selectPlace(suggestion: PlaceSuggestion) {
        invalidateRoute()
        ++searchVersion
        searchJob?.cancel()
        val version = ++placeVersion
        placeJob?.cancel()
        mutableState.update { it.copy(query = suggestion.label, suggestions = emptyList(), isSearching = false,
            destination = null, cameraTarget = null, lastRequestedPlace = suggestion, placeProblem = null, isResolving = true) }
        placeJob = viewModelScope.launch {
            try {
                val place = directions.resolve(suggestion)
                if (version == placeVersion) mutableState.update { it.copy(destination = place, cameraTarget = MapCameraTarget(place.point, version)) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: MapServiceException) {
                if (version == placeVersion) mutableState.update { it.copy(placeProblem = failure.problem) }
            } finally {
                if (version == placeVersion) mutableState.update { it.copy(isResolving = false) }
            }
        }
    }

    fun selectMapPoint(point: GeoPoint) {
        clearDestination()
        val version = ++placeVersion
        mutableState.update { it.copy(searchMode = MapSearchMode.ADDRESSES,
            destination = MapDestination(null, "", "", point), cameraTarget = MapCameraTarget(point, version)) }
    }

    fun retryPlace() {
        val last = state.value.lastRequestedPlace
        if (last != null) selectPlace(last) else search(state.value.query, state.value.originFix?.point)
    }

    fun clearDestination() {
        ++searchVersion; ++placeVersion
        searchJob?.cancel(); placeJob?.cancel()
        invalidateRoute()
        mutableState.update { it.copy(query = "", destination = null, suggestions = emptyList(), cameraTarget = null,
            isSearching = false, isResolving = false, placeProblem = null, lastRequestedPlace = null, locationProblem = null) }
    }

    fun setVehicle(vehicle: RoutingVehicle) {
        if (vehicle == state.value.vehicle) return
        invalidateRoute()
        mutableState.update { it.copy(vehicle = vehicle) }
    }
    fun setWeight(value: String) {
        val sanitized = value.filter { it in '0'..'9' }.take(8)
        if (sanitized == state.value.truckWeight) return
        invalidateRoute()
        mutableState.update { it.copy(truckWeight = sanitized) }
    }
    fun permissionDenied() {
        invalidateRoute()
        mutableState.update { it.copy(originFix = null, locationProblem = LocationProblem.PermissionDenied) }
    }
    fun clearLocationProblem() = mutableState.update { it.copy(locationProblem = null) }

    fun planRoute() {
        val snapshot = state.value
        val destination = snapshot.destination ?: return
        if (snapshot.isRouting || !snapshot.vehicleReady) return
        val vehicle = snapshot.vehicle
        if (vehicle == null) { mutableState.update { it.copy(routeProblem = MapServiceProblem.UnsupportedVehicle) }; return }
        val weight = snapshot.truckWeight.toIntOrNull()?.takeIf { it > 0 }
        if (vehicle == RoutingVehicle.TRUCK && weight == null) {
            mutableState.update { it.copy(routeProblem = MapServiceProblem.WeightRequired) }; return
        }
        val options = RoutingOptions(vehicle, if (vehicle == RoutingVehicle.TRUCK) weight else null)
        val version = ++routeVersion
        routeJob?.cancel()
        mutableState.update { it.copy(route = null, fitRouteRequest = 0, isRouting = true, routeProblem = null, locationProblem = null) }
        routeJob = viewModelScope.launch {
            try {
                when (val origin = locations.currentLocation()) {
                    is LocationResult.Unavailable -> if (version == routeVersion) mutableState.update { it.copy(locationProblem = origin.problem, originFix = null) }
                    is LocationResult.Available -> {
                        if (version != routeVersion) return@launch
                        mutableState.update { it.copy(originFix = origin.location) }
                        val road = directions.route(origin.location.point, destination.point, options)
                        if (version == routeVersion) mutableState.update { it.copy(route = road, cameraTarget = null, fitRouteRequest = version) }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: MapServiceException) {
                if (version == routeVersion) mutableState.update { it.copy(routeProblem = failure.problem) }
            } finally {
                if (version == routeVersion) mutableState.update { it.copy(isRouting = false) }
            }
        }
    }

    fun fitRoute() { if (state.value.route != null) mutableState.update { it.copy(fitRouteRequest = ++routeVersion) } }
    private fun invalidateRoute() {
        ++routeVersion
        routeJob?.cancel()
        mutableState.update { it.copy(route = null, isRouting = false, routeProblem = null, fitRouteRequest = 0) }
    }
}
