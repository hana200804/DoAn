package com.signa.app.ui.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.signa.app.BuildConfig
import com.signa.app.ui.map.components.VietMapConfiguration
import com.signa.app.ui.map.components.VietMapSurface

@Composable
fun MapRoute(
    onOpenSign: (String) -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
    directionsViewModel: DirectionsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val trip by directionsViewModel.state.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    var routeAfterPermission by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val configuration = remember { VietMapConfiguration(BuildConfig.VIETMAP_API_KEY) }
    fun hasPermission() = listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it } || hasPermission()) {
            if (routeAfterPermission) directionsViewModel.planRoute() else viewModel.locate()
        } else {
            viewModel.permissionDenied()
            directionsViewModel.permissionDenied()
        }
        routeAfterPermission = false
    }
    val locate = {
        directionsViewModel.clearLocationProblem()
        routeAfterPermission = false
        if (hasPermission()) viewModel.locate()
        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !hasPermission()) {
                if (viewModel.state.value.userLocation != null) viewModel.permissionDenied()
                if (directionsViewModel.state.value.originFix != null) directionsViewModel.permissionDenied()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val displayed = state.copy(
        userLocation = listOfNotNull(state.userLocation, trip.originFix).maxByOrNull { it.capturedAtEpochMillis },
        locationProblem = trip.locationProblem ?: state.locationProblem,
        isLocating = state.isLocating || trip.isRouting
    )
    val actions = DirectionsActions(
        onMode = directionsViewModel::setMode,
        onQuery = { directionsViewModel.search(it, displayed.userLocation?.point) },
        onSelectPlace = { keyboard?.hide(); directionsViewModel.selectPlace(it) },
        onRetryPlace = directionsViewModel::retryPlace,
        onClear = directionsViewModel::clearDestination,
        onPlanRoute = {
            keyboard?.hide()
            if (!state.isLocating && !trip.isRouting) {
                viewModel.clearLocationProblem()
                if (hasPermission()) directionsViewModel.planRoute()
                else {
                    routeAfterPermission = true
                    permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            }
        },
        onVehicle = directionsViewModel::setVehicle,
        onWeight = directionsViewModel::setWeight,
        onFitRoute = directionsViewModel::fitRoute
    )
    val selectSign: (String) -> Unit = { id ->
        directionsViewModel.setMode(MapSearchMode.SIGNS)
        directionsViewModel.clearDestination()
        viewModel.selectSign(id)
    }
    MapScreen(
        displayed, viewModel::setQuery, onOpenSign, selectSign, viewModel::setSpeedFilter,
        onLocate = locate, onRetryMap = viewModel::retryMap,
        onOpenAppSettings = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) },
        onOpenLocationSettings = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
        directions = trip, directionActions = actions,
        mapContent = { modifier, insets -> VietMapSurface(displayed, configuration, selectSign, viewModel::setMapStatus, modifier,
            directions = trip, viewportInsets = insets, onChoosePoint = { point -> keyboard?.hide(); directionsViewModel.selectMapPoint(point) }) }
    )
}
