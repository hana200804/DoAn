package com.signa.app.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.signa.app.R
import com.signa.app.ui.components.SignaPage
import com.signa.app.ui.components.SignaPrimaryButton
import com.signa.app.ui.components.SignaSectionCard

@Composable
fun MapScreen(onOpenSign: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    SignaPage(stringResource(R.string.nav_map)) {
        OutlinedTextField(
            value = "", onValueChange = {}, enabled = false,
            label = { Text(stringResource(R.string.map_search)) },
            modifier = Modifier.fillMaxWidth()
        )
        Box(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(24.dp))) {
            Canvas(Modifier.matchParentSize()) {
                drawRect(colors.surfaceVariant)
                for (index in 1..4) {
                    val x = size.width * index / 5
                    drawLine(colors.surface, Offset(x, 0f), Offset(x - size.width / 4, size.height), 18.dp.toPx())
                }
                drawLine(colors.surface, Offset(0f, size.height * .35f), Offset(size.width, size.height * .65f), 24.dp.toPx())
            }
            Text(stringResource(R.string.map_placeholder), Modifier.align(Alignment.Center), color = colors.onSurfaceVariant)
        }
        Text(stringResource(R.string.map_hint), color = colors.onSurfaceVariant)
        SignaSectionCard(stringResource(R.string.sample_sign)) {
            Text(stringResource(R.string.sample_sign_name), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.sample_location))
            SignaPrimaryButton(stringResource(R.string.view_detail), onClick = onOpenSign, modifier = Modifier.fillMaxWidth())
        }
    }
}
