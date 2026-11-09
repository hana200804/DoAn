package com.signa.app.ui.drive.components

import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.signa.app.vision.camera.CameraPreviewSession
import com.signa.app.vision.camera.CameraPreviewStatus

/** Compose adapter only; camera ownership and device handling live in vision/camera. */
@Composable
internal fun CameraPreview(
    enabled: Boolean,
    attempt: Int,
    onStatus: (CameraPreviewStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val latestStatus by rememberUpdatedState(onStatus)
    val description = stringResource(com.signa.app.R.string.camera_live_description)
    val view = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    AndroidView(factory = { view }, update = { it.contentDescription = description }, modifier = modifier)
    DisposableEffect(view, owner, enabled, attempt) {
        val session = if (enabled) CameraPreviewSession(context, view, owner) { latestStatus(it) } else null
        onDispose { session?.close() }
    }
}
