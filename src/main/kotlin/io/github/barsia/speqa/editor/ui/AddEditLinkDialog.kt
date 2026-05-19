package io.github.barsia.speqa.editor.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.model.Link
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

internal class AddEditLinkDialog(
    project: Project?,
    dialogTitle: String,
    private val okButtonText: String,
    initialTitle: String = "",
    initialUrl: String = "",
) : DialogWrapper(project) {

    private val titleField = JBTextField(initialTitle, 40)
    private val urlField = JBTextField(initialUrl, 40)

    init {
        title = dialogTitle
        setOKButtonText(okButtonText)
        init()
        // IntelliJ's DialogWrapper auto-selects the entire text of the
        // preferred-focused text field on first focus. Override that with
        // a one-shot focus listener that moves the caret to the end so
        // typing extends the value instead of replacing it — same UX as
        // the inline editors elsewhere in the plugin.
        installCaretEndOnFirstFocus(titleField)
        installCaretEndOnFirstFocus(urlField)
    }

    private fun installCaretEndOnFirstFocus(field: JBTextField) {
        field.addFocusListener(object : java.awt.event.FocusAdapter() {
            override fun focusGained(e: java.awt.event.FocusEvent) {
                field.removeFocusListener(this)
                javax.swing.SwingUtilities.invokeLater {
                    field.caretPosition = field.text.length
                }
            }
        })
    }

    override fun createSouthPanel(): JComponent {
        val south = super.createSouthPanel()
        applyPointerCursor(south)
        return south
    }

    private fun applyPointerCursor(component: JComponent) {
        val hand = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
        for (child in component.components) {
            if (child is javax.swing.JButton) {
                child.cursor = hand
            }
            if (child is JComponent) {
                applyPointerCursor(child)
            }
        }
    }

    override fun getPreferredFocusedComponent(): JComponent = titleField

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = Insets(4, 4, 4, 4)
            anchor = GridBagConstraints.WEST
        }

        // Title first (optional)
        gbc.gridx = 0; gbc.gridy = 0
        panel.add(JLabel(SpeqaBundle.message("dialog.link.titleField")), gbc)
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        panel.add(titleField, gbc)

        // URL second (required, red asterisk)
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        val urlLabel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            add(JLabel(SpeqaBundle.message("dialog.link.urlField")))
            add(JLabel("*").apply { foreground = java.awt.Color.RED })
        }
        panel.add(urlLabel, gbc)
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        panel.add(urlField, gbc)

        return panel
    }

    override fun doValidateAll(): List<ValidationInfo> {
        val errors = mutableListOf<ValidationInfo>()
        val url = urlField.text.orEmpty().trim()
        if (url.isBlank()) {
            errors += ValidationInfo(SpeqaBundle.message("dialog.link.urlRequired"), urlField)
        } else if (!url.matches(Regex("^https?://.*"))) {
            errors += ValidationInfo(SpeqaBundle.message("dialog.link.urlInvalidProtocol"), urlField)
        }
        return errors
    }

    fun getLink(): Link {
        val resolvedTitle = titleField.text.orEmpty().ifBlank { urlField.text.orEmpty() }
        return Link(title = resolvedTitle.trim(), url = urlField.text.orEmpty().trim())
    }

    companion object {
        fun show(project: Project?, editLink: Link? = null): Link? {
            val isEdit = editLink != null
            val dialogTitle = if (isEdit) {
                SpeqaBundle.message("dialog.editLink.title")
            } else {
                SpeqaBundle.message("dialog.addLink.title")
            }
            val okText = if (isEdit) {
                SpeqaBundle.message("dialog.editLink.ok")
            } else {
                SpeqaBundle.message("dialog.addLink.ok")
            }
            val dialog = AddEditLinkDialog(
                project = project,
                dialogTitle = dialogTitle,
                okButtonText = okText,
                initialTitle = editLink?.title.orEmpty(),
                initialUrl = editLink?.url.orEmpty(),
            )
            return if (dialog.showAndGet()) dialog.getLink() else null
        }
    }
}
