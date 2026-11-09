package com.signa.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.signa.app.domain.repository.SettingsRepository
import com.signa.app.domain.model.VehicleType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repository: SettingsRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsUiState(isLoading = true))
    val state = mutableState.asStateFlow()
    private var observation: Job? = null

    init { retry() }

    fun retry() {
        observation?.cancel()
        observation = viewModelScope.launch {
            mutableState.update { it.copy(isLoading = true, error = null) }
            try {
                repository.settings.collect { settings ->
                    mutableState.value = SettingsUiState(
                        vehicleType = settings.vehicleType,
                        voiceEnabled = settings.voiceEnabled,
                        contributionEnabled = settings.contributionEnabled,
                        wifiOnly = settings.wifiOnly
                    )
                }
            } catch (_: IOException) {
                mutableState.update { it.copy(isLoading = false, error = SettingsError.Read) }
            }
        }
    }

    fun setVehicleType(value: VehicleType) = save { repository.setVehicleType(value) }
    fun setVoiceEnabled(value: Boolean) = save { repository.setVoiceEnabled(value) }
    fun setContributionEnabled(value: Boolean) = save { repository.setContributionEnabled(value) }
    fun setWifiOnly(value: Boolean) = save { repository.setWifiOnly(value) }

    private fun save(write: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                write()
                mutableState.update { it.copy(error = null) }
            } catch (_: IOException) {
                mutableState.update { it.copy(error = SettingsError.Write) }
            }
        }
    }
}
