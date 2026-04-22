package io.github.barsia.speqa.editor.ui.steps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DragAutoScrollTest {
    private val edgeZone = 40f
    private val maxSpeed = 20f

    @Test
    fun `item in middle of viewport returns zero`() {
        val delta = DragAutoScroll.computeScrollDelta(
            itemTop = 300f, itemBottom = 340f,
            viewportTop = 100f, viewportBottom = 600f,
            edgeZonePx = edgeZone, maxSpeedPxPerFrame = maxSpeed,
        )
        assertEquals(0f, delta)
    }

    @Test
    fun `item top fully inside top edge zone returns max negative speed`() {
        val delta = DragAutoScroll.computeScrollDelta(
            itemTop = 100f, itemBottom = 140f,
            viewportTop = 100f, viewportBottom = 600f,
            edgeZonePx = edgeZone, maxSpeedPxPerFrame = maxSpeed,
        )
        assertEquals(-maxSpeed, delta)
    }

    @Test
    fun `item top partially in top edge zone returns proportional negative speed`() {
        val delta = DragAutoScroll.computeScrollDelta(
            itemTop = 120f, itemBottom = 160f,
            viewportTop = 100f, viewportBottom = 600f,
            edgeZonePx = edgeZone, maxSpeedPxPerFrame = maxSpeed,
        )
        assertEquals(-10f, delta)
    }

    @Test
    fun `item bottom fully inside bottom edge zone returns max positive speed`() {
        val delta = DragAutoScroll.computeScrollDelta(
            itemTop = 560f, itemBottom = 600f,
            viewportTop = 100f, viewportBottom = 600f,
            edgeZonePx = edgeZone, maxSpeedPxPerFrame = maxSpeed,
        )
        assertEquals(maxSpeed, delta)
    }

    @Test
    fun `item bottom partially in bottom edge zone returns proportional positive speed`() {
        val delta = DragAutoScroll.computeScrollDelta(
            itemTop = 540f, itemBottom = 580f,
            viewportTop = 100f, viewportBottom = 600f,
            edgeZonePx = edgeZone, maxSpeedPxPerFrame = maxSpeed,
        )
        assertEquals(10f, delta)
    }

    @Test
    fun `item larger than viewport uses deeper penetration`() {
        val delta = DragAutoScroll.computeScrollDelta(
            itemTop = 90f, itemBottom = 605f,
            viewportTop = 100f, viewportBottom = 600f,
            edgeZonePx = edgeZone, maxSpeedPxPerFrame = maxSpeed,
        )
        assertTrue("expected negative (scroll up), got $delta", delta < 0f)
        assertEquals(-maxSpeed, delta)
    }

    @Test
    fun `item top above viewport is clamped to max negative speed`() {
        val delta = DragAutoScroll.computeScrollDelta(
            itemTop = 50f, itemBottom = 90f,
            viewportTop = 100f, viewportBottom = 600f,
            edgeZonePx = edgeZone, maxSpeedPxPerFrame = maxSpeed,
        )
        assertEquals(-maxSpeed, delta)
    }

    @Test
    fun `zero-height viewport returns zero`() {
        val delta = DragAutoScroll.computeScrollDelta(
            itemTop = 0f, itemBottom = 0f,
            viewportTop = 0f, viewportBottom = 0f,
            edgeZonePx = edgeZone, maxSpeedPxPerFrame = maxSpeed,
        )
        assertEquals(0f, delta)
    }
}
