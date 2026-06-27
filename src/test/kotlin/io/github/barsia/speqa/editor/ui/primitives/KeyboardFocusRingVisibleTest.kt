package io.github.barsia.speqa.editor.ui.primitives

import java.awt.event.FocusEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure [keyboardFocusRingVisible] decision function.
 * This is the `:focus-visible` logic: show the ring for keyboard interactions,
 * hide it for mouse interactions, and use modality context for programmatic focus.
 */
class KeyboardFocusRingVisibleTest {

    // --- Direct keyboard traversal always shows the ring ---

    @Test
    fun `TRAVERSAL cause shows ring regardless of modality`() {
        assertTrue(keyboardFocusRingVisible(FocusEvent.Cause.TRAVERSAL, lastInteractionWasKeyboard = false))
        assertTrue(keyboardFocusRingVisible(FocusEvent.Cause.TRAVERSAL, lastInteractionWasKeyboard = true))
    }

    // --- Mouse cause never shows the ring ---

    @Test
    fun `MOUSE_EVENT cause never shows ring regardless of modality`() {
        assertFalse(keyboardFocusRingVisible(FocusEvent.Cause.MOUSE_EVENT, lastInteractionWasKeyboard = false))
        assertFalse(keyboardFocusRingVisible(FocusEvent.Cause.MOUSE_EVENT, lastInteractionWasKeyboard = true))
    }

    // --- Programmatic / UNKNOWN cause follows modality ---

    @Test
    fun `UNKNOWN cause shows ring when last interaction was keyboard`() {
        assertTrue(keyboardFocusRingVisible(FocusEvent.Cause.UNKNOWN, lastInteractionWasKeyboard = true))
    }

    @Test
    fun `UNKNOWN cause does not show ring when last interaction was mouse`() {
        assertFalse(keyboardFocusRingVisible(FocusEvent.Cause.UNKNOWN, lastInteractionWasKeyboard = false))
    }

    // --- null cause follows modality (defensive) ---

    @Test
    fun `null cause shows ring when last interaction was keyboard`() {
        assertTrue(keyboardFocusRingVisible(cause = null, lastInteractionWasKeyboard = true))
    }

    @Test
    fun `null cause does not show ring when last interaction was mouse`() {
        assertFalse(keyboardFocusRingVisible(cause = null, lastInteractionWasKeyboard = false))
    }
}
