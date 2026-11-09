package com.signa.app.vision.camera

import android.Manifest
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.signa.app.ui.drive.components.CameraPreview
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assume.assumeNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises real CameraX resources, not just UI labels. Run on a rear-camera device/emulator. */
@RunWith(AndroidJUnit4::class)
class CameraPreviewLifecycleTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun framesResumeAndCameraIsReleasedOnPauseBackgroundAndExit() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val rearId = manager.cameraIdList.firstOrNull {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
        assumeNotNull(rearId)
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} ${Manifest.permission.CAMERA}"
        )).use { it.readBytes() }

        val available = AtomicReference<Boolean?>(null)
        val cameraStatus = AtomicReference<CameraPreviewStatus?>(null)
        val callback = object : CameraManager.AvailabilityCallback() {
            override fun onCameraAvailable(cameraId: String) { if (cameraId == rearId) available.set(true) }
            override fun onCameraUnavailable(cameraId: String) { if (cameraId == rearId) available.set(false) }
        }
        manager.registerAvailabilityCallback(callback, Handler(Looper.getMainLooper()))
        val enabled = mutableStateOf(true)
        val shown = mutableStateOf(true)
        try {
            compose.setContent {
                if (shown.value) CameraPreview(enabled.value, 0, cameraStatus::set, Modifier.fillMaxSize())
            }
            fun awaitStreaming() = compose.waitUntil(30_000) {
                cameraStatus.get() == CameraPreviewStatus.Streaming && available.get() == false
            }
            fun awaitReleased() = compose.waitUntil(15_000) { available.get() == true }

            awaitStreaming()
            compose.runOnIdle { enabled.value = false }
            awaitReleased()
            compose.runOnIdle { cameraStatus.set(null); enabled.value = true }
            awaitStreaming()

            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            awaitReleased()
            cameraStatus.set(null)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            awaitStreaming()

            compose.runOnIdle { shown.value = false }
            awaitReleased()
        } finally {
            manager.unregisterAvailabilityCallback(callback)
        }
    }
}
