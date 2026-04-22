package io.github.barsia.speqa.editor.ui.steps

import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.editor.ui.primitives.MarkdownReadOnlyPane
import io.github.barsia.speqa.editor.ui.primitives.handCursor
import io.github.barsia.speqa.editor.ui.primitives.multiLineInput
import io.github.barsia.speqa.model.DescriptionBlock
import io.github.barsia.speqa.model.PreconditionsBlock
import io.github.barsia.speqa.model.TestCaseBodyBlock
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * Swing port of `EditableBodyBlockSection` (description / preconditions).
 * Read mode shows a [MarkdownReadOnlyPane]; clicking the pencil (or the
 * read pane itself) enters edit mode with a [JBTextArea]. Edit → read
 * transition is triggered by Esc or the trailing "Done" button; every text
 * mutation is forwarded to [onCommit] so the model stays in sync.
 *
 * Structural round-trip through [mergeBodyBlocks] / [replaceBodyBlocks] is
 * the caller's responsibility; this widget only owns the text-mode toggle.
 */
class EditableBodyBlockSection(
    private val emptyLabel: String,
    private val onCommit: (String) -> Unit,
) : JPanel(BorderLayout()) {

    private val body = JPanel()
    private val readPane = MarkdownReadOnlyPane()
    private var editArea: JBTextArea? = null
    private var editing: Boolean = false
    private var text: String = ""

    init {
        isOpaque = false
        border = JBUI.Borders.emptyTop(2)
        body.layout = BoxLayout(body, BoxLayout.Y_AXIS)
        body.isOpaque = false
        add(body, BorderLayout.CENTER)
        rebuild()
    }

    fun setText(newText: String) {
        text = newText
        if (!editing) rebuild()
    }

    /**
     * Returns the component whose background should be flashed by
     * `CommitFlash.flash` when the body block changed externally.
     * Points at the active edit area in edit mode, or the read pane / body
     * container in read mode.
     */
    fun flashTarget(): JComponent = editArea ?: body

    private fun toggleEdit() {
        editing = !editing
        rebuild()
        if (editing) SwingUtilities.invokeLater { editArea?.requestFocusInWindow() }
    }

    private fun rebuild() {
        body.removeAll()
        if (editing) {
            val area = multiLineInput(
                rows = 1,
                placeholder = emptyLabel,
                onChange = { onCommit(it) },
            )
            area.text = text
            editArea = area
            area.addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    if (e.keyCode == KeyEvent.VK_ESCAPE) { toggleEdit(); e.consume() }
                }
            })
            // No scroll pane: let the text area grow with its content so
            // multi-line existing text is fully visible on edit-mode entry.
            area.alignmentX = Component.LEFT_ALIGNMENT
            body.add(area)
        } else {
            editArea = null
            val content = if (text.isBlank()) {
                MarkdownReadOnlyPane().apply { setMarkdown("_${emptyLabel}_") }
            } else {
                readPane.also { it.setMarkdown(text) }
            }
            content.alignmentX = Component.LEFT_ALIGNMENT
            attachClickToEdit(content)
            // Wrap in a bordered panel so read-mode visually matches the edit-mode text area.
            val bordered = JPanel(BorderLayout())
            bordered.isOpaque = false
            bordered.alignmentX = Component.LEFT_ALIGNMENT
            bordered.border = javax.swing.BorderFactory.createCompoundBorder(
                com.intellij.ui.RoundedLineBorder(com.intellij.ui.JBColor.border(), JBUI.scale(8), 1),
                javax.swing.BorderFactory.createEmptyBorder(JBUI.scale(4), JBUI.scale(6), JBUI.scale(4), JBUI.scale(6)),
            )
            bordered.add(content, BorderLayout.CENTER)
            body.add(bordered)
        }
        body.revalidate()
        body.repaint()
    }

    private fun attachClickToEdit(component: JComponent) {
        component.handCursor()
        component.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e) && !editing) {
                    toggleEdit()
                }
            }
        })
    }

    /** Convenience wrapper: re-render [current] blocks into this section's text, for the given block type. */
    fun bindFromBlocks(current: List<TestCaseBodyBlock>, type: Class<out TestCaseBodyBlock>) {
        setText(
            when (type) {
                DescriptionBlock::class.java -> mergeBodyBlocks(current, DescriptionBlock::class.java)
                PreconditionsBlock::class.java -> mergeBodyBlocks(current, PreconditionsBlock::class.java)
                else -> ""
            }
        )
    }
}
