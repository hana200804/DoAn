package com.signa.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.signa.app.domain.model.LocationProblem
import com.signa.app.domain.model.LocationResult
import com.signa.app.domain.repository.LocationRepository
import com.signa.app.domain.repository.SignRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.Normalizer
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MapViewModel @Inject constructor(
    repository: SignRepository,
    private val locations: LocationRepository
) : ViewModel() {
    private val interaction = MutableStateFlow(MapUiState())
    private var cameraSequence = 0L
    val state = combine(repository.observeSigns(), interaction) { signs, ui ->
        val keyword = normalize(ui.query.trim())
        val matching = signs.filter {
            (ui.speedFilter == null || it.speedLimitKmh == ui.speedFilter) &&
                (normalize(it.name).contains(keyword) || normalize(it.code).contains(keyword) || normalize(it.locationName).contains(keyword))
        }
        val selected = matching.firstOrNull { it.id == ui.selectedSignId } ?: matching.firstOrNull()
        ui.copy(
            signs = matching, isLoading = false,
            selectedSignId = selected?.id,
            availableSpeedLimits = signs.mapNotNull { it.speedLimitKmh }.distinct().sorted(),
            cameraTarget = ui.cameraTarget ?: selected?.position?.let { MapCameraTarget(it, 0) }
        )
    }.catch {
        emit(interaction.value.copy(hasError = true, isLoading = false))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState(isLoading = true))

    fun setQuery(value: String) = interaction.update { it.copy(query = value, selectedSignId = null, cameraTarget = null) }
    fun setSpeedFilter(value: Int?) = interaction.update { it.copy(speedFilter = value, selectedSignId = null, cameraTarget = null) }
    fun selectSign(id: String) {
        val sign = state.value.signs.firstOrNull { it.id == id } ?: return
        interaction.update { it.copy(selectedSignId = id, cameraTarget = sign.position?.let { point -> MapCameraTarget(point, ++cameraSequence) }) }
    }
    fun setMapStatus(status: MapLoadStatus) = interaction.update { it.copy(mapStatus = status) }
    fun retryMap() = interaction.update { it.copy(mapStatus = MapLoadStatus.Loading, retryMapId = it.retryMapId + 1) }
    fun clearLocationProblem() = interaction.update { it.copy(locationProblem = null) }
    fun permissionDenied() = interaction.update { it.copy(locationProblem = LocationProblem.PermissionDenied, isLocating = false, userLocation = null) }

    fun locate() {
        if (interaction.value.isLocating) return
        interaction.update { it.copy(isLocating = true, locationProblem = null) }
        viewModelScope.launch {
            try {
                when (val result = locations.currentLocation()) {
                    is LocationResult.Available -> interaction.update { it.copy(
                        userLocation = result.location, locationProblem = null,
                        cameraTarget = MapCameraTarget(result.location.point, ++cameraSequence)
                    ) }
                    is LocationResult.Unavailable -> interaction.update { it.copy(locationProblem = result.problem, userLocation = null) }
                }
            } finally {
                interaction.update { it.copy(isLocating = false) }
            }
        }
    }

    private fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").replace('đ', 'd').replace('Đ', 'D').lowercase(java.util.Locale.ROOT)
}
