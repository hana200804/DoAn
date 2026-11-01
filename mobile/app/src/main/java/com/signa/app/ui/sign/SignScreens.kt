package com.signa.app.ui.sign

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.signa.app.R
import com.signa.app.ui.components.SignaPage
import com.signa.app.ui.components.SignaPrimaryButton
import com.signa.app.ui.components.SignaSectionCard
import com.signa.app.ui.components.SpeedLimitBadge

@Composable
fun SignLookupScreen() {
    SignaPage(stringResource(R.string.nav_sign)) {
        SignaSectionCard(stringResource(R.string.lookup_title)) {
            Text(stringResource(R.string.lookup_hint))
            SignaPrimaryButton(stringResource(R.string.take_photo), {}, Modifier.fillMaxWidth(), enabled = false)
            SignaPrimaryButton(stringResource(R.string.choose_photo), {}, Modifier.fillMaxWidth(), enabled = false)
        }
        Text(stringResource(R.string.lookup_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SignDetailScreen(onBack: () -> Unit) {
    SignaPage(stringResource(R.string.sign_detail)) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        SignaSectionCard(stringResource(R.string.sample_sign_name)) {
            SpeedLimitBadge(50)
            Text(stringResource(R.string.sample_sign_code), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.sample_sign_meaning))
            Text(stringResource(R.string.sample_sign_disclaimer), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SignaSectionCard(stringResource(R.string.sign_verification)) {
            Text(stringResource(R.string.verification_unavailable))
            SignaPrimaryButton(stringResource(R.string.report_sign), {}, Modifier.fillMaxWidth(), enabled = false)
        }
    }
}
