package io.github.barsia.speqa.editor.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusAccessibilityPolicyTest {

    @Test
    fun `read only title text is not keyboard focusable`() {
        assertFalse(FocusAccessibilityPolicy.titleTextCanFocus(isEditing = false))
    }

    @Test
    fun `editing title text is keyboard focusable`() {
        assertTrue(FocusAccessibilityPolicy.titleTextCanFocus(isEditing = true))
    }
}
