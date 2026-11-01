package com.signa.core

import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Phép tính địa lý cho khoảng cách ngắn và ước lượng vị trí biển (docs/04-thiet-ke-ai.md §7). */
object Geo {
    private const val R = 6_371_000.0

    fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * R * asin(sqrt(a))
    }

    /** Hướng từ điểm 1 tới điểm 2, độ, 0 = bắc. */
    fun bearingDeg(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lng2 - lng1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    fun headingDiff(a: Double, b: Double): Double {
        val d = ((a - b) % 360 + 360) % 360
        return minOf(d, 360 - d)
    }

    /** Dịch một điểm đi [distanceM] mét theo hướng [bearingDeg] (xấp xỉ mặt phẳng cục bộ). */
    fun offset(lat: Double, lng: Double, distanceM: Double, bearingDeg: Double): Pair<Double, Double> {
        val b = Math.toRadians(bearingDeg)
        val dLat = distanceM * cos(b) / R
        val dLng = distanceM * sin(b) / (R * cos(Math.toRadians(lat)))
        return (lat + Math.toDegrees(dLat)) to (lng + Math.toDegrees(dLng))
    }

    /** Khoảng cách tới biển từ chiều cao hộp: d = f × H / h. */
    fun distanceToSignM(boxHeightPx: Float, signHeightM: Double, focalPx: Double): Double =
        focalPx * signHeightM / boxHeightPx

    /** Một khung hình của track, đủ để ước lượng vị trí biển. */
    data class Sample(
        val lat: Double,
        val lng: Double,
        val headingDeg: Double,
        val box: Box,
        val imageWidthPx: Int,
        val focalPx: Double,
    )

    /** Vị trí biển: trung vị có trọng số (theo diện tích hộp) của ước lượng từng khung. Trả (lat, lng, khoảng cách). */
    fun estimateSignPosition(samples: List<Sample>, signHeightM: Double): Triple<Double, Double, Double> {
        require(samples.isNotEmpty())
        val estimates = samples.map { s ->
            val d = distanceToSignM(s.box.height, signHeightM, s.focalPx)
            val angle = Math.toDegrees(atan((s.box.centerX - s.imageWidthPx / 2.0) / s.focalPx))
            val (lat, lng) = offset(s.lat, s.lng, d, s.headingDeg + angle)
            Triple(lat, lng, d) to s.box.area.toDouble()
        }
        return Triple(
            weightedMedian(estimates.map { it.first.first to it.second }),
            weightedMedian(estimates.map { it.first.second to it.second }),
            weightedMedian(estimates.map { it.first.third to it.second }),
        )
    }

    fun weightedMedian(values: List<Pair<Double, Double>>): Double {
        val sorted = values.sortedBy { it.first }
        val half = sorted.sumOf { it.second } / 2
        var acc = 0.0
        for ((v, w) in sorted) {
            acc += w
            if (acc >= half) return v
        }
        return sorted.last().first
    }
}
