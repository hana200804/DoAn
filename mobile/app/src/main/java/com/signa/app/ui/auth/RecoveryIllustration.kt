package com.signa.app.ui.auth

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
internal fun RecoveryIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val x = size.width; val y = size.height
        val blue = Color(0xFF0760EF)
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFEAF4FF), Color(0xFFB2D5FF))), Offset(x*.1f,y*.3f), Size(x*.75f,y*.52f), CornerRadius(x*.09f))
        drawPath(Path().apply { moveTo(x*.1f,y*.8f); lineTo(x*.48f,y*.51f); lineTo(x*.85f,y*.8f); close() }, Color(0xFF6EA7FA))
        drawPath(Path().apply { moveTo(x*.1f,y*.34f); lineTo(x*.48f,y*.58f); lineTo(x*.85f,y*.34f) }, Color(0xFFDCEEFF))
        drawRoundRect(blue, Offset(x*.1f,y*.3f), Size(x*.75f,y*.52f), CornerRadius(x*.09f), style = Stroke(2.5f * density))
        drawLine(blue, Offset(x*.8f,y*.12f), Offset(x*.83f,y*.04f), 4*density, StrokeCap.Round)
        drawLine(blue, Offset(x*.88f,y*.17f), Offset(x*.94f,y*.11f), 4*density, StrokeCap.Round)
        drawLine(blue, Offset(x*.94f,y*.24f), Offset(x*.99f,y*.22f), 4*density, StrokeCap.Round)
    }
}
