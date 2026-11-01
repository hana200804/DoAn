package com.signa.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class GeoTest {
    @Test
    fun `UT-M11 distance from box height`() {
        val d = Geo.distanceToSignM(boxHeightPx = 50f, signHeightM = 0.7, focalPx = 1000.0)
        assertTrue(abs(d - 14.0) < 0.14)
    }

    @Test
    fun `sign straight ahead lands on the heading line`() {
        val box = Box(310f, 100f, 330f, 120f) // tâm ở giữa ảnh rộng 640, cao 20 px
        val samples = List(5) { Geo.Sample(21.0, 105.8, 0.0, box, 640, 1000.0) }

        val (lat, lng, dist) = Geo.estimateSignPosition(samples, 0.7)

        assertTrue(abs(dist - 35.0) < 0.5)
        assertTrue(abs(Geo.distanceM(21.0, 105.8, lat, lng) - 35.0) < 0.5)
        assertTrue(abs(lng - 105.8) < 1e-6, "biển ở chính phía bắc")
    }

    @Test
    fun `bearing and offset agree`() {
        val (lat, lng) = Geo.offset(21.0, 105.8, 100.0, 90.0)
        assertTrue(abs(Geo.bearingDeg(21.0, 105.8, lat, lng) - 90.0) < 0.5)
        assertTrue(abs(Geo.distanceM(21.0, 105.8, lat, lng) - 100.0) < 0.5)
    }
}
