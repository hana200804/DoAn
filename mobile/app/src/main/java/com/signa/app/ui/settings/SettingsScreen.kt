package com.signa.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.signa.app.R
import com.signa.app.domain.model.VehicleType
import com.signa.app.ui.components.SignaPage

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onVehicleSelected: (VehicleType) -> Unit,
    onVoiceChanged: (Boolean) -> Unit,
    onContributionChanged: (Boolean) -> Unit,
    onWifiOnlyChanged: (Boolean) -> Unit,
    onRetry: () -> Unit = {}
) {
    var showVehicles by rememberSaveable { mutableStateOf(false) }
    SignaPage(stringResource(R.string.nav_settings)) {
        if (state.isLoading) {
            CircularProgressIndicator()
        }
        if (state.error != null) {
            Text(stringResource(R.string.settings_storage_error), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
        val canEdit = !state.isLoading && state.error != SettingsError.Read
        SettingsGroup(stringResource(R.string.vehicle_group)) {
            TextButton(onClick = { showVehicles = true }, modifier = Modifier.fillMaxWidth(), enabled = canEdit) {
                Text(stringResource(R.string.vehicle_type), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(state.vehicleType.labelRes))
            }
        }
        SettingsGroup(stringResource(R.string.warning_settings)) {
            SettingSwitch(stringResource(R.string.voice_warning), state.voiceEnabled, onVoiceChanged, canEdit)
        }
        SettingsGroup(stringResource(R.string.data_settings)) {
            SettingSwitch(stringResource(R.string.contribute_data), state.contributionEnabled, onContributionChanged, canEdit)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingSwitch(stringResource(R.string.wifi_only), state.wifiOnly, onWifiOnlyChanged, canEdit)
        }
        Text(stringResource(R.string.settings_saved_locally), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
    if (showVehicles) {
        AlertDialog(
            onDismissRequest = { showVehicles = false },
            title = { Text(stringResource(R.string.vehicle_type)) },
            text = {
                Column(Modifier.selectableGroup()) {
                    VehicleType.entries.forEach { vehicle ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .selectable(
                                    selected = state.vehicleType == vehicle,
                                    role = Role.RadioButton,
                                    onClick = {
                                        onVehicleSelected(vehicle)
                                        showVehicles = false
                                    }
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(selected = state.vehicleType == vehicle, onClick = null)
                            Text(stringResource(vehicle.labelRes))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showVehicles = false }) { Text(stringResource(R.string.close)) } }
        )
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        content()
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

private val VehicleType.labelRes: Int
    get() = when (this) {
        VehicleType.CAR -> R.string.vehicle_car
        VehicleType.BUS -> R.string.vehicle_bus
        VehicleType.TRUCK -> R.string.vehicle_truck
    }
