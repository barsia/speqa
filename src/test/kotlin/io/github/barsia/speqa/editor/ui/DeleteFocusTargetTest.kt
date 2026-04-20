package io.github.barsia.speqa.editor.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DeleteFocusTargetTest {
    @Test
    fun `deleting middle item focuses the next item at same index`() {
        assertEquals(DeleteFocusTarget.Item(1), nextFocusTargetAfterDelete(deletedIndex = 1, sizeBefore = 3))
    }

    @Test
    fun `deleting first item of many focuses the new first`() {
        assertEquals(DeleteFocusTarget.Item(0), nextFocusTargetAfterDelete(deletedIndex = 0, sizeBefore = 3))
    }

    @Test
    fun `deleting last item focuses the new last`() {
        assertEquals(DeleteFocusTarget.Item(1), nextFocusTargetAfterDelete(deletedIndex = 2, sizeBefore = 3))
    }

    @Test
    fun `deleting only item focuses the add button`() {
        assertEquals(DeleteFocusTarget.AddButton, nextFocusTargetAfterDelete(deletedIndex = 0, sizeBefore = 1))
    }

    @Test
    fun `deleting only item when no add button available returns none`() {
        assertEquals(
            DeleteFocusTarget.None,
            nextFocusTargetAfterDelete(deletedIndex = 0, sizeBefore = 1, hasAddButton = false),
        )
    }

    @Test
    fun `deleting from empty list returns none`() {
        assertEquals(DeleteFocusTarget.None, nextFocusTargetAfterDelete(deletedIndex = 0, sizeBefore = 0))
    }

    @Test
    fun `deleting with out-of-bounds index returns none`() {
        assertEquals(DeleteFocusTarget.None, nextFocusTargetAfterDelete(deletedIndex = 5, sizeBefore = 3))
        assertEquals(DeleteFocusTarget.None, nextFocusTargetAfterDelete(deletedIndex = -1, sizeBefore = 3))
    }
}
