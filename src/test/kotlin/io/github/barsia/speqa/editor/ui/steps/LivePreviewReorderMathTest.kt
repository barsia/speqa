package io.github.barsia.speqa.editor.ui.steps

import org.junit.Assert.assertEquals
import org.junit.Test

class LivePreviewReorderMathTest {

    private val slotSize = 120

    @Test
    fun `dragged card itself has zero offset`() {
        assertEquals(
            0,
            livePreviewTargetOffset(
                siblingIndex = 2,
                draggedIndex = 2,
                dropTargetIndex = 5,
                slotSize = slotSize,
            )
        )
    }

    @Test
    fun `no shift when drop target equals dragged index`() {
        repeat(5) { i ->
            assertEquals(
                "sibling $i should not shift at rest",
                0,
                livePreviewTargetOffset(
                    siblingIndex = i,
                    draggedIndex = 2,
                    dropTargetIndex = 2,
                    slotSize = slotSize,
                )
            )
        }
    }

    @Test
    fun `dragging down shifts in-between siblings up`() {
        // Drag from index 1 to 4. Siblings 2, 3, 4 must move up to fill the gap.
        assertEquals(-slotSize, livePreviewTargetOffset(2, 1, 4, slotSize))
        assertEquals(-slotSize, livePreviewTargetOffset(3, 1, 4, slotSize))
        assertEquals(-slotSize, livePreviewTargetOffset(4, 1, 4, slotSize))
    }

    @Test
    fun `dragging down leaves siblings outside the range unchanged`() {
        // Drag from index 1 to 4. Sibling 0 is above the vacated slot, sibling 5 is beyond the target.
        assertEquals(0, livePreviewTargetOffset(0, 1, 4, slotSize))
        assertEquals(0, livePreviewTargetOffset(5, 1, 4, slotSize))
    }

    @Test
    fun `dragging up shifts in-between siblings down`() {
        // Drag from index 4 to 1. Siblings 1, 2, 3 must move down to open the landing slot.
        assertEquals(slotSize, livePreviewTargetOffset(1, 4, 1, slotSize))
        assertEquals(slotSize, livePreviewTargetOffset(2, 4, 1, slotSize))
        assertEquals(slotSize, livePreviewTargetOffset(3, 4, 1, slotSize))
    }

    @Test
    fun `dragging up leaves siblings outside the range unchanged`() {
        // Drag from index 4 to 1. Siblings 0 and 5 are outside the range.
        assertEquals(0, livePreviewTargetOffset(0, 4, 1, slotSize))
        assertEquals(0, livePreviewTargetOffset(5, 4, 1, slotSize))
    }

    @Test
    fun `dragging down by one slot only shifts the single neighbour`() {
        // Drag from 2 to 3. Only sibling 3 shifts up.
        assertEquals(-slotSize, livePreviewTargetOffset(3, 2, 3, slotSize))
        assertEquals(0, livePreviewTargetOffset(1, 2, 3, slotSize))
        assertEquals(0, livePreviewTargetOffset(4, 2, 3, slotSize))
    }

    @Test
    fun `dragging up by one slot only shifts the single neighbour`() {
        // Drag from 3 to 2. Only sibling 2 shifts down.
        assertEquals(slotSize, livePreviewTargetOffset(2, 3, 2, slotSize))
        assertEquals(0, livePreviewTargetOffset(1, 3, 2, slotSize))
        assertEquals(0, livePreviewTargetOffset(4, 3, 2, slotSize))
    }

    @Test
    fun `slot size scales offsets`() {
        assertEquals(-50, livePreviewTargetOffset(1, 0, 1, 50))
        assertEquals(-200, livePreviewTargetOffset(1, 0, 1, 200))
    }

    @Test
    fun `dragging to end shifts every trailing sibling`() {
        // 5 cards. Drag index 0 to 4. Siblings 1..4 shift up.
        for (i in 1..4) {
            assertEquals(-slotSize, livePreviewTargetOffset(i, 0, 4, slotSize))
        }
    }

    @Test
    fun `dragging to start shifts every leading sibling`() {
        // 5 cards. Drag index 4 to 0. Siblings 0..3 shift down.
        for (i in 0..3) {
            assertEquals(slotSize, livePreviewTargetOffset(i, 4, 0, slotSize))
        }
    }
}
