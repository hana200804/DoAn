package com.signa.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onVehicleSelected = viewModel::setVehicleType,
        onVoiceChanged = viewModel::setVoiceEnabled,
        onContributionChanged = viewModel::setContributionEnabled,
        onWifiOnlyChanged = viewModel::setWifiOnly,
        onRetry = viewModel::retry
    )
}
