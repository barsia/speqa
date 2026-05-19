package io.github.barsia.speqa.editor.ui.steps

import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.editor.ui.primitives.MarkdownEditablePane
import io.github.barsia.speqa.model.DescriptionBlock
import io.github.barsia.speqa.model.PreconditionsBlock
import io.github.barsia.speqa.model.TestCaseBodyBlock
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Description / Preconditions body block. A single always-editable text
 * area — typing inserts, backspace deletes, no mode toggle.
 *
 * Structural round-trip through [mergeBodyBlocks] / [replaceBodyBlocks]
 * is the caller's responsibility.
 */
class EditableBodyBlockSection(
    project: Project,
    emptyLabel: String,
    private val onCommit: (String) -> Unit,
) : JPanel(BorderLayout()) {

    private val editPane = MarkdownEditablePane(
        project = project,
        rows = 1,
        placeholder = emptyLabel,
        onChange = { text -> onCommit(text) },
    )

    init {
        isOpaque = false
        border = JBUI.Borders.emptyTop(2)
        editPane.alignmentX = Component.LEFT_ALIGNMENT
        add(editPane, BorderLayout.CENTER)
    }

    fun setText(newText: String, forceFocusedTextSync: Boolean = false) {
        val allowFocusedUpdate = forceFocusedTextSync || MarkdownEditablePane.undoInProgress.get()
        if (!allowFocusedUpdate && editPane.isFocusOwner) return
        editPane.setTextSuppressing(newText)
    }

    fun flashTarget(): JComponent = editPane

    fun bindFromBlocks(current: List<TestCaseBodyBlock>, type: Class<out TestCaseBodyBlock>) {
        setText(
            when (type) {
                DescriptionBlock::class.java -> mergeBodyBlocks(current, DescriptionBlock::class.java)
                PreconditionsBlock::class.java -> mergeBodyBlocks(current, PreconditionsBlock::class.java)
                else -> ""
            },
        )
    }
}
