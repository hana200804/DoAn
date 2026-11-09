package com.signa.app.ui.drive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.signa.app.R
import com.signa.app.ui.components.SignaPrimaryButton
import com.signa.app.ui.components.SignaWordmark
import com.signa.app.ui.components.SpeedLimitBadge
import com.signa.app.ui.drive.components.CameraIllustration

@Composable
fun DriveScreen(modifier: Modifier = Modifier, onStart: () -> Unit = {}) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            SignaWordmark()
            Text(stringResource(R.string.gps_waiting), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("--", style = MaterialTheme.typography.displayLarge)
                    Text(stringResource(R.string.speed_unit), Modifier.padding(start = 8.dp, bottom = 10.dp))
                }
                SpeedLimitBadge(null, Modifier.size(64.dp))
            }
        }
        CameraIllustration(Modifier.fillMaxWidth().heightIn(min = 280.dp))
        SignaPrimaryButton(stringResource(R.string.start_drive), onStart, Modifier.fillMaxWidth())
        Text(stringResource(R.string.camera_preview_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
