package io.github.barsia.speqa.editor.ui.chips

import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.ui.primitives.handCursor
import io.github.barsia.speqa.editor.ui.primitives.speqaIconButton
import io.github.barsia.speqa.registry.IdType
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Renders an editable identifier row:
 *  - when [id] is null, shows "$prefix$nextFreeId" in error colour; clicking the
 *    row invokes [onIdAssign] with the proposed [nextFreeId];
 *  - when assigned, shows "$prefix$id" + a pencil; clicking or Enter enters edit
 *    mode, which swaps the number for a small [JBTextField] plus save button.
 *    On commit (Enter / save click / focus-lost), parses the digits and calls
 *    [onIdAssign] if the value actually changed. Escape cancels.
 *
 * Duplicate-id styling: when [isDuplicate] is true the number foreground uses the
 * platform error focus colour.
 */
class InlineEditableIdRow(
    private val idType: IdType,
    private val onIdAssign: (Int) -> Unit,
) : JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)) {

    private var id: Int? = null
    private var nextFreeId: Int = 1
    private var isDuplicate: Boolean = false
    private var editing: Boolean = false

    init {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
        rebuild()
    }

    fun update(id: Int?, nextFreeId: Int, isDuplicate: Boolean) {
        this.id = id
        this.nextFreeId = nextFreeId
        this.isDuplicate = isDuplicate
        if (!editing) rebuild()
    }

    private fun prefix(): String = when (idType) {
        IdType.TEST_CASE -> SpeqaBundle.message("label.idPrefix.tc")
        IdType.TEST_RUN -> SpeqaBundle.message("label.idPrefix.tr")
    }

    private fun errorColor(): JBColor =
        JBColor.namedColor("Component.errorFocusColor", JBColor.RED)

    private fun rebuild() {
        removeAll()
        val currentId = id
        if (currentId == null) {
            val label = JBLabel("${prefix()}$nextFreeId").apply {
                foreground = errorColor()
                font = font.deriveFont(Font.BOLD)
                toolTipText = SpeqaBundle.message("tooltip.assignId")
            }
            val row = wrapClickable(label) { onIdAssign(nextFreeId) }
            add(row)
        } else {
            val label = JBLabel("${prefix()}$currentId").apply {
                if (isDuplicate) {
                    foreground = errorColor()
                    toolTipText = when (idType) {
                        IdType.TEST_CASE -> SpeqaBundle.message("id.duplicate", currentId)
                        IdType.TEST_RUN -> SpeqaBundle.message("id.duplicateTr", currentId)
                    }
                } else {
                    toolTipText = SpeqaBundle.message("tooltip.editId")
                }
                font = font.deriveFont(Font.BOLD)
            }
            add(wrapClickable(label) { enterEditMode() })
            add(speqaIconButton(
                icon = AllIcons.Actions.Edit,
                tooltip = SpeqaBundle.message("tooltip.editId"),
                onAction = { enterEditMode() },
            ))
        }
        revalidate()
        repaint()
    }

    private fun wrapClickable(inner: JComponent, onClick: () -> Unit): JComponent {
        inner.isFocusable = true
        inner.handCursor()
        inner.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                inner.requestFocusInWindow()
                onClick()
            }
        })
        inner.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER || e.keyCode == KeyEvent.VK_SPACE) {
                    onClick()
                    e.consume()
                }
            }
        })
        return inner
    }

    private fun enterEditMode() {
        val currentId = id ?: return
        editing = true
        removeAll()
        add(JBLabel(prefix()).apply { font = font.deriveFont(Font.BOLD) })
        val field = JBTextField(currentId.toString(), 4)
        add(field)
        val save = speqaIconButton(
            icon = AllIcons.Actions.MenuSaveall,
            tooltip = SpeqaBundle.message("tooltip.save"),
            onAction = { commit(field) },
        )
        add(save)

        field.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ENTER -> { commit(field); e.consume() }
                    KeyEvent.VK_ESCAPE -> { cancelEdit(); e.consume() }
                }
            }
        })
        field.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) {
                if (editing) commit(field)
            }
        })

        revalidate()
        repaint()
        field.requestFocusInWindow()
        field.selectAll()
    }

    private fun commit(field: JBTextField) {
        val parsed = field.text.trim().toIntOrNull()
        editing = false
        if (parsed != null && parsed != id) {
            onIdAssign(parsed)
        } else {
            rebuild()
        }
    }

    private fun cancelEdit() {
        editing = false
        rebuild()
    }
}
