package io.github.barsia.speqa.editor.ui.primitives

import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.FocusEvent
import java.awt.geom.RoundRectangle2D

/**
 * Focus-ring helpers shared by the focusable preview widgets (tag/ticket chips,
 * link and attachment rows). Centralizes two contracts:
 *  - the ring (and the progressive-disclosure remove action) appears for keyboard
 *    interaction: direct traversal (Tab/arrows) OR programmatic focus restored
 *    after a keyboard action (Esc, Delete, reorder) when the last user interaction
 *    was the keyboard. Mouse-driven focus never shows the ring.
 *  - the ring is a thin, theme-colored outline rather than the heavy platform glow.
 */

/** Process-wide flag set by [SpeqaInputModalityTracker] via the IDE event queue. */
internal object SpeqaInputModality {
    @Volatile var lastInteractionWasKeyboard: Boolean = false
}

private val KEYBOARD_FOCUS_CAUSES = setOf(
    FocusEvent.Cause.TRAVERSAL,
    FocusEvent.Cause.TRAVERSAL_FORWARD,
    FocusEvent.Cause.TRAVERSAL_BACKWARD,
    FocusEvent.Cause.TRAVERSAL_UP,
    FocusEvent.Cause.TRAVERSAL_DOWN,
)

/**
 * Pure `:focus-visible` decision: true when the ring should show.
 * - A direct keyboard traversal cause always shows it.
 * - A non-mouse cause (UNKNOWN / programmatic) shows it when the last user
 *   interaction was the keyboard, allowing restored focus after keyboard
 *   actions (Esc, Delete, reorder menu close) to ring correctly.
 * - A MOUSE_EVENT cause never shows it, regardless of modality.
 */
internal fun keyboardFocusRingVisible(cause: FocusEvent.Cause?, lastInteractionWasKeyboard: Boolean): Boolean =
    cause in KEYBOARD_FOCUS_CAUSES || (cause != FocusEvent.Cause.MOUSE_EVENT && lastInteractionWasKeyboard)

/**
 * True when the focus ring should be visible for the given focus-gained [cause].
 * Implements the `:focus-visible` model: direct keyboard traversal always rings;
 * programmatic refocus rings when [SpeqaInputModality.lastInteractionWasKeyboard]
 * is true (set by [SpeqaInputModalityTracker]); mouse focus never rings.
 */
fun isKeyboardFocusCause(cause: FocusEvent.Cause?): Boolean =
    keyboardFocusRingVisible(cause, SpeqaInputModality.lastInteractionWasKeyboard)

/**
 * Paints a thin (1 px, theme-scaled) rounded focus outline in the IDE theme focus
 * color onto [g], which the caller owns and disposes. [width]/[height] are the
 * bounds to ring; [arc] is the corner diameter.
 */
fun paintCompactFocusRing(g: Graphics2D, width: Int, height: Int, arc: Float) {
    val lineWidth = JBUI.scale(1).toFloat()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.color = JBUI.CurrentTheme.Focus.focusColor()
    g.stroke = BasicStroke(lineWidth)
    val half = lineWidth / 2f
    g.draw(RoundRectangle2D.Float(half, half, width - lineWidth, height - lineWidth, arc, arc))
}
