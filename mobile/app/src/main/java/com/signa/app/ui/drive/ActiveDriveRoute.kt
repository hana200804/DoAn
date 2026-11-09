package com.signa.app.ui.drive

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.signa.app.ui.drive.components.CameraPreview
import com.signa.app.vision.camera.CameraPreviewStatus

@Composable
fun ActiveDriveRoute(onEnd: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(hasPermission()) }
    var requested by rememberSaveable { mutableStateOf(false) }
    var blocked by rememberSaveable { mutableStateOf(false) }
    var enabled by rememberSaveable { mutableStateOf(true) }
    var attempt by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf(CameraPreviewStatus.Opening) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        granted = allowed || hasPermission()
        blocked = !granted && context.activityOrNull()?.let {
            !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
        } == true
        status = if (granted) CameraPreviewStatus.Opening else CameraPreviewStatus.PermissionRequired
    }
    // Entry follows Start driving; opening the landing tab never requests camera access.
    LaunchedEffect(Unit) {
        if (!granted && !requested) {
            requested = true
            permission.launch(Manifest.permission.CAMERA)
        }
    }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = hasPermission()
                if (granted) blocked = false
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val displayedStatus = when {
        !granted -> CameraPreviewStatus.PermissionRequired
        !enabled -> CameraPreviewStatus.Paused
        else -> status
    }
    ActiveDriveScreen(
        state = DriveCameraUiState(displayedStatus, blocked),
        onEnd = onEnd,
        onPause = { enabled = false },
        onResume = { status = CameraPreviewStatus.Opening; enabled = true; attempt++ },
        onRequestPermission = {
            requested = true
            permission.launch(Manifest.permission.CAMERA)
        },
        onOpenSettings = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
        },
        preview = { modifier ->
            CameraPreview(enabled = granted && enabled, attempt = attempt, onStatus = { status = it }, modifier = modifier)
        }
    )
}

private tailrec fun Context.activityOrNull(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activityOrNull()
    else -> null
}
