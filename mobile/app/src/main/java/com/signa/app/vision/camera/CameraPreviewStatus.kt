package com.signa.app.vision.camera

/** Ephemeral device state, independent of authentication, backend and recognition. */
enum class CameraPreviewStatus {
    Opening, Streaming, Paused, PermissionRequired, NoRearCamera, Busy, Disabled, Unavailable
}
