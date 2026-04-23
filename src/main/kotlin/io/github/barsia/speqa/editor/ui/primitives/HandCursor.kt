package io.github.barsia.speqa.editor.ui.primitives

import java.awt.Cursor
import javax.swing.JComponent

/**
 * Sets [Cursor.HAND_CURSOR] on the receiver and returns it for chaining.
 *
 * Swing keeps the cursor once [JComponent.setCursor] is called — no manual
 * mouse-enter/mouse-exit juggling is needed.
 */
fun <T : JComponent> T.handCursor(): T {
    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    return this
}
