package io.github.barsia.speqa.editor.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StepCardStateTest {

    @Test
    fun `accent bar is visible for focused step when not dragging`() {
        assertTrue(stepCardAccentBarVisible(isFocused = true, isDragging = false))
    }

    @Test
    fun `accent bar is hidden while dragging even if step stays focused`() {
        assertFalse(stepCardAccentBarVisible(isFocused = true, isDragging = true))
    }
}
