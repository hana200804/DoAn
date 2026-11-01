package com.signa.core

import kotlin.test.Test
import kotlin.test.assertEquals

class PassMonitorTest {
    private fun around(d: Double) = mapOf("c1" to ("P.123a" to d))

    @Test
    fun `passing a map sign the camera never saw reports it once`() {
        val m = PassMonitor()
        assertEquals(emptyList(), m.update(ahead = setOf("c1"), around = around(100.0), nowMs = 0))
        assertEquals(emptyList(), m.update(ahead = setOf("c1"), around = around(10.0), nowMs = 5_000))
        assertEquals(listOf("c1"), m.update(ahead = setOf("c1"), around = around(60.0), nowMs = 10_000))
        assertEquals(emptyList(), m.update(ahead = setOf("c1"), around = around(80.0), nowMs = 12_000))
    }

    @Test
    fun `sign seen by the camera is not reported`() {
        val m = PassMonitor()
        m.update(ahead = setOf("c1"), around = around(10.0), nowMs = 0)
        m.cameraSaw("P.123a", 2_000)
        assertEquals(emptyList(), m.update(ahead = setOf("c1"), around = around(60.0), nowMs = 6_000))
    }

    @Test
    fun `standing still near a sign reports nothing`() {
        val m = PassMonitor()
        repeat(100) { assertEquals(emptyList(), m.update(ahead = setOf("c1"), around = around(15.0), nowMs = it * 1_000L)) }
    }

    @Test
    fun `sign for the other direction is never reported`() {
        val m = PassMonitor()
        m.update(ahead = emptySet(), around = around(10.0), nowMs = 0)
        assertEquals(emptyList(), m.update(ahead = emptySet(), around = around(60.0), nowMs = 5_000))
    }

    @Test
    fun `turning away before getting close reports nothing`() {
        val m = PassMonitor()
        m.update(ahead = setOf("c1"), around = around(40.0), nowMs = 0)
        assertEquals(emptyList(), m.update(ahead = setOf("c1"), around = around(200.0), nowMs = 5_000))
    }
}
