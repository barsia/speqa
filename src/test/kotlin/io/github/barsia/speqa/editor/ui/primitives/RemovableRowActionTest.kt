package io.github.barsia.speqa.editor.ui.primitives

import org.junit.Assert.assertEquals
import org.junit.Test

class RemovableRowActionTest {

    @Test
    fun `remove action is hidden at rest`() {
        assertEquals(
            false,
            shouldShowRemovableRowAction(
                hasAction = true,
                rowHovered = false,
                rowFocused = false,
                actionFocused = false,
            ),
        )
    }

    @Test
    fun `remove action is visible on row hover`() {
        assertEquals(
            true,
            shouldShowRemovableRowAction(
                hasAction = true,
                rowHovered = true,
                rowFocused = false,
                actionFocused = false,
            ),
        )
    }

    @Test
    fun `remove action is visible on keyboard focus`() {
        assertEquals(
            true,
            shouldShowRemovableRowAction(
                hasAction = true,
                rowHovered = false,
                rowFocused = true,
                actionFocused = false,
            ),
        )
    }

    @Test
    fun `remove action remains visible while action owns focus`() {
        assertEquals(
            true,
            shouldShowRemovableRowAction(
                hasAction = true,
                rowHovered = false,
                rowFocused = false,
                actionFocused = true,
            ),
        )
    }
}
