package com.signa.app.ui.map.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.signa.app.R
import com.signa.app.domain.model.MapServiceProblem
import com.signa.app.domain.model.RoutingVehicle
import com.signa.app.ui.map.DirectionsActions
import com.signa.app.ui.map.DirectionsUiState
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.ceil

@Composable
internal fun AddressResults(state: DirectionsUiState, actions: DirectionsActions) {
    if (state.destination != null || (state.query.length < 3 && !state.isResolving && state.placeProblem == null)) return
    Surface(shape = RoundedCornerShape(14.dp), shadowElevation = 4.dp) {
        Column(Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState()).padding(12.dp)) {
            when {
                state.isSearching || state.isResolving -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(if (state.isResolving) R.string.place_resolving else R.string.place_searching), style = MaterialTheme.typography.bodyMedium)
                }
                state.placeProblem != null -> {
                    Text(stringResource(mapServiceMessage(state.placeProblem)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = actions.onRetryPlace) { Text(stringResource(R.string.retry)) }
                }
                state.suggestions.isEmpty() -> Text(stringResource(R.string.place_no_results), style = MaterialTheme.typography.bodyMedium)
                else -> state.suggestions.forEach { suggestion ->
                    Row(Modifier.fillMaxWidth().clickable { actions.onSelectPlace(suggestion) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(painterResource(R.drawable.ic_location), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(suggestion.label, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
internal fun DestinationCard(state: DirectionsUiState, actions: DirectionsActions, isLocating: Boolean) {
    val destination = state.destination ?: return
    val route = state.route
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(painterResource(R.drawable.ic_location), null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.route_destination), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(destination.name.ifBlank { stringResource(R.string.route_pinned_destination) }, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(destination.address.ifBlank { String.format(Locale.ROOT, "%.5f, %.5f", destination.point.latitude, destination.point.longitude) },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = actions.onClear, modifier = Modifier.size(32.dp)) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.route_clear)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(RoutingVehicle.CAR to R.string.vehicle_car, RoutingVehicle.TRUCK to R.string.vehicle_truck).forEach { (vehicle, label) ->
                FilterChip(selected = state.vehicle == vehicle, onClick = { actions.onVehicle(vehicle) }, enabled = state.vehicleReady && !state.isRouting,
                    label = { Text(stringResource(label)) }, colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.primary))
            }
        }
        if (state.vehicle == null) Text(stringResource(R.string.route_choose_profile), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.vehicle == RoutingVehicle.TRUCK) OutlinedTextField(
            value = state.truckWeight, onValueChange = actions.onWeight, modifier = Modifier.fillMaxWidth(), enabled = !state.isRouting,
            label = { Text(stringResource(R.string.route_truck_weight)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = state.routeProblem == MapServiceProblem.WeightRequired, singleLine = true, shape = RoundedCornerShape(10.dp)
        )
        if (route != null) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.route_distance), style = MaterialTheme.typography.labelMedium)
                        val format = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")).apply { maximumFractionDigits = 1 }
                        Text(if (route.distanceMeters < 1000) stringResource(R.string.route_meters, kotlin.math.round(route.distanceMeters).toInt())
                            else stringResource(R.string.route_kilometers, format.format(route.distanceMeters / 1000)),
                            style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.route_duration), style = MaterialTheme.typography.labelMedium)
                        val minutes = ceil(route.durationMillis / 60_000.0).toLong().coerceAtLeast(1)
                        Text(if (minutes < 60) stringResource(R.string.route_minutes, minutes)
                            else stringResource(R.string.route_hours_minutes, minutes / 60, minutes % 60),
                            style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            TextButton(onClick = actions.onFitRoute) { Text(stringResource(R.string.route_show_all)) }
        }
        state.routeProblem?.let { Text(stringResource(mapServiceMessage(it)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Button(onClick = actions.onPlanRoute, enabled = state.vehicleReady && state.vehicle != null && !state.isRouting && !isLocating,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(10.dp)) {
            if (state.isRouting) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(if (state.isRouting) R.string.route_calculating else if (route == null) R.string.route_preview else R.string.route_recalculate), fontWeight = FontWeight.SemiBold)
        }
    }
}

internal fun mapServiceMessage(problem: MapServiceProblem): Int = when (problem) {
    MapServiceProblem.MissingKey -> R.string.directions_missing_key
    MapServiceProblem.Network -> R.string.directions_network
    MapServiceProblem.AccessDenied -> R.string.directions_access_denied
    MapServiceProblem.QuotaUnavailable -> R.string.directions_quota
    MapServiceProblem.RateLimited -> R.string.directions_rate_limited
    MapServiceProblem.InvalidRequest -> R.string.directions_invalid_request
    MapServiceProblem.InvalidResponse -> R.string.directions_invalid_response
    MapServiceProblem.Server -> R.string.directions_server
    MapServiceProblem.NoRoute -> R.string.directions_no_route
    MapServiceProblem.UnsupportedVehicle -> R.string.route_choose_profile
    MapServiceProblem.WeightRequired -> R.string.route_weight_required
}
