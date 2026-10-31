package com.signa.app

import android.graphics.Bitmap
import android.graphics.Matrix
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.signa.core.WarningEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/** Màn hình lái xe (FR-01 → FR-05): camera, khung biển, tốc độ, giới hạn, cảnh báo giọng nói. */
@Composable
fun DriveScreen() {
    val context = LocalContext.current
    val app = context.app
    val models = app.models
    if (models == null) {
        Text("Chưa có mô hình nhận diện. Chép bộ mô hình vào app/src/main/assets/models rồi build lại.\n${app.modelError.orEmpty()}", Modifier.padding(24.dp))
        return
    }

    val engine = remember { WarningEngine(models.catalog, app.prefs.vehicle) }
    val speaker = remember { Speaker(context) }
    val location = remember { LocationTracker(context) }
    var fix by remember { mutableStateOf<Fix?>(null) }
    var tracks by remember { mutableStateOf(emptyList<TrackView>()) }
    var frameSize by remember { mutableStateOf(Size(1f, 1f)) }
    var banner by remember { mutableStateOf<String?>(null) }
    val pipeline = remember { Pipeline(models, engine) { obs, crop -> app.queue.add(obs, crop) } }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        val window = (context as ComponentActivity).window
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        location.start { fix = it }
        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            location.stop()
            speaker.shutdown()
            executor.shutdown()
        }
    }

    // Nhịp cảnh báo: tốc độ, biển tải trước phía trước, đọc câu kế tiếp
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            fix?.let { f ->
                withContext(Dispatchers.IO) { runCatching { app.signs.refreshAround(f.lat, f.lng, now) } }
                synchronized(engine) {
                    engine.onSpeed(f.speedKmh, now)
                    app.signs.ahead(f.lat, f.lng, f.bearing).forEach { (s, d) ->
                        engine.onMapSign(s.id, s.code, s.conditions, false, d, f.speedKmh, now)
                    }
                }
            }
            synchronized(engine) { engine.nextSpeech(now) }?.let {
                banner = it.text
                if (app.prefs.voice) speaker.say(it.text)
            }
            delay(250)
        }
    }

    Box(Modifier.fillMaxSize()) {
        CameraPreview(executor) { frame ->
            frameSize = Size(frame.width.toFloat(), frame.height.toFloat())
            tracks = pipeline.process(frame, fix, System.currentTimeMillis())
        }
        TrackOverlay(tracks, frameSize)
        Column(Modifier.align(Alignment.TopStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SpeedBadge(fix?.speedKmh, synchronized(engine) { engine.limitKmh })
            banner?.let {
                Text(it, color = Color.White, fontSize = 18.sp, modifier = Modifier.background(Color(0xCC000000), RoundedCornerShape(8.dp)).padding(12.dp))
            }
        }
    }
}

@Composable
private fun CameraPreview(executor: java.util.concurrent.Executor, onFrame: (Bitmap) -> Unit) {
    val owner = LocalLifecycleOwner.current
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    val provider = future.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(surfaceProvider) }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build()
                    analysis.setAnalyzer(executor) { image ->
                        image.use {
                            val bitmap = it.toBitmap()
                            val rotation = it.imageInfo.rotationDegrees
                            onFrame(
                                if (rotation == 0) bitmap
                                else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation.toFloat()) }, true),
                            )
                        }
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }, ContextCompat.getMainExecutor(ctx))
            }
        },
    )
}

/** Vẽ hộp theo cách PreviewView FILL_CENTER phóng khung hình lên màn hình. */
@Composable
private fun TrackOverlay(tracks: List<TrackView>, frame: Size) {
    Canvas(Modifier.fillMaxSize()) {
        val scale = maxOf(size.width / frame.width, size.height / frame.height)
        val dx = (size.width - frame.width * scale) / 2
        val dy = (size.height - frame.height * scale) / 2
        for (t in tracks) {
            val color = if (t.confirmed) Color(0xFF2E7D32) else Color(0xFFFFC107)
            drawRect(
                color,
                topLeft = Offset(t.box.left * scale + dx, t.box.top * scale + dy),
                size = Size(t.box.width * scale, t.box.height * scale),
                style = Stroke(width = 4f),
            )
        }
    }
}

@Composable
private fun SpeedBadge(speedKmh: Float?, limitKmh: Int?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(72.dp).background(Color.White, CircleShape).border(6.dp, Color(0xFFD32F2F), CircleShape), contentAlignment = Alignment.Center) {
            Text(limitKmh?.toString() ?: "–", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        val over = speedKmh != null && limitKmh != null && speedKmh > limitKmh + WarningEngine.SPEED_TOLERANCE
        Text(
            "${speedKmh?.toInt() ?: "–"} km/h",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.background(if (over) Color(0xFFD32F2F) else Color(0xCC000000), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
