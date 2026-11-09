package com.signa.app.ui.sign

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.signa.app.R
import com.signa.app.ui.components.SignaPage
import com.signa.app.ui.components.SignaPrimaryButton
import com.signa.app.ui.components.SpeedLimitBadge

@Composable
fun SignLookupScreen(state: SignLookupUiState, onAction: () -> Unit = {}, onOpenSign: (String) -> Unit = {}) {
    SignaPage(stringResource(R.string.lookup_title)) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SpeedLimitBadge(50, Modifier.size(100.dp))
            Icon(painterResource(R.drawable.ic_camera), null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.lookup_hero), style = MaterialTheme.typography.bodyLarge)
        }
        SignaPrimaryButton(stringResource(R.string.take_photo), onAction, Modifier.fillMaxWidth())
        OutlinedButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.choose_photo)) }
        Text(stringResource(R.string.common_signs), style = MaterialTheme.typography.titleMedium)
        when {
            state.isLoading -> CircularProgressIndicator()
            state.hasError -> Text(stringResource(R.string.data_load_error))
            state.signs.isEmpty() -> Text(stringResource(R.string.no_signs_found))
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            state.signs.forEach { sign ->
                Surface(onClick = { onOpenSign(sign.id) }, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SpeedLimitBadge(sign.speedLimitKmh, Modifier.size(64.dp))
                        Text(sign.name, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
