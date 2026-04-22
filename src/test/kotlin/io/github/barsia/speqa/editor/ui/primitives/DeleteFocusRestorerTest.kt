package io.github.barsia.speqa.editor.ui.primitives

import org.junit.Assert.assertEquals
import org.junit.Test

class DeleteFocusRestorerTest {
    @Test
    fun `only item deleted goes to add button`() {
        assertEquals(FocusTarget.AddButton, nextFocusTargetAfterDelete(deletedIndex = 0, sizeBefore = 1))
    }

    @Test
    fun `middle item deleted focuses same index`() {
        assertEquals(FocusTarget.Item(1), nextFocusTargetAfterDelete(deletedIndex = 1, sizeBefore = 3))
    }

    @Test
    fun `last item deleted focuses previous index`() {
        assertEquals(FocusTarget.Item(1), nextFocusTargetAfterDelete(deletedIndex = 2, sizeBefore = 3))
    }
}
