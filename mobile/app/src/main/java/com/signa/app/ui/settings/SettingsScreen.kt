package com.signa.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.signa.app.ui.components.SignaPage
import com.signa.app.ui.components.SignaSectionCard

@Composable
fun SettingsScreen() {
    var vehicle by rememberSaveable { mutableStateOf(0) }
    var voice by rememberSaveable { mutableStateOf(true) }
    var contribution by rememberSaveable { mutableStateOf(false) }
    var wifi by rememberSaveable { mutableStateOf(true) }
    SignaPage(stringResource(R.string.nav_settings)) {
        Text(stringResource(R.string.settings_demo_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
        SignaSectionCard(stringResource(R.string.vehicle_type)) {
            Column(Modifier.selectableGroup()) {
                listOf(R.string.vehicle_car, R.string.vehicle_bus, R.string.vehicle_truck).forEachIndexed { index, label ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(vehicle == index, role = Role.RadioButton, onClick = { vehicle = index }),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(selected = vehicle == index, onClick = null)
                        Text(stringResource(label))
                    }
                }
            }
        }
        SignaSectionCard(stringResource(R.string.warning_settings)) {
            SettingSwitch(stringResource(R.string.voice_warning), voice, { voice = it })
        }
        SignaSectionCard(stringResource(R.string.data_settings)) {
            SettingSwitch(stringResource(R.string.contribute_data), contribution, { contribution = it })
            SettingSwitch(stringResource(R.string.wifi_only), wifi, { wifi = it })
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}
