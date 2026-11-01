package com.signa.core

import kotlin.test.Test
import kotlin.test.assertEquals

class PassMonitorTest {
    private fun around(d: Double) = mapOf("c1" to ("P.123a" to d))

    @Test
    fun `passing a map sign the camera never saw reports it once`() {
        val m = PassMonitor()
        assertEquals(emptyList(), m.update(around(100.0), 0))
        assertEquals(emptyList(), m.update(around(10.0), 5_000))
        assertEquals(listOf("c1"), m.update(around(60.0), 10_000))
        assertEquals(emptyList(), m.update(around(80.0), 12_000))
    }

    @Test
    fun `sign seen by the camera is not reported`() {
        val m = PassMonitor()
        m.update(around(10.0), 0)
        m.cameraSaw("P.123a", 2_000)
        assertEquals(emptyList(), m.update(around(60.0), 6_000))
    }

    @Test
    fun `standing still near a sign reports nothing`() {
        val m = PassMonitor()
        repeat(100) { assertEquals(emptyList(), m.update(around(15.0), it * 1_000L)) }
    }

    @Test
    fun `turning away before getting close reports nothing`() {
        val m = PassMonitor()
        m.update(around(40.0), 0)
        assertEquals(emptyList(), m.update(around(200.0), 5_000))
    }
}
