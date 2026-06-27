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
    fun `mouse and programmatic focus do not paint the ring`() {
        // The regression we are guarding: focus restored onto a neighbour after a
        // delete arrives as a programmatic request (cause UNKNOWN) and must NOT light up,
        // and a plain mouse click on a chip must not leave a stuck ring either.
        assertFalse(isKeyboardFocusCause(FocusEvent.Cause.UNKNOWN))
        assertFalse(isKeyboardFocusCause(FocusEvent.Cause.MOUSE_EVENT))
        assertFalse(isKeyboardFocusCause(FocusEvent.Cause.ACTIVATION))
        assertFalse(isKeyboardFocusCause(null))
    }
}
