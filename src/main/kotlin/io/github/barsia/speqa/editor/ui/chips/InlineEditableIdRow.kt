package io.github.barsia.speqa.editor.ui.chips

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.UndoRedoAction
import com.intellij.ui.ClientProperty
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.ui.primitives.handCursor
import io.github.barsia.speqa.editor.ui.primitives.speqaIconButton
import io.github.barsia.speqa.registry.IdType
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Insets
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent

/**
 * Renders an editable identifier row:
 *  - when [id] is null, shows "$prefix$nextFreeId" in error colour; clicking the
 *    row invokes [onIdAssign] with the proposed [nextFreeId];
 *  - when assigned, shows "$prefix$id" + a pencil; clicking or Enter enters edit
 *    mode, keeping the same compact inline [JBTextField] for the number and
 *    swapping only the pencil/save action button.
 *    On commit (Enter / save click / focus-lost), parses the digits and calls
 *    [onIdAssign] if the value actually changed. Escape cancels.
 *
 * Duplicate-id styling: when [isDuplicate] is true the number foreground uses the
 * platform error focus colour.
 */
class InlineEditableIdRow(
    private val idType: IdType,
    private val onIdAssign: (Int) -> Unit,
) : JPanel(null) {

    private var id: Int? = null
    private var nextFreeId: Int = 1
    private var isDuplicate: Boolean = false
    private var editing: Boolean = false
    private val prefixLabel = JBLabel()
    private val idField = buildInlineIdField("")
    private var actionButton: JComponent? = null
    private val normalPrefixForeground = prefixLabel.foreground
    private val normalValueForeground = idField.foreground

    init {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
        ClientProperty.put(idField, UndoRedoAction.IGNORE_SWING_UNDO_MANAGER, true)
        wireInlineEditTriggers()
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
            editing = false
            val label = JBLabel("${prefix()}$nextFreeId").apply {
                foreground = errorColor()
                toolTipText = SpeqaBundle.message("tooltip.assignId")
            }
            val row = wrapClickable(label) { onIdAssign(nextFreeId) }
            add(row)
        } else {
            configureAssignedReadMode(currentId)
            addAssignedComponents()
            setActionButton(editing = false)
        }
        revalidate()
        repaint()
    }

    private fun addAssignedComponents() {
        add(prefixLabel)
        // Single text field for both read and edit modes. Using one
        // BasicTextFieldUI-backed component eliminates the pixel-level
        // X-rendering difference that used to make the digit jitter when
        // toggling between a JLabel (read) and a JBTextField (edit).
        add(idField)
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
        prefixLabel.text = prefix()
        setIdFieldText(currentId.toString())
        applyAssignedColors()
        applyEditMode()
        setActionButton(editing = true)
        revalidate()
        repaint()
        SwingUtilities.invokeLater {
            idField.requestFocusInWindow()
            // Caret to end (no select-all): consistent across all inline /
            // dialog editors in Speqa — typing extends the value instead of
            // wiping it. Cmd/Ctrl+A still selects all if the user wants to
            // replace the whole ID.
            idField.caretPosition = idField.text.length
        }
    }

    private fun buildInlineIdField(value: String): JBTextField =
        object : JBTextField(value, value.length.coerceAtLeast(1)) {
            override fun getInsets(): Insets = Insets(0, 0, 0, 0)

            override fun getPreferredSize(): Dimension {
                val natural = super.getPreferredSize()
                // Reserve enough trailing width for the end-of-text caret
                // so BasicTextFieldUI does not horizontally scroll the
                // text when the field becomes editable (the scroll-to-show-
                // caret pass would otherwise shove the digits ~1 px to the
                // left). 3 px covers caret stroke + insertion gutter.
                val textWidth = getFontMetrics(font).stringWidth(text.ifEmpty { "0" })
                return Dimension(textWidth + JBUI.scale(3), natural.height)
            }

            override fun getMinimumSize(): Dimension = preferredSize
        }.apply {
            isOpaque = false
            background = null
            border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
            margin = Insets(0, 0, 0, 0)
            // Plain Basic UI bypasses LAF-applied internal padding so the
            // rendered text starts at x = 0 (same as the JLabel in read
            // mode) instead of being offset by Darcula's field insets.
            ui = javax.swing.plaf.basic.BasicTextFieldUI()
        }

    private fun commit(field: JBTextField) {
        if (!editing) return
        val parsed = field.text.trim().toIntOrNull()
        editing = false
        if (parsed != null && parsed != id) {
            onIdAssign(parsed)
        } else {
            rebuild()
        }
    }

    private fun cancelEdit() {
        if (!editing) return
        editing = false
        rebuild()
    }

    private fun configureAssignedReadMode(currentId: Int) {
        prefixLabel.text = prefix()
        setIdFieldText(currentId.toString())
        applyAssignedColors()
        applyReadMode()
    }

    private fun setIdFieldText(value: String) {
        idField.text = value
        syncIdFieldColumns()
    }

    private fun syncIdFieldColumns() {
        idField.columns = idField.text.length.coerceAtLeast(1)
        idField.revalidate()
        revalidate()
    }

    private fun applyAssignedColors() {
        val currentId = id ?: return
        val duplicate = isDuplicate
        val fg = if (duplicate) errorColor() else null
        prefixLabel.foreground = fg ?: normalPrefixForeground
        idField.foreground = fg ?: normalValueForeground
        val tooltip = if (duplicate) {
            when (idType) {
                IdType.TEST_CASE -> SpeqaBundle.message("id.duplicate", currentId)
                IdType.TEST_RUN -> SpeqaBundle.message("id.duplicateTr", currentId)
            }
        } else {
            SpeqaBundle.message("tooltip.editId")
        }
        prefixLabel.toolTipText = tooltip
        idField.toolTipText = tooltip
        toolTipText = tooltip
    }

    private fun applyReadMode() {
        // Same field is used in both modes; toggling editability +
        // focusability is enough. Hand cursor signals clickable; the
        // caret is hidden because the field isn't focusable.
        idField.isVisible = true
        prefixLabel.isVisible = true
        idField.isEditable = false
        idField.isFocusable = false
        idField.caretPosition = 0
        handCursor()
        prefixLabel.handCursor()
        idField.handCursor()
    }

    private fun applyEditMode() {
        prefixLabel.isVisible = true
        idField.isVisible = true
        idField.isEditable = true
        idField.isFocusable = true
        cursor = Cursor.getDefaultCursor()
        prefixLabel.cursor = Cursor.getDefaultCursor()
        idField.cursor = Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR)
    }

    private fun setActionButton(editing: Boolean) {
        actionButton?.let { remove(it) }
        val next = speqaIconButton(
            icon = if (editing) AllIcons.Actions.MenuSaveall else AllIcons.Actions.Edit,
            tooltip = SpeqaBundle.message(if (editing) "tooltip.save" else "tooltip.editId"),
            onAction = {
                if (editing) commit(idField) else enterEditMode()
            },
        )
        actionButton = next
        add(next)
    }

    private fun wireInlineEditTriggers() {
        val clickTargets = listOf(this, prefixLabel, idField)
        clickTargets.forEach { target ->
            target.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (!editing) {
                        target.requestFocusInWindow()
                        enterEditMode()
                    }
                }
            })
        }
        idField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when {
                    editing && e.keyCode == KeyEvent.VK_ENTER -> {
                        commit(idField)
                        e.consume()
                    }
                    editing && e.keyCode == KeyEvent.VK_ESCAPE -> {
                        cancelEdit()
                        e.consume()
                    }
                    !editing && (e.keyCode == KeyEvent.VK_ENTER || e.keyCode == KeyEvent.VK_SPACE) -> {
                        enterEditMode()
                        e.consume()
                    }
                }
            }
        })
        idField.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) {
                if (editing && !e.isTemporary) commit(idField)
            }
        })
        idField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                syncIdFieldColumns()
            }
        })
    }

    override fun getPreferredSize(): Dimension {
        val currentId = id
        if (currentId == null && componentCount > 0) {
            return getComponent(0).preferredSize
        }

        val value = maxIdContentSize()
        val prefix = prefixLabel.preferredSize
        val action = actionButton?.preferredSize ?: Dimension(0, 0)
        return Dimension(
            prefix.width + value.width + action.width,
            maxOf(prefix.height, value.height, action.height),
        )
    }

    override fun getMinimumSize(): Dimension = preferredSize

    override fun doLayout() {
        if (id == null && componentCount > 0) {
            val child = getComponent(0)
            val pref = child.preferredSize
            child.setBounds(0, centeredY(pref.height), pref.width, pref.height)
            return
        }

        val prefix = prefixLabel.preferredSize
        val fieldSize = idField.preferredSize
        val action = actionButton
        val actionSize = action?.preferredSize ?: Dimension(0, 0)

        // Center each child vertically in the panel's actual height. This
        // matches how the sibling dates row centers its DateIconLabels.
        prefixLabel.setBounds(0, centeredY(prefix.height), prefix.width, prefix.height)

        val valueX = prefix.width
        idField.setBounds(valueX, centeredY(fieldSize.height), fieldSize.width, fieldSize.height)

        action ?: return
        action.setBounds(valueX + fieldSize.width, centeredY(actionSize.height), actionSize.width, actionSize.height)
    }

    private fun maxIdContentSize(): Dimension = idField.preferredSize

    private fun rowBaseline(items: List<Pair<JComponent, Dimension>>): Int =
        items.maxOf { (component, size) ->
            component.getBaseline(size.width, size.height).takeIf { it >= 0 } ?: (size.height / 2)
        }

    private fun baselineY(component: JComponent, size: Dimension, baseline: Int): Int {
        val childBaseline = component.getBaseline(size.width, size.height).takeIf { it >= 0 } ?: (size.height / 2)
        return (baseline - childBaseline).coerceAtLeast(0)
    }

    private fun centeredY(childHeight: Int): Int =
        ((height - childHeight) / 2).coerceAtLeast(0)
}
