package io.github.barsia.speqa.editor.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.UndoRedoAction
import com.intellij.ui.ClientProperty
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBFont
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.ui.primitives.CommitFlash
import io.github.barsia.speqa.editor.ui.primitives.handCursor
import io.github.barsia.speqa.editor.ui.primitives.setSpeqaIcon
import io.github.barsia.speqa.editor.ui.primitives.setSpeqaTooltip
import io.github.barsia.speqa.editor.ui.primitives.speqaIconButton
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
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
import javax.swing.text.View

/**
 * Large-title row backed by a single [JBTextArea] (wrapping enabled) that
 * toggles between a borderless label-like appearance (read mode) and the
 * native editable appearance (edit mode). A `JBTextArea` (not `JBTextField`)
 * is required so long titles wrap to multiple lines instead of clipping on
 * one line. Using one component (not a JBLabel<->JBTextArea swap) avoids
 * vertical/horizontal layout shift between read and edit modes, and keeps
 * focus wiring simple so focus-loss commit fires reliably when the user
 * clicks away (including into the IntelliJ text editor on the left).
 */
class InlineEditableTitleRow(
    initialTitle: String,
    private val placeholder: String,
    private val onCommit: (String) -> Unit,
    /** When true, propagate edits live on every keystroke (like the runner field), not only on commit. */
    private val liveCommit: Boolean = false,
) : JPanel(BorderLayout()) {

    private var title: String = initialTitle
    private var editing: Boolean = false
    // Suppresses the live-commit document listener during programmatic text changes (enterEdit,
    // commit, cancel, setTitle). Without it, setText's intermediate empty state fires a live
    // onCommit("") that wipes the title.
    private var suppressDocEvents: Boolean = false
    private val editableBorder: Border
    private val field: JBTextArea = buildField()
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
        // BorderLayout vertically centers EAST components in their cell. When
        // the title wraps to multiple lines, the cell height grows and the
        // pencil drifts to the middle of the wrapped block. Pin the pencil to
        // the TOP of a BorderLayout wrapper - NORTH gives it exactly its
        // preferred size (22x22) at the top, and the CENTER slot stays empty,
        // so the icon paints its rectangular hover background only on its
        // own bounds. A BoxLayout-based wrapper would let the button's
        // unbounded maximumSize stretch vertically, producing an elongated
        // hover background instead.
        val pencilSlot = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(pencil, BorderLayout.NORTH)
        }
        add(pencilSlot, BorderLayout.EAST)
    }

    fun setTitle(newTitle: String, flash: Boolean = true) {
        if (editing) return
        if (title == newTitle) return
        title = newTitle
        if (!field.hasFocus()) {
            setFieldTextSilently(displayText(newTitle))
            field.caretPosition = 0
            if (flash) CommitFlash.flash(field)
        }
    }

    /** Set the field text without triggering the live-commit document listener. */
    private fun setFieldTextSilently(text: String) {
        suppressDocEvents = true
        try {
            field.text = text
        } finally {
            suppressDocEvents = false
        }
    }

    fun flashTarget(): JComponent = field

    // True only within the event cycle in which a commit just happened, so the pencil-button
    // action does not re-enter edit mode after `focusLost` already committed on the same click
    // (that race is what made the caret jump start/end on repeated icon clicks).
    private var justCommitted = false

    private fun toggleEdit() {
        when {
            editing -> commit()
            justCommitted -> Unit
            else -> enterEdit()
        }
    }

    /** Pencil in read mode, checkmark while editing (clicking it then commits the title). */
    private fun updatePencilAffordance() {
        pencil.setSpeqaIcon(if (editing) AllIcons.Actions.MenuSaveall else AllIcons.Actions.Edit)
        pencil.setSpeqaTooltip(SpeqaBundle.message(if (editing) "tooltip.save" else "tooltip.editTitle"))
    }

    private fun enterEdit(caretTarget: Int? = null) {
        editing = true
        updatePencilAffordance()
        applyEditMode()
        setFieldTextSilently(if (title.isBlank()) "" else title)
        SwingUtilities.invokeLater {
            field.requestFocusInWindow()
            // Place the caret where the user clicked (caretTarget); fall back to the end for
            // keyboard/pencil activation. No select-all, so typing extends rather than wipes.
            field.caretPosition = (caretTarget ?: field.text.length).coerceIn(0, field.text.length)
        }
    }

    private fun commit() {
        if (!editing) return
        editing = false
        justCommitted = true
        SwingUtilities.invokeLater { justCommitted = false }
        updatePencilAffordance()
        // A run/case title is required: a blank edit reverts to the previous title rather than
        // persisting an empty value.
        val next = field.text.trim().ifBlank { title }
        applyReadMode()
        setFieldTextSilently(displayText(next))
        field.caretPosition = 0
        if (next != title) {
            title = next
            onCommit(next)
        }
    }

    private fun cancel() {
        if (!editing) return
        editing = false
        updatePencilAffordance()
        applyReadMode()
        setFieldTextSilently(displayText(title))
        field.caretPosition = 0
    }

    private fun applyReadMode() {
        field.isEditable = false
        field.isOpaque = false
        field.background = null
        field.border = null
        field.margin = Insets(0, 0, 0, 0)
        field.handCursor()
        // Render the placeholder (shown when the title is blank) in a muted hint color so it reads
        // as a placeholder, not a real saved title.
        field.foreground =
            if (title.isBlank()) com.intellij.util.ui.UIUtil.getContextHelpForeground()
            else com.intellij.util.ui.UIUtil.getLabelForeground()
    }

    private fun applyEditMode() {
        field.isEditable = true
        field.isOpaque = true
        field.background = com.intellij.util.ui.UIUtil.getTextFieldBackground()
        field.border = editableBorder
        field.margin = Insets(0, 0, 0, 0)
        field.cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.TEXT_CURSOR)
        field.foreground = com.intellij.util.ui.UIUtil.getLabelForeground()
    }

    private fun buildField(): JBTextArea {
        val f = object : JBTextArea(displayText(title), 1, 0) {
            // `JTextArea` with `lineWrap = true` reports its preferred height
            // based on the wrapped layout at the CURRENT width. Outside a
            // scroll pane the parent layout has no width to ask for - we ask
            // the root View directly so the row grows to fit wrapped lines.
            override fun getPreferredSize(): Dimension {
                val natural = super.getPreferredSize()
                val w = width
                if (w <= 0 || !lineWrap) return natural
                val ui = getUI() ?: return natural
                val rootView = ui.getRootView(this) ?: return natural
                val ins = insets
                val innerW = (w - ins.left - ins.right).coerceAtLeast(1).toFloat()
                rootView.setSize(innerW, Short.MAX_VALUE.toFloat())
                val viewH = rootView.getPreferredSpan(View.Y_AXIS).toInt()
                return Dimension(natural.width, viewH + ins.top + ins.bottom)
            }

            // Allow the row to shrink horizontally without forcing the area to
            // its natural single-line text width.
            override fun getMinimumSize(): Dimension = Dimension(0, super.getPreferredSize().height)
        }
        f.lineWrap = true
        f.wrapStyleWord = true
        f.caretPosition = 0
        // Base on the proportional UI font, NOT JBTextArea's default editor
        // (monospace) font. Without this, the title rendered in monospace
        // bold while every other preview widget used the proportional UI
        // font, breaking visual consistency.
        val base = JBFont.label()
        val bold = base.deriveFont(Font.BOLD, base.size2D * 1.4f)
        f.font = bold.deriveFont(mapOf(java.awt.font.TextAttribute.UNDERLINE to -1))

        // Opt out of the Swing undo manager intercept so that Cmd+Z while focus
        // is in this title field delegates to the project UndoManager (which
        // holds the CommandProcessor undo stack) rather than the per-field
        // SwingUndoManagerWrapper that knows nothing about document-level ops.
        ClientProperty.put(f, UndoRedoAction.IGNORE_SWING_UNDO_MANAGER, true)

        // Live-commit on each keystroke (when enabled), matching the runner field. Guarded by
        // editing + focus so programmatic text changes (enterEdit/commit/cancel/setTitle) do not
        // fire: enterEdit sets the text before focus is requested, the others run after editing
        // is cleared or while unfocused.
        if (liveCommit) {
            f.document.addDocumentListener(object : javax.swing.event.DocumentListener {
                private fun fire() {
                    // Skip blank edits so live typing never persists an empty title (commit
                    // reverts blank to the previous value).
                    val text = f.text.trim()
                    if (!suppressDocEvents && editing && f.isFocusOwner && text.isNotBlank()) onCommit(text)
                }
                override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = fire()
                override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = fire()
                override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = fire()
            })
        }

        // Recompute height whenever width changes so wrap-line count updates.
        var lastWidth = 0
        f.addComponentListener(object : java.awt.event.ComponentAdapter() {
            override fun componentResized(e: java.awt.event.ComponentEvent) {
                if (f.width != lastWidth) {
                    lastWidth = f.width
                    f.revalidate()
                }
            }
        })

        f.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e) && !editing) {
                    // Enter edit with the caret at the clicked character, not forced to the end.
                    enterEdit(caretTarget = f.viewToModel2D(e.point))
                }
            }
        })
        f.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (!editing) return
                when (e.keyCode) {
                    // Enter commits the title rather than inserting a newline -
                    // the title is conceptually a single value, even when its
                    // rendering wraps to multiple visual lines.
                    KeyEvent.VK_ENTER -> { commit(); e.consume() }
                    KeyEvent.VK_ESCAPE -> { cancel(); e.consume() }
                }
            }
        })
        f.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) {
                // Commit on real focus loss. If focus went to the pencil button, the button's
                // toggle then sees justCommitted and does not re-enter edit mode.
                if (editing && !e.isTemporary) commit()
            }
        })
        return f
    }

    private fun displayText(value: String): String =
        if (value.isBlank()) placeholder else value
}
