package com.signa.app

import android.graphics.Color
import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.sources.GeoJsonSource

private const val LAYER = "signs-layer"

/** Bản đồ biển báo quanh vị trí (FR-06), lọc theo điểm tin cậy (FR-07), chi tiết và báo cáo sai (FR-08). */
@Composable
fun MapScreen() {
    val context = LocalContext.current
    val app = context.app
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var signs by remember { mutableStateOf(emptyList<MapSign>()) }
    var selected by remember { mutableStateOf<MapSign?>(null) }
    var includeCandidates by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    val mapView = remember { MapView(context).apply { onCreate(Bundle()) } }

    fun reload() {
        val m = map ?: return
        val b = m.projection.visibleRegion.latLngBounds
        if (b.latitudeSpan > 0.4 || b.longitudeSpan > 0.4) return // phóng to hơn mới tải
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { SignStore.parse(app.signs.fetch(b.longitudeWest, b.latitudeSouth, b.longitudeEast, b.latitudeNorth, includeCandidates)) }
            }.onSuccess { list ->
                signs = list
                m.style?.getSourceAs<GeoJsonSource>("signs")?.setGeoJson(geoJson(list))
            }.onFailure { message = it.message }
        }
    }

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        mapView.getMapAsync { m ->
            m.cameraPosition = CameraPosition.Builder().target(app.signs.signs.firstOrNull()?.let { LatLng(it.lat, it.lng) } ?: LatLng(21.0285, 105.8542)).zoom(15.0).build()
            m.setStyle(styleUrl()) { style ->
                style.addSource(GeoJsonSource("signs", geoJson(emptyList())))
                style.addLayer(
                    CircleLayer(LAYER, "signs").withProperties(
                        circleRadius(8f),
                        circleColor(Color.parseColor("#D32F2F")),
                        circleStrokeColor(Color.WHITE),
                        circleStrokeWidth(2f),
                    ),
                )
                map = m
                reload()
            }
            m.addOnCameraIdleListener { reload() }
            m.addOnMapClickListener { point ->
                val id = m.queryRenderedFeatures(m.projection.toScreenLocation(point), LAYER).firstOrNull()?.getStringProperty("id")
                selected = signs.firstOrNull { it.id == id }
                id != null
            }
        }
        onDispose {
            owner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView({ mapView }, Modifier.fillMaxSize())
        FilterChip(
            selected = includeCandidates,
            onClick = { includeCandidates = !includeCandidates; reload() },
            label = { Text("Hiện cả biển chưa xác minh") },
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
        )
        selected?.let { s ->
            SignCard(s, onClose = { selected = null }, onReported = { message = it })
        }
        message?.let {
            AlertDialog(onDismissRequest = { message = null }, confirmButton = { TextButton({ message = null }) { Text("Đóng") } }, text = { Text(it) })
        }
    }
}

@Composable
private fun SignCard(s: MapSign, onClose: () -> Unit, onReported: (String) -> Unit) {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    var reporting by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Card(Modifier.fillMaxWidth().padding(12.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${s.code} · ${s.name}")
                Text("Độ tin cậy ${"%.0f".format(s.confidence * 100)}% · ${s.observationCount} lượt thấy · ${s.status}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { reporting = true }) { Text("Báo cáo sai") }
                    TextButton(onClose) { Text("Đóng") }
                }
            }
        }
    }
    if (reporting) {
        val reasons = listOf("NOT_EXIST" to "Không tồn tại", "WRONG_TYPE" to "Sai loại biển", "WRONG_POSITION" to "Sai vị trí", "OTHER" to "Khác")
        AlertDialog(
            onDismissRequest = { reporting = false },
            confirmButton = {},
            title = { Text("Biển này sai thế nào?") },
            text = {
                Column {
                    reasons.forEach { (code, label) ->
                        TextButton(onClick = {
                            reporting = false
                            scope.launch {
                                val result = runCatching {
                                    withContext(Dispatchers.IO) { app.api.post("/signs/${s.id}/reports", JSONObject().put("reason", code)) }
                                }
                                onReported(result.fold({ "Đã gửi báo cáo, cảm ơn bạn" }, { it.message ?: "Gửi báo cáo lỗi" }))
                            }
                        }) { Text(label) }
                    }
                }
            },
        )
    }
}

// shortcut: style VietMap theo tài liệu công khai, cần kiểm tra với khoá thật; chưa có khoá thì dùng bản đồ demo của MapLibre
private fun styleUrl(): String =
    if (BuildConfig.VIETMAP_API_KEY.isBlank()) "https://demotiles.maplibre.org/style.json"
    else "https://maps.vietmap.vn/api/maps/light/styles.json?apikey=${BuildConfig.VIETMAP_API_KEY}"

private fun geoJson(signs: List<MapSign>): String = JSONObject()
    .put("type", "FeatureCollection")
    .put(
        "features",
        JSONArray(
            signs.map {
                JSONObject()
                    .put("type", "Feature")
                    .put("geometry", JSONObject().put("type", "Point").put("coordinates", JSONArray(listOf(it.lng, it.lat))))
                    .put("properties", JSONObject().put("id", it.id))
            },
        ),
    ).toString()
