package com.signa.app.ui.drive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.signa.app.R
import com.signa.app.ui.components.SignaPrimaryButton
import com.signa.app.ui.components.SpeedLimitBadge
import com.signa.app.vision.camera.CameraPreviewStatus

data class DriveCameraUiState(
    val status: CameraPreviewStatus = CameraPreviewStatus.Opening,
    val permissionBlocked: Boolean = false
)

/** Display only. Permission actions and camera ownership live outside this screen. */
@Composable
fun ActiveDriveScreen(
    state: DriveCameraUiState,
    onEnd: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    preview: @Composable (Modifier) -> Unit
) {
    val status = state.status
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cameraHeight = (maxHeight * .55f).coerceIn(200.dp, 440.dp)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.drive_active_title), style = MaterialTheme.typography.headlineSmall)
            Box(Modifier.fillMaxWidth().height(cameraHeight).clip(RoundedCornerShape(20.dp)).background(Color(0xFF101820))) {
                preview(Modifier.fillMaxSize())
                if (status != CameraPreviewStatus.Streaming) {
                    Column(
                        Modifier.fillMaxSize().background(Color(0xFF101820)).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
                    ) {
                        if (status == CameraPreviewStatus.Opening) CircularProgressIndicator(color = Color.White)
                        else Icon(painterResource(R.drawable.ic_camera), null, Modifier.size(48.dp), tint = Color.White)
                        Text(stringResource(cameraStatusText(status)), color = Color.White,
                            style = MaterialTheme.typography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                } else {
                    Surface(Modifier.align(Alignment.TopStart).padding(12.dp), color = Color(0xCC101820), shape = RoundedCornerShape(8.dp)) {
                        Text(stringResource(R.string.camera_streaming), Modifier.padding(10.dp), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("--", style = MaterialTheme.typography.displayMedium)
                            Text(stringResource(R.string.speed_unit), Modifier.padding(start = 8.dp, bottom = 6.dp))
                        }
                        Text(stringResource(R.string.drive_speed_not_connected), style = MaterialTheme.typography.bodySmall)
                    }
                    SpeedLimitBadge(null, Modifier.size(64.dp))
                }
            }
            Text(stringResource(R.string.camera_ai_pending), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (status) {
                CameraPreviewStatus.PermissionRequired -> {
                    SignaPrimaryButton(
                        stringResource(if (state.permissionBlocked) R.string.camera_open_settings else R.string.camera_grant_permission),
                        if (state.permissionBlocked) onOpenSettings else onRequestPermission, Modifier.fillMaxWidth()
                    )
                    if (!state.permissionBlocked) TextButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.camera_open_settings))
                    }
                }
                CameraPreviewStatus.Opening, CameraPreviewStatus.Streaming ->
                    SignaPrimaryButton(stringResource(R.string.camera_pause), onPause, Modifier.fillMaxWidth())
                else -> SignaPrimaryButton(
                    stringResource(if (status == CameraPreviewStatus.Paused) R.string.camera_resume else R.string.retry),
                    onResume, Modifier.fillMaxWidth()
                )
            }
            OutlinedButton(onClick = onEnd, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(10.dp)) {
                Text(stringResource(R.string.end_drive))
            }
        }
    }
}

private fun cameraStatusText(status: CameraPreviewStatus) = when (status) {
    CameraPreviewStatus.Opening -> R.string.camera_opening
    CameraPreviewStatus.Streaming -> R.string.camera_streaming
    CameraPreviewStatus.Paused -> R.string.camera_paused
    CameraPreviewStatus.PermissionRequired -> R.string.camera_permission_required
    CameraPreviewStatus.NoRearCamera -> R.string.camera_no_rear
    CameraPreviewStatus.Busy -> R.string.camera_busy
    CameraPreviewStatus.Disabled -> R.string.camera_disabled
    CameraPreviewStatus.Unavailable -> R.string.camera_unavailable
}
