package com.signa.app.ui.drive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.signa.app.R
import com.signa.app.ui.components.SignaPrimaryButton
import com.signa.app.ui.components.SignaSectionCard
import com.signa.app.ui.components.SignaStatusLabel
import com.signa.app.ui.components.SpeedLimitBadge
import com.signa.app.ui.theme.SignaTheme

@Composable
fun DriveScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.drive_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SignaStatusLabel(stringResource(R.string.ui_demo_status))
        SignaSectionCard(title = stringResource(R.string.current_speed)) {
            Text("--", style = MaterialTheme.typography.displayLarge)
            Text(stringResource(R.string.speed_unit))
            Text(
                stringResource(R.string.gps_unavailable),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SignaSectionCard(title = stringResource(R.string.current_limit)) {
            SpeedLimitBadge(limitKmh = null)
            Text(stringResource(R.string.speed_limit_unknown))
        }
        SignaSectionCard(title = stringResource(R.string.camera_title)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.camera_unavailable))
                Text(
                    stringResource(R.string.camera_demo_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        SignaPrimaryButton(
            text = stringResource(R.string.start_drive),
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            stringResource(R.string.drive_demo_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true, name = "Light")
@Preview(showBackground = true, name = "Dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, name = "Large text", fontScale = 1.5f)
@Composable
private fun DriveScreenPreview() {
    SignaTheme {
        Scaffold { padding -> DriveScreen(Modifier.padding(padding)) }
    }
}
