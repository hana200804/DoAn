package com.signa.app

import com.signa.core.Conditions
import com.signa.core.Geo
import com.signa.core.VehicleType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalTime

/** Cụm biển trên bản đồ (GET /signs). */
data class MapSign(
    val id: String,
    val code: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val headingDeg: Double,
    val confidence: Double,
    val status: String,
    val observationCount: Int,
    val conditions: Conditions?,
)

/** Biển đã xác minh quanh vị trí, tải trước để cảnh báo cả khi mất mạng (FR-04). */
class SignStore(private val file: File, private val api: Api) {
    @Volatile
    var signs: List<MapSign> = runCatching { parse(file.readText()) }.getOrDefault(emptyList())
        private set
    private var center: Pair<Double, Double>? = null
    private var fetchedAtMs = 0L

    /** Tải lại vùng ±2 km khi đã đi xa hơn 1 km hoặc dữ liệu cũ quá 10 phút. Gọi trên luồng nền. */
    fun refreshAround(lat: Double, lng: Double, nowMs: Long) {
        val c = center
        val moved = c == null || Geo.distanceM(c.first, c.second, lat, lng) > 1_000
        if (!moved && nowMs - fetchedAtMs < 10 * 60_000) return
        val text = fetch(lng - 0.02, lat - 0.02, lng + 0.02, lat + 0.02, includeCandidates = false)
        signs = parse(text)
        // Ghi file tạm rồi đổi tên: app bị dừng giữa chừng không làm hỏng bản cache cũ
        val tmp = File(file.path + ".tmp")
        tmp.writeText(text)
        tmp.renameTo(file)
        center = lat to lng
        fetchedAtMs = nowMs
    }

    fun fetch(minLng: Double, minLat: Double, maxLng: Double, maxLat: Double, includeCandidates: Boolean): String =
        api.get("/signs?bbox=$minLng,$minLat,$maxLng,$maxLat&includeCandidates=$includeCandidates")

    /** Biển phía trước theo hướng đi, trong 300 m, cùng chiều xe chạy; kèm khoảng cách. */
    fun ahead(lat: Double, lng: Double, heading: Double): List<Pair<MapSign, Double>> = signs.mapNotNull { s ->
        val d = Geo.distanceM(lat, lng, s.lat, s.lng)
        val inFront = Geo.headingDiff(Geo.bearingDeg(lat, lng, s.lat, s.lng), heading) < 30
        val sameDirection = Geo.headingDiff(s.headingDeg, heading) < 45
        if (d < 300 && inFront && sameDirection) s to d else null
    }

    companion object {
        fun parse(text: String): List<MapSign> {
            val items = JSONObject(text).getJSONArray("items")
            return List(items.length()) { i ->
                val o = items.getJSONObject(i)
                val loc = o.getJSONObject("location")
                MapSign(
                    id = o.getString("id"),
                    code = o.getString("signCode"),
                    name = o.optString("name"),
                    lat = loc.getDouble("lat"),
                    lng = loc.getDouble("lng"),
                    headingDeg = o.getDouble("headingDeg"),
                    confidence = o.getDouble("confidence"),
                    status = o.getString("status"),
                    observationCount = o.optInt("observationCount"),
                    conditions = o.optJSONObject("conditions")?.let(::conditions),
                )
            }
        }

        private fun conditions(c: JSONObject) = Conditions(
            timeWindows = c.optJSONArray("timeWindows").objects().map {
                LocalTime.parse(it.getString("from")) to LocalTime.parse(it.getString("to"))
            },
            include = c.optJSONObject("vehicles")?.optJSONArray("include").strings().map(VehicleType::valueOf).toSet(),
            exclude = c.optJSONObject("vehicles")?.optJSONArray("exclude").strings().map(VehicleType::valueOf).toSet(),
        )

        private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else List(length()) { getJSONObject(it) }
        private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else List(length()) { getString(it) }
    }
}
