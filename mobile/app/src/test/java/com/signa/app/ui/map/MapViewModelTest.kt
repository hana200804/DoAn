package com.signa.app.ui.map

import androidx.lifecycle.viewModelScope
import com.signa.app.data.fake.FakeSignRepository
import com.signa.app.domain.model.DeviceLocation
import com.signa.app.domain.model.GeoPoint
import com.signa.app.domain.model.LocationProblem
import com.signa.app.domain.model.LocationResult
import com.signa.app.domain.repository.LocationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val location = TestLocationRepository()
    private lateinit var viewModel: MapViewModel

    @Before fun prepare() {
        Dispatchers.setMain(dispatcher)
        viewModel = MapViewModel(FakeSignRepository(), location)
    }
    @After fun cleanup() {
        viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test fun filtersVietnameseSearchWithoutAccentsAndCombinesSpeedFilter() = runTest(dispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        advanceUntilIdle()
        viewModel.setQuery("binh thanh")
        advanceUntilIdle()
        assertEquals(3, viewModel.state.value.positionedSigns.size)
        viewModel.setSpeedFilter(60)
        advanceUntilIdle()
        assertEquals(60, viewModel.state.value.selectedSign?.speedLimitKmh)
        viewModel.setQuery("P.127_50")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.signs.isEmpty())
        assertNull(viewModel.state.value.selectedSign)
        viewModel.setSpeedFilter(null)
        advanceUntilIdle()
        assertEquals("sample-speed-50", viewModel.state.value.selectedSign?.id)
    }

    @Test fun markerSelectionUsesIdAndIgnoresUnknownMarker() = runTest(dispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        advanceUntilIdle()
        viewModel.selectSign("sample-speed-80")
        advanceUntilIdle()
        val selected = viewModel.state.value
        assertEquals("sample-speed-80", selected.selectedSignId)
        assertEquals(selected.selectedSign?.position, selected.cameraTarget?.point)
        viewModel.selectSign("unknown")
        advanceUntilIdle()
        assertEquals("sample-speed-80", viewModel.state.value.selectedSignId)
    }

    @Test fun oneLocationRequestAtATimeAndRepeatedFixCanRecenter() = runTest(dispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        advanceUntilIdle()
        viewModel.locate()
        viewModel.locate()
        advanceUntilIdle()
        assertEquals(1, location.requests)
        assertFalse(viewModel.state.value.isLocating)
        assertEquals(location.fix.point, viewModel.state.value.cameraTarget?.point)
        assertEquals(location.fix, viewModel.state.value.userLocation)
        val requestId = viewModel.state.value.cameraTarget!!.requestId
        viewModel.locate()
        advanceUntilIdle()
        assertEquals(2, location.requests)
        assertTrue(viewModel.state.value.cameraTarget!!.requestId > requestId)
    }

    @Test fun deniedOrUnavailableLocationNeverAppearsAsASampleUserPosition() = runTest(dispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        advanceUntilIdle()
        viewModel.permissionDenied()
        advanceUntilIdle()
        assertEquals(0, location.requests)
        assertEquals(LocationProblem.PermissionDenied, viewModel.state.value.locationProblem)
        location.result = LocationResult.Unavailable(LocationProblem.Disabled)
        viewModel.locate()
        advanceUntilIdle()
        assertNull(viewModel.state.value.userLocation)
        assertEquals(LocationProblem.Disabled, viewModel.state.value.locationProblem)
        assertFalse(viewModel.state.value.isLocating)
    }

    private class TestLocationRepository : LocationRepository {
        val fix = DeviceLocation(GeoPoint(21.0291, 105.8061), 10f, 123L, false)
        var result: LocationResult = LocationResult.Available(fix)
        var requests = 0
        override suspend fun currentLocation(): LocationResult { requests++; delay(100); return result }
    }
}
