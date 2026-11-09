package com.signa.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signa.app.R

@Composable
fun SpeedLimitBadge(limitKmh: Int?, modifier: Modifier = Modifier) {
    val description = if (limitKmh == null) {
        stringResource(R.string.speed_limit_unknown)
    } else {
        stringResource(R.string.speed_limit_description, limitKmh)
    }
    Surface(
        modifier = modifier.size(80.dp).clearAndSetSemantics { contentDescription = description },
        shape = CircleShape,
        border = BorderStroke(4.dp, MaterialTheme.colorScheme.error),
        color = MaterialTheme.colorScheme.surface
    ) {
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            Text(limitKmh?.toString() ?: "--", fontSize = minOf(maxWidth.value * .43f, 64f).sp, fontWeight = FontWeight.Bold)
        }
    }
}
