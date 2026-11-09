package com.signa.app.ui.drive.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.signa.app.R

@Composable
fun CameraIllustration(modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(24.dp)).background(Color(0xFFDBE6EC))) {
        Canvas(Modifier.matchParentSize()) {
            drawRect(Color(0xFFDAE9F1))
            drawRect(Color(0xFFADC1AE), Offset(0f, size.height * .48f), androidx.compose.ui.geometry.Size(size.width, size.height * .52f))
            val road = Path().apply {
                moveTo(size.width * .45f, size.height * .48f)
                lineTo(size.width * .55f, size.height * .48f)
                lineTo(size.width * .98f, size.height)
                lineTo(size.width * .02f, size.height)
                close()
            }
            drawPath(road, Color(0xFF667483))
            drawLine(Color.White.copy(alpha = .70f), Offset(size.width * .5f, size.height * .60f), Offset(size.width * .5f, size.height), 3.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(20f, 25f)))
        }
        Surface(Modifier.align(Alignment.TopStart).padding(12.dp), shape = RoundedCornerShape(8.dp)) {
            Text(stringResource(R.string.camera_preview_label), Modifier.padding(8.dp), style = MaterialTheme.typography.labelMedium)
        }
        Icon(painterResource(R.drawable.ic_camera), null, Modifier.align(Alignment.Center).size(56.dp), tint = Color.White)
    }
}
