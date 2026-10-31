package com.signa.app

import android.graphics.Bitmap
import com.signa.core.Box
import com.signa.core.Geo
import com.signa.core.Track
import com.signa.core.Tracker
import com.signa.core.WarningEngine
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID
import kotlin.math.tan

/** Vị trí GPS mới nhất. */
data class Fix(val lat: Double, val lng: Double, val speedKmh: Float, val bearing: Double, val timeMs: Long)

/** Hộp hiển thị trên màn hình lái xe. */
data class TrackView(val box: Box, val code: String?, val confirmed: Boolean)

/**
 * Chuỗi xử lý một khung hình (docs/04-thiet-ke-ai.md §1): phát hiện → phân loại → theo dõi →
 * cảnh báo; track kết thúc thì sinh quan sát.
 */
class Pipeline(
    private val models: ModelBundle,
    private val engine: WarningEngine,
    private val onObservation: (JSONObject, Bitmap) -> Unit,
) {
    private val tracker = Tracker()
    private val samples = HashMap<Int, MutableList<Geo.Sample>>()
    private val bestCrop = HashMap<Int, Pair<Float, Bitmap>>()
    private val announced = HashSet<Int>()

    fun process(frame: Bitmap, fix: Fix?, nowMs: Long): List<TrackView> {
        val result = tracker.update(models.detector.detect(frame))
        var classified = 0
        for (t in result.active) {
            if (classified < MAX_CROPS_PER_FRAME && t.needsClassification()) {
                val crop = crop(frame, t.box) ?: continue
                t.vote(models.classifier.classify(crop))
                classified++
                if (t.box.area > (bestCrop[t.id]?.first ?: 0f)) bestCrop[t.id] = t.box.area to crop
            }
            if (fix != null) {
                samples.getOrPut(t.id) { mutableListOf() } += Geo.Sample(fix.lat, fix.lng, fix.bearing, t.box, frame.width, focalPx(frame.width))
            }
            val code = t.label()
            if (t.confirmed && code != null && announced.add(t.id)) {
                synchronized(engine) { engine.onSign("track:${t.id}", code, null, false, nowMs) }
            }
        }
        result.ended.forEach { emit(it) }
        val alive = result.active.map { it.id }.toSet()
        samples.keys.retainAll(alive)
        bestCrop.keys.retainAll(alive)
        announced.retainAll(alive)
        return result.active.map { TrackView(it.box, it.label(), it.confirmed) }
    }

    private fun emit(t: Track) {
        val code = t.label() ?: return // SPLIT_VOTE: không gửi
        val s = samples[t.id].orEmpty()
        val crop = bestCrop[t.id]?.second ?: return
        if (s.isEmpty()) return
        val group = models.groups[t.group]
        val (lat, lng, dist) = Geo.estimateSignPosition(s, models.signHeightM[group] ?: 0.7)
        val obs = JSONObject()
            .put("clientId", UUID.randomUUID().toString())
            .put("kind", "POSITIVE")
            .put("signCode", code)
            .put("detectorGroup", group)
            .put("classProbs", JSONArray(t.topProbs().map { (c, p) -> JSONObject().put("code", c).put("p", p) }))
            .put("voteShare", t.share())
            .put("location", JSONObject().put("lat", lat).put("lng", lng))
            .put("headingDeg", s.last().headingDeg.toInt().mod(360))
            .put("distanceM", dist.coerceIn(0.0, 500.0))
            .put("frames", t.frames)
            .put("hasSubSign", false)
            .put("observedAt", Instant.now().toString())
        onObservation(obs, crop)
    }

    private fun crop(frame: Bitmap, box: Box): Bitmap? {
        val padW = box.width * 0.1f
        val padH = box.height * 0.1f
        val l = (box.left - padW).toInt().coerceAtLeast(0)
        val t = (box.top - padH).toInt().coerceAtLeast(0)
        val r = (box.right + padW).toInt().coerceAtMost(frame.width)
        val b = (box.bottom + padH).toInt().coerceAtMost(frame.height)
        return if (r - l < 8 || b - t < 8) null else Bitmap.createBitmap(frame, l, t, r - l, b - t)
    }

    companion object {
        const val MAX_CROPS_PER_FRAME = 3

        // shortcut: góc nhìn ngang mặc định 66°; đọc LENS_INTRINSIC_CALIBRATION khi cần sai số vị trí nhỏ hơn
        fun focalPx(imageWidth: Int): Double = imageWidth / 2.0 / tan(Math.toRadians(33.0))
    }
}
