package com.signa.app.ui.preview

import androidx.compose.runtime.Composable
import com.signa.app.ui.account.ProfileScreen

/** Explicit design-preview destination, independent of the current authentication session. */
@Composable
fun ProfilePreviewRoute(
    onClose: () -> Unit,
    onContributions: () -> Unit,
    onChangePassword: () -> Unit
) {
    ProfileScreen(
        user = PreviewSamples.profile,
        onClose = onClose,
        onContributions = onContributions,
        onChangePassword = onChangePassword,
        isPreview = true
    )
}
