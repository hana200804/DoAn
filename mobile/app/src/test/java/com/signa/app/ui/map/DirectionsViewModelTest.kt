package com.signa.app.ui.map

import androidx.lifecycle.viewModelScope
import com.signa.app.domain.model.*
import com.signa.app.domain.repository.DirectionsRepository
import com.signa.app.domain.repository.LocationRepository
import com.signa.app.domain.repository.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DirectionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val settings = TestSettings()
    private val repository = TestDirections()
    private val location = TestLocation()
    private lateinit var viewModel: DirectionsViewModel
    private val first = PlaceSuggestion("first", "First place")
    private val second = PlaceSuggestion("second", "Second place")

    @Before fun prepare() { Dispatchers.setMain(dispatcher); viewModel = DirectionsViewModel(repository,location,settings) }
    @After fun cleanup() { viewModel.viewModelScope.cancel(); Dispatchers.resetMain() }

    @Test fun debouncesAndOnlyQueriesLatestText() = runTest(dispatcher) {
        viewModel.search("la")
        advanceUntilIdle()
        assertEquals(0,repository.searches.size)
        viewModel.search("land")
        advanceTimeBy(449); runCurrent()
        assertEquals(0,repository.searches.size)
        viewModel.search("landmark")
        advanceUntilIdle()
        assertEquals(listOf("landmark"),repository.searches)
    }

    @Test fun lateAutocompleteCannotReplaceNewResults() = runTest(dispatcher) {
        repository.searchAction = { text ->
            if (text == "older") withContext(NonCancellable) { delay(1200) }
            listOf(PlaceSuggestion(text,text))
        }
        viewModel.search("older"); advanceTimeBy(450); runCurrent()
        viewModel.search("newer"); advanceUntilIdle()
        assertEquals("newer",viewModel.state.value.suggestions.single().refId)
        assertFalse(viewModel.state.value.isSearching)
    }

    @Test fun latePlaceCannotRestoreAnOldDestination() = runTest(dispatcher) {
        repository.resolveAction = { selected ->
            if (selected.refId == "first") withContext(NonCancellable) { delay(500) }
            MapDestination(selected.refId,selected.label,selected.label,GeoPoint(10.79,106.72))
        }
        viewModel.selectPlace(first); runCurrent()
        viewModel.selectPlace(second); advanceUntilIdle()
        assertEquals("second",viewModel.state.value.destination?.refId)
        assertFalse(viewModel.state.value.isResolving)
    }

    @Test fun selectingAnotherDestinationCancelsLateRoute() = runTest(dispatcher) {
        advanceUntilIdle()
        repository.routeDelay = true
        viewModel.selectMapPoint(GeoPoint(10.79,106.72)); viewModel.planRoute()
        advanceTimeBy(30); runCurrent()
        viewModel.selectMapPoint(GeoPoint(10.78,106.70))
        advanceUntilIdle()
        assertEquals(GeoPoint(10.78,106.70),viewModel.state.value.destination?.point)
        assertNull(viewModel.state.value.route)
        assertFalse(viewModel.state.value.isRouting)
    }

    @Test fun routeUsesFreshGpsAndFailureClearsOldGeometry() = runTest(dispatcher) {
        advanceUntilIdle()
        viewModel.selectPlace(first); advanceUntilIdle()
        viewModel.planRoute(); advanceUntilIdle()
        assertEquals(location.fix.point,repository.lastOrigin)
        assertEquals(location.fix,viewModel.state.value.originFix)
        assertNotNull(viewModel.state.value.route)
        repository.routeFailure = MapServiceProblem.NoRoute
        viewModel.planRoute(); advanceUntilIdle()
        assertNull(viewModel.state.value.route)
        assertEquals(MapServiceProblem.NoRoute,viewModel.state.value.routeProblem)
    }

    @Test fun unavailableGpsPreventsRoutingFromSampleCoordinates() = runTest(dispatcher) {
        advanceUntilIdle()
        location.problem = LocationProblem.Disabled
        viewModel.selectMapPoint(GeoPoint(10.79,106.72));viewModel.planRoute();advanceUntilIdle()
        assertEquals(0,repository.routes)
        assertNull(viewModel.state.value.route)
        assertEquals(LocationProblem.Disabled,viewModel.state.value.locationProblem)
    }

    @Test fun truckRequiresWeightBeforeAnyLocationOrRouteRequest() = runTest(dispatcher) {
        settings.settings.value = AppSettings(vehicleType = VehicleType.TRUCK)
        advanceUntilIdle()
        viewModel.selectMapPoint(GeoPoint(10.79,106.72));viewModel.planRoute();advanceUntilIdle()
        assertEquals(MapServiceProblem.WeightRequired,viewModel.state.value.routeProblem)
        assertEquals(0,location.calls)
        viewModel.setWeight("5000");viewModel.planRoute();advanceUntilIdle()
        assertEquals(RoutingOptions(RoutingVehicle.TRUCK,5000),repository.lastOptions)
        viewModel.setWeight("6000")
        assertNull(viewModel.state.value.route)
    }

    @Test fun busNeverSilentlyUsesCarRouting() = runTest(dispatcher) {
        settings.settings.value = AppSettings(vehicleType = VehicleType.BUS)
        advanceUntilIdle()
        viewModel.selectMapPoint(GeoPoint(10.79,106.72));viewModel.planRoute();advanceUntilIdle()
        assertEquals(MapServiceProblem.UnsupportedVehicle,viewModel.state.value.routeProblem)
        assertEquals(0,repository.routes)
        viewModel.setVehicle(RoutingVehicle.CAR);viewModel.planRoute();advanceUntilIdle()
        assertEquals(RoutingOptions(RoutingVehicle.CAR),repository.lastOptions)
    }

    private class TestSettings : SettingsRepository {
        override val settings = MutableStateFlow(AppSettings())
        override suspend fun setVehicleType(value: VehicleType) { settings.value=settings.value.copy(vehicleType=value) }
        override suspend fun setVoiceEnabled(value: Boolean) { settings.value=settings.value.copy(voiceEnabled=value) }
        override suspend fun setContributionEnabled(value: Boolean) { settings.value=settings.value.copy(contributionEnabled=value) }
        override suspend fun setWifiOnly(value: Boolean) { settings.value=settings.value.copy(wifiOnly=value) }
    }
    private class TestLocation : LocationRepository {
        val fix=DeviceLocation(GeoPoint(10.80318,106.71430),10f,123,false)
        var problem: LocationProblem?=null
        var calls=0
        override suspend fun currentLocation(): LocationResult { calls++;delay(20);return problem?.let { LocationResult.Unavailable(it) } ?: LocationResult.Available(fix) }
    }
    private class TestDirections : DirectionsRepository {
        val searches=mutableListOf<String>()
        var searchAction: suspend (String)->List<PlaceSuggestion> = { listOf(PlaceSuggestion(it,it)) }
        var resolveAction: suspend (PlaceSuggestion)->MapDestination = { MapDestination(it.refId,it.label,it.label,GeoPoint(10.79,106.72)) }
        var routeDelay=false
        var routeFailure: MapServiceProblem?=null
        var routes=0
        var lastOrigin: GeoPoint?=null
        var lastOptions: RoutingOptions?=null
        override suspend fun search(text: String,focus: GeoPoint?): List<PlaceSuggestion> { searches.add(text);return searchAction(text) }
        override suspend fun resolve(suggestion: PlaceSuggestion)=resolveAction(suggestion)
        override suspend fun route(origin: GeoPoint,destination: GeoPoint,options: RoutingOptions): RoadRoute {
            routes++;lastOrigin=origin;lastOptions=options
            if(routeDelay) withContext(NonCancellable) { delay(500) }
            routeFailure?.let { throw MapServiceException(it) }
            return RoadRoute(origin,destination,listOf(origin,destination),1500.0,300000,options)
        }
    }
}
