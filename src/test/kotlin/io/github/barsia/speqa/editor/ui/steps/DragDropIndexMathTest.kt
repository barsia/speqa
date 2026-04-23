package io.github.barsia.speqa.editor.ui.steps

import org.junit.Assert.assertEquals
import org.junit.Test

class DragDropIndexMathTest {

    // Three cards, height 100 each, stacked from y=0.
    // Dragged card is index 1 (middle). Siblings are indices 0 and 2.
    private val siblings = listOf(
        SiblingBounds(originalIndex = 0, top = 0, height = 100),
        SiblingBounds(originalIndex = 2, top = 200, height = 100),
    )

    @Test
    fun `at rest returns original index`() {
        // Dragged card center at its at-rest position (y=150).
        val target = calculateTargetIndex(
            draggedCenterY = 150f,
            siblings = siblings,
            originalIndex = 1,
        )
        assertEquals(1, target)
    }

    @Test
    fun `small drift under threshold does not flip`() {
        // Upward drift of 20 px — boundary above is at 30 (top + 100 * 0.3).
        val target = calculateTargetIndex(
            draggedCenterY = 130f,
            siblings = siblings,
            originalIndex = 1,
        )
        assertEquals(1, target)
    }

    @Test
    fun `drift past threshold flips upward`() {
        // Upper sibling: top=0, height=100, boundary = 0 + 100 * (1 - 0.7) = 30.
        // Center must be strictly less than 30 to flip.
        val target = calculateTargetIndex(
            draggedCenterY = 25f,
            siblings = siblings,
            originalIndex = 1,
        )
        assertEquals(0, target)
    }

    @Test
    fun `drift past threshold flips downward`() {
        // Lower sibling: top=200, height=100, boundary = 200 + 100 * 0.7 = 270.
        // Center must be strictly greater than 270 to flip.
        val target = calculateTargetIndex(
            draggedCenterY = 275f,
            siblings = siblings,
            originalIndex = 1,
        )
        assertEquals(2, target)
    }

    @Test
    fun `drag to top`() {
        // Five-card list, dragging index 4 to the very top.
        val many = listOf(
            SiblingBounds(0, top = 0, height = 100),
            SiblingBounds(1, top = 100, height = 100),
            SiblingBounds(2, top = 200, height = 100),
            SiblingBounds(3, top = 300, height = 100),
        )
        val target = calculateTargetIndex(
            draggedCenterY = 5f,
            siblings = many,
            originalIndex = 4,
        )
        assertEquals(0, target)
    }

    @Test
    fun `drag to bottom`() {
        // Five-card list, dragging index 0 to the very bottom.
        val many = listOf(
            SiblingBounds(1, top = 0, height = 100),
            SiblingBounds(2, top = 100, height = 100),
            SiblingBounds(3, top = 200, height = 100),
            SiblingBounds(4, top = 300, height = 100),
        )
        val target = calculateTargetIndex(
            draggedCenterY = 395f,
            siblings = many,
            originalIndex = 0,
        )
        assertEquals(4, target)
    }

    @Test
    fun `empty siblings returns original`() {
        val target = calculateTargetIndex(
            draggedCenterY = 500f,
            siblings = emptyList(),
            originalIndex = 0,
        )
        assertEquals(0, target)
    }
}
