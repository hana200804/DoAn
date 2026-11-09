package com.signa.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp

/** Local illustration for the UI prototype, independent of map services and GPS. */
@Composable
fun MapIllustration(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.clip(RoundedCornerShape(0.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(0xFFE9EEE8))
            drawRoundRect(Color(0xFFD5E6CD), Offset(size.width * .08f, size.height * .26f), androidx.compose.ui.geometry.Size(size.width * .23f, size.height * .18f), androidx.compose.ui.geometry.CornerRadius(18f))
            drawRoundRect(Color(0xFFD8E6D1), Offset(size.width * .50f, size.height * .53f), androidx.compose.ui.geometry.Size(size.width * .30f, size.height * .19f), androidx.compose.ui.geometry.CornerRadius(20f))
            val river = Path().apply {
                moveTo(size.width * .90f, -20f)
                cubicTo(size.width * .48f, size.height * .30f, size.width * 1.25f, size.height * .48f, size.width * .80f, size.height * .72f)
                lineTo(size.width * .66f, size.height * 1.1f)
            }
            drawPath(river, Color(0xFFB5DDF3), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 48.dp.toPx()))
            for (index in 0..7) {
                val x = size.width * index / 6
                drawLine(Color.White, Offset(x, 0f), Offset(x - size.width * .30f, size.height), 9.dp.toPx())
            }
            for (index in 1..8) {
                val y = size.height * index / 9
                drawLine(Color.White, Offset(0f, y), Offset(size.width, y + size.height * .16f), 9.dp.toPx())
            }
            drawLine(Color(0xFFFFE5A6), Offset(0f, size.height * .68f), Offset(size.width, size.height * .35f), 12.dp.toPx())
        }
        Text("Bình Thạnh", Modifier.offset(maxWidth * .52f, maxHeight * .30f), color = Color(0xFF58665E), style = MaterialTheme.typography.labelLarge)
        Text("TP. Hồ Chí Minh", Modifier.align(Alignment.Center).offset(y = (-55).dp), color = Color(0xFF394A43), style = MaterialTheme.typography.titleLarge)
        Box(
            Modifier.offset(maxWidth * .28f, maxHeight * .60f).size(22.dp).background(Color.White, CircleShape).padding(4.dp).background(Color(0xFF1456B8), CircleShape)
        )
    }
}
