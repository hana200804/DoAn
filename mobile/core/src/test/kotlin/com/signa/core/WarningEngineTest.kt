package com.signa.core

import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** UT-M05 → UT-M10 (docs/08-kiem-thu.md §2.1). */
class WarningEngineTest {
    private val catalog = listOf(
        SignInfo("P.127_50", SignGroup.PROHIBITORY, "Tốc độ tối đa cho phép 50 km/h", speedKmh = 50),
        SignInfo("P.123a", SignGroup.PROHIBITORY, "Cấm rẽ trái"),
        SignInfo("P.131a", SignGroup.PROHIBITORY, "Cấm đỗ xe"),
        SignInfo("W.225", SignGroup.WARNING, "Trẻ em"),
    ).associateBy { it.code }

    private fun engine(time: LocalTime = LocalTime.NOON) = WarningEngine(catalog, VehicleType.CAR) { time }

    /** Đọc hết câu trong hàng đợi, mỗi câu cách nhau 4 giây. */
    private fun WarningEngine.drain(fromMs: Long): List<String> =
        generateSequence(fromMs) { it + WarningEngine.MIN_GAP_MS }.take(10)
            .mapNotNull { nextSpeech(it)?.text }.toList()

    private fun WarningEngine.withLimit50() = apply {
        onSign("track:1", "P.127_50", null, false, 0)
        nextSpeech(0)
    }

    @Test
    fun `UT-M05 over limit for two seconds stays quiet`() {
        val e = engine().withLimit50()
        e.onSpeed(54f, 10_000)
        e.onSpeed(54f, 12_000)
        assertEquals(emptyList(), e.drain(12_000))
    }

    @Test
    fun `UT-M06 over limit for three seconds warns once`() {
        val e = engine().withLimit50()
        e.onSpeed(54f, 10_000)
        e.onSpeed(54f, 13_000)
        e.onSpeed(55f, 14_000)
        assertEquals(listOf("Vượt tốc độ, giới hạn 50"), e.drain(14_000))
    }

    @Test
    fun `UT-M07 no warning while limit is unknown`() {
        val e = engine()
        e.onSpeed(90f, 0)
        e.onSpeed(90f, 5_000)
        assertNull(e.limitKmh)
        assertEquals(emptyList(), e.drain(5_000))
    }

    @Test
    fun `UT-M08 sub-sign time window outside now suppresses warning`() {
        val e = engine(LocalTime.of(23, 0))
        val sixToTen = Conditions(timeWindows = listOf(LocalTime.of(6, 0) to LocalTime.of(22, 0)))
        e.onSign("track:1", "P.131a", sixToTen, true, 0)
        assertEquals(emptyList(), e.drain(0))
    }

    @Test
    fun `UT-M09 same sign from map and camera is read once`() {
        val e = engine()
        e.onMapSign("c1", "P.123a", null, false, distanceAheadM = 40.0, speedKmh = 40f, nowMs = 0)
        e.onSign("track:7", "P.123a", null, false, 2_000)
        assertEquals(listOf("Phía trước cấm rẽ trái"), e.drain(2_000))
    }

    @Test
    fun `UT-M10 queue reads by priority with four-second gaps`() {
        val e = engine().withLimit50()
        e.onSign("track:2", "W.225", null, false, 9_000)
        e.onSign("track:3", "P.123a", null, false, 9_000)
        e.onSpeed(60f, 6_000)
        e.onSpeed(60f, 9_000)

        assertEquals("Vượt tốc độ, giới hạn 50", e.nextSpeech(9_000)?.text)
        assertNull(e.nextSpeech(11_000))
        assertEquals("Phía trước cấm rẽ trái", e.nextSpeech(13_000)?.text)
        assertNull(e.nextSpeech(17_000), "câu đã chờ quá 5 giây bị bỏ")
    }

    @Test
    fun `map sign far ahead waits until within range`() {
        val e = engine()
        e.onMapSign("c1", "W.225", null, false, distanceAheadM = 300.0, speedKmh = 50f, nowMs = 0)
        assertEquals(emptyList(), e.drain(0))
    }

    @Test
    fun `unreadable sub-sign adds a caution`() {
        val e = engine()
        e.onSign("track:1", "P.131a", null, true, 0)
        assertEquals(listOf("Phía trước cấm đỗ xe, có biển phụ, chú ý điều kiện"), e.drain(0))
    }
}
