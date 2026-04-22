package io.github.barsia.speqa.editor.ui

import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBTextField
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.ui.primitives.CommitFlash
import io.github.barsia.speqa.editor.ui.primitives.handCursor
import io.github.barsia.speqa.editor.ui.primitives.speqaIconButton
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import java.awt.Insets
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.border.Border

/**
 * Large-title row backed by a single [JBTextField] that toggles between a
 * borderless label-like appearance (read mode) and the native editable
 * appearance (edit mode). Using one component avoids the vertical/horizontal
 * shift of swapping a JBLabel for a JBTextField, and keeps focus wiring simple
 * so focus-loss commit fires reliably when the user clicks away (including
 * into the IntelliJ text editor on the left).
 */
class InlineEditableTitleRow(
    initialTitle: String,
    private val placeholder: String,
    private val onCommit: (String) -> Unit,
) : JPanel(BorderLayout()) {

    private var title: String = initialTitle
    private var editing: Boolean = false
    private val editableBorder: Border
    private val field: JBTextField = buildField()
    private val pencil: JComponent = speqaIconButton(
        icon = AllIcons.Actions.Edit,
        tooltip = SpeqaBundle.message("tooltip.editTitle"),
        onAction = { toggleEdit() },
    )

    init {
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        editableBorder = field.border
        applyReadMode()
        add(field, BorderLayout.CENTER)
        add(pencil, BorderLayout.EAST)
    }

    fun setTitle(newTitle: String, flash: Boolean = true) {
        if (editing) return
        if (title == newTitle) return
        title = newTitle
        if (!field.hasFocus()) {
            field.text = displayText(newTitle)
            if (flash) CommitFlash.flash(field)
        }
    }

    fun flashTarget(): JComponent = field

    private fun toggleEdit() {
        if (editing) commit() else enterEdit()
    }

    private fun enterEdit() {
        editing = true
        applyEditMode()
        // Swap placeholder-as-text for empty editable content if we were
        // showing the placeholder label.
        if (field.text == displayText(title) && title.isBlank()) {
            field.text = ""
        } else {
            field.text = title
        }
        SwingUtilities.invokeLater {
            field.requestFocusInWindow()
            field.selectAll()
        }
    }

    private fun commit() {
        if (!editing) return
        editing = false
        val next = field.text
        applyReadMode()
        field.text = displayText(next)
        if (next != title) {
            title = next
            onCommit(next)
        }
    }

    private fun cancel() {
        if (!editing) return
        editing = false
        applyReadMode()
        field.text = displayText(title)
    }

    private fun applyReadMode() {
        field.isEditable = false
        field.isOpaque = false
        field.background = null
        field.border = null
        field.margin = Insets(0, 0, 0, 0)
        field.handCursor()
    }

    private fun applyEditMode() {
        field.isEditable = true
        field.isOpaque = true
        field.background = com.intellij.util.ui.UIUtil.getTextFieldBackground()
        field.border = editableBorder
        field.margin = Insets(0, 0, 0, 0)
        field.cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.TEXT_CURSOR)
    }

    private fun buildField(): JBTextField {
        val f = JBTextField(displayText(title))
        val base = f.font
        val bold = base.deriveFont(Font.BOLD, base.size2D * 1.4f)
        f.font = bold.deriveFont(mapOf(java.awt.font.TextAttribute.UNDERLINE to -1))
        f.toolTipText = placeholder
        f.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e) && !editing) enterEdit()
            }
        })
        f.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (!editing) return
                when (e.keyCode) {
                    KeyEvent.VK_ENTER -> { commit(); e.consume() }
                    KeyEvent.VK_ESCAPE -> { cancel(); e.consume() }
                }
            }
        })
        f.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) {
                if (editing && !e.isTemporary) commit()
            }
        })
        return f
    }

    private fun displayText(value: String): String =
        if (value.isBlank()) placeholder else value
}
