package io.github.barsia.speqa.editor.ui.primitives

import java.awt.event.FocusEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusRingTest {

    @Test
    fun `keyboard traversal causes paint the ring`() {
        assertTrue(isKeyboardFocusCause(FocusEvent.Cause.TRAVERSAL))
        assertTrue(isKeyboardFocusCause(FocusEvent.Cause.TRAVERSAL_FORWARD))
        assertTrue(isKeyboardFocusCause(FocusEvent.Cause.TRAVERSAL_BACKWARD))
        assertTrue(isKeyboardFocusCause(FocusEvent.Cause.TRAVERSAL_UP))
        assertTrue(isKeyboardFocusCause(FocusEvent.Cause.TRAVERSAL_DOWN))
    }

    @Test
    fun `mouse and programmatic focus do not paint the ring when no keyboard interaction preceded`() {
        // SpeqaInputModality.lastInteractionWasKeyboard defaults to false (no AWT events
        // fired in a unit test), so UNKNOWN / null / MOUSE_EVENT / ACTIVATION all return
        // false — the same result as the old implementation for the default-off case.
        assertFalse(isKeyboardFocusCause(FocusEvent.Cause.UNKNOWN))
        assertFalse(isKeyboardFocusCause(FocusEvent.Cause.MOUSE_EVENT))
        assertFalse(isKeyboardFocusCause(FocusEvent.Cause.ACTIVATION))
        assertFalse(isKeyboardFocusCause(null))
    }
}
