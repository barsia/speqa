package io.github.barsia.speqa.editor.ui.primitives

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Single-text-field input dialog, identical in look to `Messages.showInputDialog`
 * but with the caret positioned at the end of the initial value (no select-all).
 * Returns the trimmed user input on OK, or `null` on Cancel / empty.
 *
 * Used by Speqa for all editable string values so the user can extend or
 * tweak an existing value without accidentally wiping it.
 */
fun showCaretEndInputDialog(
    project: Project?,
    title: String,
    prompt: String,
    initial: String,
): String? {
    val field = JBTextField(initial, 30)
    // DialogWrapper auto-selects all text of the preferred-focused field on
    // first focus. Install a one-shot listener that moves the caret to the
    // end so the user can extend the value without wiping it.
    field.addFocusListener(object : java.awt.event.FocusAdapter() {
        override fun focusGained(e: java.awt.event.FocusEvent) {
            field.removeFocusListener(this)
            javax.swing.SwingUtilities.invokeLater {
                field.caretPosition = field.text.length
            }
        }
    })
    val dialog = object : DialogWrapper(project) {
        init {
            this.title = title
            init()
        }

        override fun getPreferredFocusedComponent(): JComponent = field

        override fun createCenterPanel(): JComponent {
            val panel = JPanel(BorderLayout(0, 6))
            panel.add(JBLabel(prompt), BorderLayout.NORTH)
            panel.add(field, BorderLayout.CENTER)
            return panel
        }
    }
    return if (dialog.showAndGet()) field.text.trim().ifEmpty { null } else null
}
