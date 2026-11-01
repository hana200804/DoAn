package com.signa.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** UT-M01 → UT-M04 (docs/08-kiem-thu.md §2.1). */
class TrackerTest {
    private val box = Box(100f, 100f, 140f, 140f)
    private fun det(b: Box = box, score: Float = 0.8f) = Detection(b, group = 0, score = score)

    @Test
    fun `UT-M01 a single-frame detection never confirms and leaves no observation`() {
        val tracker = Tracker()
        tracker.update(listOf(det()))
        repeat(5) {
            val r = tracker.update(emptyList())
            assertTrue(r.ended.isEmpty())
        }
        assertTrue(tracker.update(emptyList()).active.isEmpty())
    }

    @Test
    fun `UT-M02 three hits in five frames confirm the track`() {
        val tracker = Tracker()
        tracker.update(listOf(det()))
        tracker.update(emptyList())
        tracker.update(listOf(det()))
        val r = tracker.update(listOf(det()))
        assertTrue(r.active.single().confirmed)
    }

    @Test
    fun `UT-M03 a confirmed track ends once after eleven misses`() {
        val tracker = Tracker()
        repeat(3) { tracker.update(listOf(det())) }
        var ended = 0
        repeat(15) { ended += tracker.update(emptyList()).ended.size }
        assertEquals(1, ended)
    }

    @Test
    fun `low-score detections only extend existing tracks`() {
        val tracker = Tracker()
        assertTrue(tracker.update(listOf(det(score = 0.3f))).active.isEmpty())
        tracker.update(listOf(det()))
        val moved = Box(102f, 101f, 143f, 142f)
        val r = tracker.update(listOf(det(moved, score = 0.3f)))
        assertEquals(moved, r.active.single().box)
    }

    @Test
    fun `UT-M04 split vote gives no label`() {
        val track = Tracker().update(listOf(det())).active.single()
        track.vote(mapOf("P.123a" to 0.55f, "P.123b" to 0.45f))
        assertNull(track.label())
        assertTrue(track.needsClassification())

        track.vote(mapOf("P.123a" to 0.95f, "P.123b" to 0.05f))
        assertEquals("P.123a", track.label())
        assertFalse(track.needsClassification())
    }

    @Test
    fun `uncertain classification is rejected instead of inflating the vote`() {
        val track = Tracker().update(listOf(det())).active.single()
        val rest = (1..19).associate { "X.$it" to 0.6f / 19 }
        track.vote(rest + ("P.123a" to 0.4f))
        assertNull(track.label())
        assertEquals(0.0, track.share())
    }

    @Test
    fun `sub-sign directly below is attached, one beside or far below is not`() {
        val main = Box(100f, 100f, 200f, 200f)
        assertTrue(main.holdsSubSign(Box(110f, 205f, 190f, 245f)))
        assertFalse(main.holdsSubSign(Box(220f, 205f, 300f, 245f)))
        assertFalse(main.holdsSubSign(Box(110f, 300f, 190f, 340f)))
    }
}
