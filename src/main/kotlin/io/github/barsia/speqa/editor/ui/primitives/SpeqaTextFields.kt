package io.github.barsia.speqa.editor.ui.primitives

import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.Insets
import javax.swing.event.DocumentEvent

/**
 * Single-line plain-text input with an inline empty-text placeholder.
 * [onChange] fires on every document mutation with the current text.
 */
fun singleLineInput(
    placeholder: String = "",
    onChange: (String) -> Unit = {},
): JBTextField {
    val field = JBTextField()
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
    val area = JBTextArea(rows, 0)
    // JBTextArea defaults to the editor monospace font; override to the UI
    // font so action/expected/description/preconditions all render with the
    // same proportional font as the read-mode Markdown pane.
    area.font = JBFont.label()
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
    return area
}
