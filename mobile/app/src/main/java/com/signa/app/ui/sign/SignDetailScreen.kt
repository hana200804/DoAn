package com.signa.app.ui.sign

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.signa.app.R
import com.signa.app.ui.components.SignaPage
import com.signa.app.ui.components.SignaStatusLabel
import com.signa.app.ui.components.SpeedLimitBadge
import com.signa.app.ui.components.MapIllustration

@Composable
fun SignDetailScreen(state: SignDetailUiState, onBack: () -> Unit, onReport: () -> Unit = {}) {
    SignaPage(stringResource(R.string.sign_detail), onBack = onBack) {
        val sign = state.sign
        when {
            state.isLoading -> CircularProgressIndicator()
            state.hasError -> Text(stringResource(R.string.data_load_error))
            sign == null -> Text(stringResource(R.string.sign_not_found))
        }
        if (sign == null || state.isLoading || state.hasError) return@SignaPage
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SpeedLimitBadge(sign.speedLimitKmh, Modifier.size(152.dp))
        }
        Text("${sign.code} · ${sign.name}", style = MaterialTheme.typography.titleLarge)
        SignaStatusLabel(stringResource(R.string.sample_data_label))
        Text(stringResource(R.string.sign_meaning_title), style = MaterialTheme.typography.titleMedium)
        Text(sign.meaning)
        Text(stringResource(R.string.map_position), style = MaterialTheme.typography.titleMedium)
        Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
            MapIllustration(Modifier.matchParentSize())
            SpeedLimitBadge(sign.speedLimitKmh, Modifier.size(56.dp))
        }
        Text(stringResource(R.string.sample_sign_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onReport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.report_sign)) }
    }
}
