package io.github.barsia.speqa.editor.ui.primitives

import com.intellij.ide.actions.UndoRedoAction
import com.intellij.ui.ClientProperty
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.Insets
import javax.swing.event.DocumentEvent
import javax.swing.text.View

/**
 * Single-line plain-text input with an inline empty-text placeholder.
 * [onChange] fires on every document mutation with the current text.
 */
fun singleLineInput(
    placeholder: String = "",
    onChange: (String) -> Unit = {},
): JBTextField {
    val field = JBTextField()
    // Unify font across every preview text widget. JBTextField inherits the
    // platform's text-field LAF font which may differ slightly from the
    // proportional UI font (`JBFont.label()`) used in `multiLineInput`. Force
    // the same family/size everywhere so action/expected/runner/ticket inputs
    // all read as the same typographic unit.
    field.font = JBFont.label()
    // Opt out of the Swing undo manager intercept in UndoRedoAction.getUndoManager().
    // Without this flag, Cmd+Z while focus is in any JTextComponent returns the
    // per-field SwingUndoManagerWrapper instead of the project UndoManager, so
    // CommandProcessor-level document operations (e.g. DeleteStep) cannot be undone
    // from the preview side. Setting this property causes the platform to skip the
    // JTextComponent guard and return the project UndoManager, which holds the
    // full undo stack for all document mutations made via CommandProcessor.
    ClientProperty.put(field, UndoRedoAction.IGNORE_SWING_UNDO_MANAGER, true)
    // JBTextField already ships a DarculaTextBorder with appropriate inner
    // padding on HiDPI. Setting an extra `margin` here stacks on top of that
    // and doubles the vertical padding, which is what made the ticket-input
    // field and its placeholder look oversized compared to the chip rows
    // above it.
    if (placeholder.isNotEmpty()) {
        field.emptyText.text = placeholder
    }
    field.document.addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
            onChange(field.text)
        }
    })
    return field
}

/**
 * Multi-line plain-text input with an inline empty-text placeholder.
 * Caller is responsible for wrapping in [com.intellij.ui.components.JBScrollPane]
 * if scrolling is desired.
 */
fun multiLineInput(
    rows: Int = 3,
    placeholder: String = "",
    onChange: (String) -> Unit = {},
): JBTextArea {
    // JTextArea with lineWrap=true does not know what width to wrap at unless
    // it lives in a JScrollPane (where getScrollableTracksViewportWidth wires
    // the viewport width into the view). In a plain BorderLayout slot it
    // computes preferredSize.height assuming the text fits on one long line,
    // so the textarea is sized to one row tall while the actual paint wraps
    // to 2-3 visual lines and clips the bottom rows.
    //
    // Override getPreferredSize to ask the root View for its preferred Y span
    // at the current width, which is what BasicTextAreaUI's WrappedPlainView
    // would report once it knows the wrap width.
    val area = object : JBTextArea(rows, 0) {
        override fun getPreferredSize(): Dimension {
            val natural = super.getPreferredSize()
            val w = width
            if (w <= 0 || !lineWrap) return natural
            val textUi = getUI() ?: return natural
            val rootView = textUi.getRootView(this) ?: return natural
            val insets = insets
            val innerW = (w - insets.left - insets.right).coerceAtLeast(1).toFloat()
            rootView.setSize(innerW, Short.MAX_VALUE.toFloat())
            val viewHeight = rootView.getPreferredSpan(View.Y_AXIS).toInt()
            val rowHeight = getRowHeight() * getRows()
            val totalH = maxOf(viewHeight, rowHeight) + insets.top + insets.bottom
            return Dimension(natural.width, totalH)
        }
    }
    // JBTextArea defaults to the editor monospace font; override to the UI
    // font so action/expected/description/preconditions all render with the
    // same proportional font as the read-mode Markdown pane.
    area.font = JBFont.label()
    // Same Swing-undo intercept bypass as singleLineInput. See the comment
    // above for the rationale.
    ClientProperty.put(area, UndoRedoAction.IGNORE_SWING_UNDO_MANAGER, true)
    area.lineWrap = true
    area.wrapStyleWord = true
    // Rounded outline border + inner 4x6 padding so the text area reads as a
    // bordered editable field without needing a
    // JBScrollPane wrapper that would clip multi-line content.
    area.border = javax.swing.BorderFactory.createCompoundBorder(
        com.intellij.ui.RoundedLineBorder(com.intellij.ui.JBColor.border(), JBUI.scale(8), 1),
        javax.swing.BorderFactory.createEmptyBorder(JBUI.scale(4), JBUI.scale(6), JBUI.scale(4), JBUI.scale(6))
    )
    area.margin = Insets(0, 0, 0, 0)
    if (placeholder.isNotEmpty()) {
        area.emptyText.text = placeholder
    }
    area.document.addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
            onChange(area.text)
        }
    })
    // Auto-continue markdown bullet/ordered lists on Enter. The helper
    // returns null when the caret is not on a list line, letting the
    // default Enter behaviour through.
    area.addKeyListener(object : java.awt.event.KeyAdapter() {
        override fun keyPressed(e: java.awt.event.KeyEvent) {
            if (e.keyCode != java.awt.event.KeyEvent.VK_ENTER) return
            if (e.isShiftDown || e.isControlDown || e.isMetaDown || e.isAltDown) return
            val result = ListContinuation.onEnter(area.text, area.caretPosition) ?: return
            e.consume()
            area.text = result.text
            area.caretPosition = result.cursor.coerceIn(0, area.text.length)
        }
    })
    // When the textarea's width changes (e.g. first layout or container
    // resize), our overridden getPreferredSize will return a different
    // height; trigger revalidate so the parent re-lays-out and stops
    // clipping the wrapped tail.
    var lastWidth = 0
    area.addComponentListener(object : java.awt.event.ComponentAdapter() {
        override fun componentResized(e: java.awt.event.ComponentEvent) {
            if (area.width != lastWidth) {
                lastWidth = area.width
                area.revalidate()
            }
        }
    })
    return area
}
