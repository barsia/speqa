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
 *  - the ring (and the progressive-disclosure remove action) appears only for
 *    genuine keyboard-traversal focus, never for mouse-click or programmatic
 *    focus such as the focus restored onto a neighbour after a delete;
 *  - the ring is a thin, theme-colored outline rather than the heavy platform glow.
 */

private val KEYBOARD_FOCUS_CAUSES = setOf(
    FocusEvent.Cause.TRAVERSAL,
    FocusEvent.Cause.TRAVERSAL_FORWARD,
    FocusEvent.Cause.TRAVERSAL_BACKWARD,
    FocusEvent.Cause.TRAVERSAL_UP,
    FocusEvent.Cause.TRAVERSAL_DOWN,
)

/**
 * True only when [cause] is a keyboard traversal (Tab / arrow navigation). Mouse
 * focus and programmatic `requestFocusInWindow()` (cause `UNKNOWN`) return false,
 * so the focus ring and remove affordance stay hidden for those.
 */
fun isKeyboardFocusCause(cause: FocusEvent.Cause?): Boolean = cause in KEYBOARD_FOCUS_CAUSES

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
