package com.signa.app.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.signa.app.ui.map.components.AddressResults
import com.signa.app.ui.map.components.DestinationCard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.signa.app.R
import com.signa.app.domain.model.LocationProblem
import com.signa.app.ui.components.MapIllustration
import com.signa.app.ui.components.SignaWordmark
import com.signa.app.ui.components.SpeedLimitBadge

@Composable
fun MapScreen(
    state: MapUiState,
    onQueryChange: (String) -> Unit,
    onOpenSign: (String) -> Unit,
    onSelectSign: (String) -> Unit = {},
    onSpeedFilter: (Int?) -> Unit = {},
    onLocate: () -> Unit = {},
    onRetryMap: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
    onOpenLocationSettings: () -> Unit = {},
    directions: DirectionsUiState = DirectionsUiState(),
    directionActions: DirectionsActions = DirectionsActions(),
    mapContent: @Composable (Modifier, MapViewportInsets) -> Unit = { modifier, _ -> MapIllustration(modifier) }
) {
    var showFilters by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val selected = state.selectedSign
    val density = LocalDensity.current
    val margin = with(density) { 32.dp.roundToPx() }
    var topInset by remember { mutableIntStateOf(0) }
    var bottomInset by remember { mutableIntStateOf(0) }
    val searchLabel = stringResource(if (directions.searchMode == MapSearchMode.ADDRESSES) R.string.place_search_hint else R.string.map_search_signs)
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.surfaceVariant)) {
        val maximumCardHeight = maxHeight * .52f
        mapContent(Modifier.fillMaxSize(), MapViewportInsets(topInset, bottomInset))
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(16.dp).onSizeChanged { topInset = it.height + margin }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Surface(shape = RoundedCornerShape(10.dp), color = colors.surface.copy(alpha = .96f)) {
                    Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) { SignaWordmark() }
                }
                Surface(shape = RoundedCornerShape(8.dp), color = colors.surface.copy(alpha = .96f)) {
                    Text(stringResource(R.string.map_sample_count, state.positionedSigns.size), Modifier.padding(8.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
            OutlinedTextField(
                value = if (directions.searchMode == MapSearchMode.ADDRESSES) directions.query else state.query,
                onValueChange = if (directions.searchMode == MapSearchMode.ADDRESSES) directionActions.onQuery else onQueryChange,
                placeholder = { Text(searchLabel, style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                trailingIcon = {
                    if (directions.searchMode == MapSearchMode.SIGNS) IconButton(onClick = { showFilters = true }) {
                        Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.map_filter), tint = if (state.speedFilter == null) colors.onSurfaceVariant else colors.primary)
                    } else if (directions.query.isNotEmpty() || directions.destination != null) IconButton(onClick = directionActions.onClear) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.place_clear_search))
                    }
                },
                singleLine = true, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(14.dp)).semantics { contentDescription = searchLabel }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = directions.searchMode == MapSearchMode.ADDRESSES, onClick = { directionActions.onMode(MapSearchMode.ADDRESSES) },
                    label = { Text(stringResource(R.string.place_search_mode)) })
                FilterChip(selected = directions.searchMode == MapSearchMode.SIGNS, onClick = { directionActions.onMode(MapSearchMode.SIGNS) },
                    label = { Text(stringResource(R.string.sign_search_mode)) })
            }
            if (directions.searchMode == MapSearchMode.ADDRESSES) AddressResults(directions, directionActions)
            if (state.mapStatus != MapLoadStatus.Ready) Surface(shape = RoundedCornerShape(12.dp), color = colors.surface.copy(alpha = .97f)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.mapStatus == MapLoadStatus.Loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(stringResource(when (state.mapStatus) {
                        MapLoadStatus.Loading -> R.string.map_loading
                        MapLoadStatus.MissingKey -> R.string.map_missing_key
                        else -> R.string.map_load_failed
                    }), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    if (state.mapStatus == MapLoadStatus.Failed) TextButton(onClick = onRetryMap) { Text(stringResource(R.string.retry)) }
                }
            }
            if (state.locationProblem != null) Surface(shape = RoundedCornerShape(12.dp), color = colors.surface) {
                Column(Modifier.padding(12.dp)) {
                    Text(stringResource(when (state.locationProblem) {
                        LocationProblem.PermissionDenied -> R.string.map_location_permission
                        LocationProblem.Disabled -> R.string.map_location_disabled
                        LocationProblem.TimedOut -> R.string.map_location_timeout
                        LocationProblem.Unavailable -> R.string.map_location_unavailable
                    }), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = when (state.locationProblem) {
                        LocationProblem.PermissionDenied -> onOpenAppSettings
                        LocationProblem.Disabled -> onOpenLocationSettings
                        else -> onLocate
                    }) { Text(stringResource(if (state.locationProblem == LocationProblem.PermissionDenied || state.locationProblem == LocationProblem.Disabled) R.string.open_settings else R.string.retry)) }
                }
            } else if (state.userLocation != null) Surface(shape = RoundedCornerShape(10.dp), color = colors.surface) {
                Text(stringResource(if (state.userLocation.isApproximate) R.string.map_location_approximate else R.string.map_location_found), Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        Surface(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = with(density) { bottomInset.toDp() } + 52.dp), shape = CircleShape, shadowElevation = 4.dp, color = colors.surface) {
            IconButton(onClick = onLocate, enabled = !state.isLocating, modifier = Modifier.size(52.dp)) {
                if (state.isLocating) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else Icon(painterResource(R.drawable.ic_my_location), stringResource(R.string.map_my_location), tint = colors.primary)
            }
        }
        Surface(Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth().heightIn(max = maximumCardHeight).onSizeChanged { bottomInset = it.height + margin }, shape = RoundedCornerShape(20.dp), shadowElevation = 8.dp) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
            when {
                directions.destination != null -> DestinationCard(directions, directionActions, state.isLocating && !directions.isRouting)
                directions.searchMode == MapSearchMode.ADDRESSES -> Text(stringResource(R.string.place_map_hint), Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium)
                state.isLoading -> Box(Modifier.padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.hasError -> Text(stringResource(R.string.data_load_error), Modifier.padding(20.dp))
                selected == null -> Text(stringResource(R.string.no_signs_found), Modifier.padding(20.dp))
                else -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { onOpenSign(selected.id) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SpeedLimitBadge(selected.speedLimitKmh, Modifier.size(56.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(selected.name, style = MaterialTheme.typography.titleMedium)
                            Text(selected.locationName, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            Text(stringResource(R.string.map_marker_sample), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                        }
                        Icon(painterResource(R.drawable.ic_chevron), null)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { onOpenSign(selected.id) }) { Text(stringResource(R.string.view_detail), fontWeight = FontWeight.SemiBold) }
                        val index = state.signs.indexOfFirst { it.id == selected.id }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onSelectSign(state.signs[index - 1].id) }, enabled = index > 0) {
                                Icon(painterResource(R.drawable.ic_back), stringResource(R.string.map_previous_sign))
                            }
                            Text("${index + 1}/${state.signs.size}", style = MaterialTheme.typography.labelMedium)
                            IconButton(onClick = { onSelectSign(state.signs[index + 1].id) }, enabled = index >= 0 && index < state.signs.lastIndex) {
                                Icon(painterResource(R.drawable.ic_chevron), stringResource(R.string.map_next_sign))
                            }
                        }
                    }
                }
            }
            }
        }
    }
    if (showFilters) AlertDialog(
        onDismissRequest = { showFilters = false }, title = { Text(stringResource(R.string.map_filter)) },
        text = {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.speedFilter == null, onClick = { onSpeedFilter(null) }, label = { Text(stringResource(R.string.map_all_signs)) })
                state.availableSpeedLimits.forEach { speed ->
                    FilterChip(selected = state.speedFilter == speed, onClick = { onSpeedFilter(speed) }, label = { Text(stringResource(R.string.map_speed_filter, speed)) })
                }
            }
        },
        confirmButton = { TextButton(onClick = { showFilters = false }) { Text(stringResource(R.string.close)) } }
    )
}
