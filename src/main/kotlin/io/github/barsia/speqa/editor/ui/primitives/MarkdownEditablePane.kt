package io.github.barsia.speqa.editor.ui.primitives

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.Timer

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.FoldingModel
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorTextField
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import org.intellij.plugins.markdown.MarkdownIcons

private const val INLINE_CODE_CONTENT_PADDING = 2
private const val INLINE_CODE_EXTERNAL_GAP = 2

/**
 * Editable markdown view used for step `action` / `expected` / `comment`
 * and body blocks. Built on top of [EditorTextField] configured for the
 * IntelliJ Markdown file type so that soft-wrap, list continuation,
 * triple-click paragraph selection and native undo work out of the box.
 *
 * Inline Markdown formatting gets a WYSIWYG treatment: delimiters are
 * folded away (neverExpands) and the content range receives the matching
 * visual attributes, so the user edits styled text without seeing raw
 * markers for bold, italic, strike, and inline code.
 */
class MarkdownEditablePane(
    private val project: Project,
    @Suppress("UNUSED_PARAMETER") rows: Int = 1,
    placeholder: String,
    private val onChange: (String) -> Unit,
) : JPanel(BorderLayout()) {

    private var currentText: String = ""
    private var suppress: Boolean = false

    private val ourFolds = mutableListOf<FoldRegion>()
    private val ourHighlighters = mutableListOf<RangeHighlighter>()
    private val ourInlays = mutableListOf<Inlay<*>>()
    private var activeWysiwygEditor: EditorEx? = null
    private var wysiwygOwner: EditorEx? = null
    private var scheduledWysiwygEditor: EditorEx? = null
    private var formattingPopup: JBPopup? = null
    private var formattingSelection: FormattingSelection? = null
    private var suppressFormattingToolbarUpdate = false

    private val field: EditorTextField = object : EditorTextField(
        "",
        project,
        markdownFileType(),
    ) {
        override fun createEditor(): EditorEx {
            val editor = super.createEditor()
            editor.settings.isUseSoftWraps = true
            // Keep wrapping but hide the soft-wrap indicator arrows the editor draws
            // at each wrap point; they are visual noise in these small preview fields.
            // Scoped to this embedded editor only - the main text editor is unaffected.
            editor.settings.isPaintSoftWraps = false
            editor.settings.isLineNumbersShown = false
            editor.settings.isLineMarkerAreaShown = false
            editor.settings.isFoldingOutlineShown = false
            editor.settings.isRightMarginShown = false
            editor.settings.isCaretRowShown = false
            editor.settings.additionalLinesCount = 0
            editor.setBorder(BorderFactory.createEmptyBorder())
            editor.setPlaceholder(placeholder)
            editor.setShowPlaceholderWhenFocused(false)
            installWysiwyg(editor)
            installUndoDelegation(editor)
            installFormattingToolbar(editor)
            installEnterHandling(editor)
            installTabFocusTraversal(editor)
            installHiddenCodeBlockEditGuard(editor)
            installCodeBlockCopyButton(editor)
            normalizeEmbeddedEditorScroll(editor)
            return editor
        }
    }.apply {
        setOneLineMode(false)
        background = null
        border = BorderFactory.createEmptyBorder()
    }

    init {
        isOpaque = false
        border = BorderFactory.createCompoundBorder(
            RoundedLineBorder(JBColor.border(), JBUI.scale(8), 1),
            BorderFactory.createEmptyBorder(JBUI.scale(4), JBUI.scale(6), JBUI.scale(4), JBUI.scale(6)),
        )
        field.alignmentX = Component.LEFT_ALIGNMENT
        add(field, BorderLayout.CENTER)

        field.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                currentText = field.text
                if (!suppress) onChange(currentText)
            }
        })
    }

    var text: String
        get() = currentText
        set(value) {
            setTextSuppressing(value)
        }

    fun setTextSuppressing(value: String) {
        if (currentText == value) return
        suppress = true
        try {
            val previousText = currentText
            currentText = value
            ApplicationManager.getApplication().invokeAndWait {
                ApplicationManager.getApplication().runWriteAction {
                    val editor = field.editor as? EditorEx
                    val savedCaret = editor?.caretModel?.offset ?: 0
                    if (editor != null && !editor.isDisposed) {
                        // Stale fold regions and inlays crash VisualLineFragmentsIterator /
                        // EditorSizeManager during setText's synchronous document change events.
                        clearWysiwygNonFoldingArtifacts()
                        editor.foldingModel.runBatchFoldingOperation {
                            for (region in editor.foldingModel.allFoldRegions.toList()) {
                                editor.foldingModel.removeFoldRegion(region)
                            }
                            ourFolds.clear()
                        }
                    }
                    field.text = value
                    val target = caretOffsetAfterTextSync(previousText, value, savedCaret)
                    editor?.let {
                        it.caretModel.moveToOffset(target)
                        normalizeEmbeddedEditorScroll(it)
                    }
                }
            }
        } finally {
            suppress = false
        }
    }

    override fun isFocusOwner(): Boolean = field.editor?.contentComponent?.hasFocus() == true

    override fun requestFocusInWindow(): Boolean = field.requestFocusInWindow()

    private fun editorDisposable(editor: EditorEx): Disposable {
        val d = Disposer.newDisposable()
        EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
            override fun editorReleased(event: EditorFactoryEvent) {
                if (event.editor === editor) Disposer.dispose(d)
            }
        }, d)
        return d
    }

    private fun installFormattingToolbar(editor: EditorEx) {
        val ed = editorDisposable(editor)
        val debounce = Timer(200) { updateFormattingToolbar(editor) }.apply { isRepeats = false }
        Disposer.register(ed, Disposable { debounce.stop() })
        editor.selectionModel.addSelectionListener(object : SelectionListener {
            override fun selectionChanged(e: SelectionEvent) {
                debounce.restart()
            }
        }, ed)
        registerFormattingShortcut(editor, MarkdownFormatAction.BOLD, "meta B", "control B")
        registerFormattingShortcut(editor, MarkdownFormatAction.ITALIC, "meta I", "control I")
        registerFormattingShortcut(editor, MarkdownFormatAction.STRIKE, "meta shift X", "control shift X")
    }

    private fun updateFormattingToolbar(editor: EditorEx) {
        if (suppressFormattingToolbarUpdate) return
        if (editor.isDisposed) return
        if (!editor.selectionModel.hasSelection() || !editor.contentComponent.hasFocus()) {
            hideFormattingToolbar()
            return
        }
        hideFormattingToolbar()
        formattingSelection = FormattingSelection(
            text = editor.document.charsSequence.toString(),
            selectionStart = editor.selectionModel.selectionStart,
            selectionEnd = editor.selectionModel.selectionEnd,
        )
        val toolbar = buildFormattingToolbar(editor)
        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(toolbar, null)
            .setRequestFocus(false)
            .setCancelOnClickOutside(true)
            .setCancelOnWindowDeactivation(true)
            .setResizable(false)
            .setMovable(false)
            .createPopup()
        formattingPopup = popup
        popup.show(RelativePoint(editor.contentComponent, toolbarLocation(editor, toolbar, formattingSelection)))
    }

    private fun hideFormattingToolbar() {
        formattingPopup?.cancel()
        formattingPopup = null
    }

    private fun buildFormattingToolbar(editor: EditorEx): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = true
            background = JBColor.PanelBackground
            border = BorderFactory.createLineBorder(JBColor.border())
        }
        for (action in MarkdownFormatAction.entries) {
            panel.add(formatButton(editor, action))
        }
        return panel
    }

    private fun formatButton(editor: EditorEx, action: MarkdownFormatAction): JButton =
        JButton(action.toolbarIcon()).apply {
            toolTipText = SpeqaBundle.message(action.tooltipKey)
            isFocusable = false
            handCursor()
            horizontalAlignment = SwingConstants.CENTER
            border = BorderFactory.createEmptyBorder()
            margin = JBUI.emptyInsets()
            preferredSize = action.toolbarButtonSize()
            minimumSize = preferredSize
            maximumSize = preferredSize
            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    applyMarkdownFormatting(editor, action, formattingSelection)
                    formattingSelection = null
                    hideFormattingToolbar()
                    e.consume()
                }
            })
        }

    private fun registerFormattingShortcut(editor: EditorEx, action: MarkdownFormatAction, vararg shortcuts: String) {
        object : AnAction() {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT

            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = editor.selectionModel.hasSelection()
            }

            override fun actionPerformed(e: AnActionEvent) {
                applyMarkdownFormatting(editor, action, null)
            }
        }.registerCustomShortcutSet(CustomShortcutSet.fromString(*shortcuts), editor.contentComponent)
    }

    private fun applyMarkdownFormatting(
        editor: EditorEx,
        action: MarkdownFormatAction,
        selectionSnapshot: FormattingSelection?,
    ) {
        val selectionModel = editor.selectionModel
        val source = selectionSnapshot ?: run {
            if (!selectionModel.hasSelection()) return
            FormattingSelection(
                text = editor.document.charsSequence.toString(),
                selectionStart = selectionModel.selectionStart,
                selectionEnd = selectionModel.selectionEnd,
            )
        }
        val result = MarkdownSelectionFormatter.apply(
            text = source.text,
            selectionStart = source.selectionStart,
            selectionEnd = source.selectionEnd,
            action = action,
        )
        runWriteAction {
            editor.document.replaceString(0, editor.document.textLength, result.text)
        }
        suppressFormattingToolbarUpdate = true
        selectionModel.removeSelection()
        editor.caretModel.moveToOffset(result.selectionEnd)
        hideFormattingToolbar()
        ApplicationManager.getApplication().invokeLater {
            formattingSelection = null
            suppressFormattingToolbarUpdate = false
        }
    }

    private data class FormattingSelection(
        val text: String,
        val selectionStart: Int,
        val selectionEnd: Int,
    )

    private fun toolbarLocation(editor: EditorEx, toolbar: JPanel, selection: FormattingSelection?): Point {
        val bounds = selectionBounds(editor, selection)
        val size = toolbar.preferredSize
        val gap = JBUI.scale(6)
        val visible = editor.scrollingModel.visibleArea
        val aboveY = bounds.y - size.height - gap
        val belowY = bounds.y + bounds.height + gap
        val y = when {
            aboveY >= visible.y -> aboveY
            belowY + size.height <= visible.y + visible.height -> belowY
            aboveY < visible.y && belowY + size.height > visible.y + visible.height -> belowY
            else -> aboveY
        }

        val centeredX = bounds.x + (bounds.width - size.width) / 2
        val minX = visible.x
        val maxX = visible.x + visible.width - size.width
        val x = if (maxX >= minX) centeredX.coerceIn(minX, maxX) else centeredX
        return Point(x, y)
    }

    private fun selectionBounds(editor: EditorEx, selection: FormattingSelection?): Rectangle {
        val start = selection?.selectionStart ?: editor.selectionModel.selectionStart
        val end = selection?.selectionEnd ?: editor.selectionModel.selectionEnd
        val startPoint = editor.offsetToXY(start)
        val endPoint = editor.offsetToXY(end)
        val lineHeight = editor.lineHeight
        val left = minOf(startPoint.x, endPoint.x)
        val right = maxOf(startPoint.x, endPoint.x)
        val top = minOf(startPoint.y, endPoint.y)
        val bottom = maxOf(startPoint.y, endPoint.y) + lineHeight
        val width = (right - left).coerceAtLeast(JBUI.scale(24))
        return Rectangle(left, top, width, bottom - top)
    }

    /**
     * Route the field's Cmd+Z / Cmd+Shift+Z to the project's UndoManager
     * operating on the parent FileEditor. Without this, undo would target
     * the field's transient light-virtual-file document, which is not what
     * the user expects in a visual editor backed by a real .tc.md / .tr.md
     * document.
     */
    private fun installUndoDelegation(editor: EditorEx) {
        com.intellij.openapi.command.undo.UndoUtil.disableUndoFor(editor.document)

        val undoManager = com.intellij.openapi.command.undo.UndoManager.getInstance(project)
        val component = editor.contentComponent

        fun parentFileEditor(): com.intellij.openapi.fileEditor.FileEditor? {
            val dataContext = com.intellij.ide.DataManager.getInstance().getDataContext(component)
            return com.intellij.openapi.actionSystem.PlatformCoreDataKeys.FILE_EDITOR.getData(dataContext)
        }

        val undoAction = object : com.intellij.openapi.actionSystem.AnAction() {
            override fun getActionUpdateThread() =
                com.intellij.openapi.actionSystem.ActionUpdateThread.EDT

            override fun update(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                val fileEditor = parentFileEditor()
                e.presentation.isEnabled = fileEditor != null && undoManager.isUndoAvailable(fileEditor)
            }

            override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                val fileEditor = parentFileEditor() ?: return
                undoInProgress.set(true)
                try {
                    undoManager.undo(fileEditor)
                } finally {
                    undoInProgress.set(false)
                }
            }
        }
        val redoAction = object : com.intellij.openapi.actionSystem.AnAction() {
            override fun getActionUpdateThread() =
                com.intellij.openapi.actionSystem.ActionUpdateThread.EDT

            override fun update(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                val fileEditor = parentFileEditor()
                e.presentation.isEnabled = fileEditor != null && undoManager.isRedoAvailable(fileEditor)
            }

            override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                val fileEditor = parentFileEditor() ?: return
                undoInProgress.set(true)
                try {
                    undoManager.redo(fileEditor)
                } finally {
                    undoInProgress.set(false)
                }
            }
        }

        val actionManager = com.intellij.openapi.actionSystem.ActionManager.getInstance()
        val undoShortcuts = actionManager
            .getAction(com.intellij.openapi.actionSystem.IdeActions.ACTION_UNDO)
            ?.shortcutSet
            ?: com.intellij.openapi.actionSystem.CustomShortcutSet.fromString("meta Z", "control Z")
        val redoShortcuts = actionManager
            .getAction(com.intellij.openapi.actionSystem.IdeActions.ACTION_REDO)
            ?.shortcutSet
            ?: com.intellij.openapi.actionSystem.CustomShortcutSet.fromString("meta shift Z", "control shift Z")
        undoAction.registerCustomShortcutSet(undoShortcuts, component)
        redoAction.registerCustomShortcutSet(redoShortcuts, component)
    }

    /**
     * Single Enter handler: inside an indented code block insert a plain newline,
     * otherwise continue the Markdown list. Both write through a command so the change
     * applies and is undoable. One action (not two competing ENTER bindings) so the
     * editor never receives an ambiguous shortcut that silently swallows Enter.
     */
    private fun installEnterHandling(editor: EditorEx) {
        val action = object : AnAction() {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT

            override fun update(e: AnActionEvent) {
                e.presentation.isEnabledAndVisible =
                    isInsideIndentedCodeBlock(editor) ||
                    ListContinuation.onEnter(editor.document.charsSequence.toString(), editor.caretModel.offset) != null
            }

            override fun actionPerformed(e: AnActionEvent) {
                if (isInsideIndentedCodeBlock(editor)) {
                    val caret = editor.caretModel.offset
                    WriteCommandAction.runWriteCommandAction(project) {
                        editor.document.insertString(caret, "\n")
                    }
                    editor.caretModel.moveToOffset(caret + 1)
                    return
                }
                MarkdownEnterHandler.apply(editor, project)
            }
        }
        action.registerCustomShortcutSet(CustomShortcutSet.fromString("ENTER"), editor.contentComponent)
    }

    private fun installTabFocusTraversal(editor: EditorEx) {
        val forward = object : AnAction() {
            override fun actionPerformed(e: AnActionEvent) {
                editor.contentComponent.transferFocus()
            }
        }
        forward.registerCustomShortcutSet(CustomShortcutSet.fromString("TAB"), editor.contentComponent)
        val backward = object : AnAction() {
            override fun actionPerformed(e: AnActionEvent) {
                editor.contentComponent.transferFocusBackward()
            }
        }
        backward.registerCustomShortcutSet(CustomShortcutSet.fromString("shift TAB"), editor.contentComponent)
    }

    private fun installCodeBlockCopyButton(editor: EditorEx) {
        val ed = editorDisposable(editor)
        var currentBlock: MarkdownWysiwygRange? = null
        var hideTimer: Timer? = null
        var feedbackTimer: Timer? = null
        var isFeedbackShowing = false

        val doneLabel = javax.swing.JLabel(AllIcons.General.GreenCheckmark).apply {
            isVisible = false
            isFocusable = false
            val s = JBUI.scale(22)
            preferredSize = java.awt.Dimension(s, s)
        }

        lateinit var button: JComponent
        button = speqaIconButton(
            icon = AllIcons.Actions.Copy,
            tooltip = SpeqaBundle.message("editor.codeBlock.copy.tooltip"),
            muted = true,
        ) {
            currentBlock?.let { block ->
                val content = extractCodeBlockContent(editor.document.charsSequence, block)
                val sel = StringSelection(content)
                java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)

                feedbackTimer?.stop()
                isFeedbackShowing = true
                button.isVisible = false
                doneLabel.setBounds(button.bounds)
                doneLabel.isVisible = true
                feedbackTimer = Timer(500) {
                    isFeedbackShowing = false
                    doneLabel.isVisible = false
                    if (currentBlock != null) button.isVisible = true
                }.apply { isRepeats = false; start() }
            }
        }.apply {
            isVisible = false
            // Must not be focusable: when an ActionButton appears/disappears inside
            // editor.contentComponent, Swing triggers TRAVERSAL_FORWARD and steals
            // focus from the active text field.
            isFocusable = false
        }

        editor.contentComponent.add(button)
        editor.contentComponent.add(doneLabel)
        Disposer.register(ed) {
            hideTimer?.stop()
            feedbackTimer?.stop()
            editor.contentComponent.remove(button)
            editor.contentComponent.remove(doneLabel)
        }

        fun scheduleHide() {
            if (isFeedbackShowing) return
            hideTimer?.stop()
            hideTimer = Timer(300) { button.isVisible = false; currentBlock = null }
                .apply { isRepeats = false; start() }
        }

        fun cancelHide() { hideTimer?.stop(); hideTimer = null }

        fun showForBlock(block: MarkdownWysiwygRange) {
            if (editor.isDisposed) return
            cancelHide()
            currentBlock = block
            val startLine = editor.offsetToVisualPosition(block.contentStart).line
            val y = editor.visualLineToY(startLine) + JBUI.scale(4)
            val bw = button.preferredSize.width
            val bh = button.preferredSize.height
            val x = editor.contentComponent.width - bw - JBUI.scale(4)
            button.setBounds(x, y, bw, bh)
            if (!doneLabel.isVisible) button.isVisible = true
        }

        editor.addEditorMouseMotionListener(object : EditorMouseMotionListener {
            override fun mouseMoved(e: EditorMouseEvent) {
                val block = codeBlockUnderPoint(editor, e.mouseEvent.point)
                if (block != null) showForBlock(block) else scheduleHide()
            }
        }, ed)
        editor.contentComponent.addMouseListener(object : MouseAdapter() {
            override fun mouseExited(e: MouseEvent) = scheduleHide()
        })
        button.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) = cancelHide()
            override fun mouseExited(e: MouseEvent) = scheduleHide()
        })
        editor.scrollingModel.addVisibleAreaListener(VisibleAreaListener {
            currentBlock?.let { showForBlock(it) }
        }, ed)
    }

    private fun codeBlockUnderPoint(editor: EditorEx, point: Point): MarkdownWysiwygRange? {
        if (editor.isDisposed) return null
        val offset = editor.logicalPositionToOffset(editor.xyToLogicalPosition(point))
        val text = editor.document.charsSequence
        return MarkdownWysiwygRanges.fencedCodeBlocks(text).firstOrNull { range ->
            offset in range.openStart until range.closeFoldEnd
        }
    }

    private fun extractCodeBlockContent(text: CharSequence, range: MarkdownWysiwygRange): String {
        if (range.contentIndentFolds.isEmpty()) {
            return text.substring(range.contentStart, range.contentEnd)
        }
        // Walk content range, skipping fold intervals (sorted, non-overlapping)
        val sb = StringBuilder()
        var pos = range.contentStart
        for (fold in range.contentIndentFolds) {
            if (pos < fold.start) sb.append(text, pos, fold.start)
            pos = fold.end
        }
        if (pos < range.contentEnd) sb.append(text, pos, range.contentEnd)
        return sb.toString()
    }

    private fun isInsideIndentedCodeBlock(editor: EditorEx): Boolean {
        val caret = editor.caretModel.offset
        val text = editor.document.charsSequence
        return MarkdownWysiwygRanges.fencedCodeBlocks(text).any { block ->
            block.contentIndentFolds.isNotEmpty() &&
                caret >= block.contentStart && caret <= block.contentEnd
        }
    }

    private fun installHiddenCodeBlockEditGuard(editor: EditorEx) {
        hiddenCodeBlockEditGuard(editor, backspace = true)
            .registerCustomShortcutSet(CustomShortcutSet.fromString("BACK_SPACE"), editor.contentComponent)
        hiddenCodeBlockEditGuard(editor, backspace = false)
            .registerCustomShortcutSet(CustomShortcutSet.fromString("DELETE"), editor.contentComponent)
    }

    private fun hiddenCodeBlockEditGuard(editor: EditorEx, backspace: Boolean): AnAction =
        object : AnAction() {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT

            override fun update(e: AnActionEvent) {
                e.presentation.isEnabledAndVisible = shouldConsumeHiddenCodeBlockEdit(editor, backspace)
            }

            override fun actionPerformed(e: AnActionEvent) {
                if (backspace) smartDeleteIndentEmptyLine(editor)
                // Delete (forward) at openStart: consume with no-op — can't delete fence
            }
        }

    private fun smartDeleteIndentEmptyLine(editor: EditorEx) {
        if (editor.isDisposed) return
        val caret = editor.caretModel.offset
        val text = editor.document.charsSequence
        for (block in MarkdownWysiwygRanges.fencedCodeBlocks(text)) {
            if (caret == block.contentStart) return // caret at fence boundary — structural no-op
            for (fold in block.contentIndentFolds) {
                if (caret - 1 !in fold.start until fold.end) continue
                // Only smart-delete when the line is visually empty (fold is immediately followed by \n)
                if (caret >= text.length || text[caret] != '\n') return
                val nlBefore = fold.start - 1
                if (nlBefore < 0 || text[nlBefore] != '\n') return
                WriteCommandAction.runWriteCommandAction(project) {
                    editor.document.deleteString(nlBefore, fold.end)
                }
                editor.caretModel.moveToOffset(nlBefore)
                return
            }
        }
    }

    private fun shouldConsumeHiddenCodeBlockEdit(editor: EditorEx, backspace: Boolean): Boolean {
        if (editor.selectionModel.hasSelection()) return false
        return MarkdownWysiwygRanges.shouldConsumeHiddenCodeBlockEdit(
            text = editor.document.charsSequence,
            caretOffset = editor.caretModel.offset,
            backspace = backspace,
        )
    }

    @Suppress("DEPRECATION")
    private fun installWysiwyg(editor: EditorEx) {
        activeWysiwygEditor = editor
        refreshMarkdownWysiwyg(editor)
        editor.document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                scheduleMarkdownWysiwygRefresh(editor)
            }
        }, editorDisposable(editor))
    }

    private fun scheduleMarkdownWysiwygRefresh(editor: EditorEx) {
        if (!shouldRunMarkdownWysiwygRefresh(
                editorDisposed = editor.isDisposed,
                editorIsActive = activeWysiwygEditor === editor,
                refreshAlreadyScheduled = scheduledWysiwygEditor === editor,
            )
        ) return
        scheduledWysiwygEditor = editor
        ApplicationManager.getApplication().invokeLater {
            if (scheduledWysiwygEditor === editor) {
                scheduledWysiwygEditor = null
            }
            if (editor.isDisposed) return@invokeLater
            if (activeWysiwygEditor !== editor) return@invokeLater
            refreshMarkdownWysiwyg(editor)
        }
    }

    private fun refreshMarkdownWysiwyg(editor: EditorEx) {
        if (editor.isDisposed) return
        if (activeWysiwygEditor !== editor) return
        ensureWysiwygOwner(editor)
        val foldingModel = editor.foldingModel
        val markup = editor.markupModel
        val codeBlocks = MarkdownWysiwygRanges.fencedCodeBlocks(editor.document.charsSequence)

        clearWysiwygNonFoldingArtifacts()
        foldingModel.runBatchFoldingOperation {
            clearWysiwygFolds(foldingModel)

            inlineCodeAttributes(editor)?.let { attrs ->
                addDelimitedWysiwyg(
                    editor,
                    Regex("`([^`\\n]+)`"),
                    1,
                    attrs,
                    InlineCodeBorderRenderer(
                        border = inlineCodeBorder(),
                        background = attrs.backgroundColor,
                    ),
                )
            }
            addDelimitedWysiwyg(editor, Regex("\\*\\*([^*\\n]+)\\*\\*"), 2, boldAttributes())
            addDelimitedWysiwyg(editor, Regex("_(?!_)([^_\\n]+)_"), 1, italicAttributes())
            addDelimitedWysiwyg(editor, Regex("~~([^~\\n]+)~~"), 2, strikeAttributes())
            addCodeBlockWysiwyg(editor, codeBlockStyle(), codeBlocks)
        }
        addInlineCodePaddingInlays(editor, Regex("`([^`\\n]+)`"), delimiterLength = 1)
        addCodeBlockSpacerInlays(editor, codeBlocks)
        invalidateWysiwygLayout()
    }

    private fun ensureWysiwygOwner(editor: EditorEx) {
        val owner = wysiwygOwner
        if (owner === editor) return
        clearWysiwygNonFoldingArtifacts()
        if (owner != null && !owner.isDisposed) {
            owner.foldingModel.runBatchFoldingOperation {
                clearWysiwygFolds(owner.foldingModel)
            }
        } else {
            ourFolds.clear()
        }
        wysiwygOwner = editor
    }

    private fun clearWysiwygFolds(foldingModel: FoldingModel) {
        for (region in ourFolds) {
            if (region.isValid) foldingModel.removeFoldRegion(region)
        }
        ourFolds.clear()
    }

    private fun clearWysiwygNonFoldingArtifacts() {
        for (hl in ourHighlighters) {
            if (hl.isValid) hl.dispose()
        }
        ourHighlighters.clear()
        for (inlay in ourInlays) {
            if (inlay.isValid) inlay.dispose()
        }
        ourInlays.clear()
    }

    private fun invalidateWysiwygLayout() {
        field.revalidate()
        field.repaint()
        revalidate()
        repaint()
        SwingUtilities.invokeLater {
            field.revalidate()
            field.repaint()
            var component: Component? = this
            repeat(4) {
                val current = component as? JComponent ?: return@repeat
                current.revalidate()
                current.repaint()
                component = current.parent
            }
        }
    }

    private fun addCodeBlockWysiwyg(
        editor: EditorEx,
        style: CodeBlockStyle,
        ranges: List<MarkdownWysiwygRange>,
    ) {
        val foldingModel = editor.foldingModel
        val markup = editor.markupModel
        for (range in ranges) {
            val open = foldingModel.createFoldRegion(range.openStart, range.openEnd, "", null, true)
            val close = foldingModel.createFoldRegion(range.closeStart, range.closeFoldEnd, "", null, true)
            if (open != null) {
                open.isExpanded = false
                ourFolds += open
            }
            if (close != null) {
                close.isExpanded = false
                ourFolds += close
            }
            for (indent in range.contentIndentFolds) {
                val fold = foldingModel.createFoldRegion(indent.start, indent.end, "", null, true)
                if (fold != null) {
                    fold.isExpanded = false
                    ourFolds += fold
                }
            }
            if (range.contentStart < range.contentEnd) {
                // Erase any URL/hyperlink underlines painted by the Markdown plugin at HYPERLINK layer.
                val eraser = markup.addRangeHighlighter(
                    range.contentStart,
                    range.contentEnd,
                    HighlighterLayer.HYPERLINK + 10,
                    TextAttributes.ERASE_MARKER,
                    HighlighterTargetArea.EXACT_RANGE,
                )
                ourHighlighters += eraser
                // Re-apply code foreground above the erase layer and attach the border renderer.
                val highlighter = markup.addRangeHighlighter(
                    range.contentStart,
                    range.contentEnd,
                    HighlighterLayer.HYPERLINK + 20,
                    style.textAttributes,
                    HighlighterTargetArea.EXACT_RANGE,
                )
                highlighter.setCustomRenderer(CodeBlockRenderer(style.background, style.border))
                ourHighlighters += highlighter
            }
        }
    }

    private fun addCodeBlockSpacerInlays(editor: EditorEx, ranges: List<MarkdownWysiwygRange>) {
        if (editor.isDisposed) return
        val vPad = JBUI.scale(4)
        val spacer = SpacerRenderer(vPad)
        for (range in ranges) {
            if (range.contentStart >= range.contentEnd) continue
            editor.inlayModel.addBlockElement(range.contentStart, false, true, 0, spacer)
                ?.let { ourInlays += it }
            val lastContent = (range.contentEnd - 1).coerceAtLeast(range.contentStart)
            editor.inlayModel.addBlockElement(lastContent, true, false, 0, spacer)
                ?.let { ourInlays += it }
        }
    }

    private fun addInlineCodePaddingInlays(
        editor: EditorEx,
        pattern: Regex,
        delimiterLength: Int,
    ) {
        if (editor.isDisposed) return
        val padding = JBUI.scale(INLINE_CODE_CONTENT_PADDING)
        val text = editor.document.charsSequence
        for (m in pattern.findAll(text)) {
            val contentStart = m.range.first + delimiterLength
            val contentEnd = m.range.last + 1 - delimiterLength
            val closeEnd = m.range.last + 1
            if (contentStart >= contentEnd) continue
            for (offset in inlineCodePaddingInlayOffsets(contentStart, closeEnd, padding)) {
                if (offset.width <= 0) continue
                editor.inlayModel.addInlineElement(
                    offset.offset,
                    offset.relatesToPrecedingText,
                    InlineCodePaddingInlay(offset.width),
                )?.let {
                    ourInlays += it
                }
            }
        }
    }

    private fun addDelimitedWysiwyg(
        editor: EditorEx,
        pattern: Regex,
        delimiterLength: Int,
        attrs: TextAttributes,
        renderer: CustomHighlighterRenderer? = null,
    ) {
        val text = editor.document.charsSequence
        val foldingModel = editor.foldingModel
        val markup = editor.markupModel
        for (m in pattern.findAll(text)) {
            val openStart = m.range.first
            val closeEnd = m.range.last + 1
            val contentStart = openStart + delimiterLength
            val contentEnd = closeEnd - delimiterLength
            if (contentStart >= contentEnd) continue
            val open = foldingModel.createFoldRegion(openStart, contentStart, "", null, true)
            val close = foldingModel.createFoldRegion(contentEnd, closeEnd, "", null, true)
            if (open != null) {
                open.isExpanded = false
                ourFolds += open
            }
            if (close != null) {
                close.isExpanded = false
                ourFolds += close
            }
            val highlighter = markup.addRangeHighlighter(
                contentStart,
                contentEnd,
                HighlighterLayer.SYNTAX + 10,
                attrs,
                HighlighterTargetArea.EXACT_RANGE,
            )
            if (renderer != null) {
                highlighter.setCustomRenderer(renderer)
            }
            ourHighlighters += highlighter
        }
    }

    private fun inlineCodeAttributes(editor: EditorEx): TextAttributes? {
        val src = markdownAttributes("MARKDOWN_CODE_SPAN") ?: return null
        if (src.backgroundColor == null && src.foregroundColor == null) return null
        return inlineCodeTokenAttributes(
            source = src,
            fallbackBackground = editor.colorsScheme.defaultBackground,
        )
    }

    private fun inlineCodeBorder(): Color =
        JBColor.namedColor("Component.borderColor", JBColor.border())

    private fun boldAttributes(): TextAttributes =
        TextAttributes().apply {
            fontType = Font.BOLD
        }

    private fun italicAttributes(): TextAttributes =
        TextAttributes().apply {
            fontType = Font.ITALIC
        }

    private fun strikeAttributes(): TextAttributes =
        TextAttributes().apply {
            effectType = EffectType.STRIKEOUT
            effectColor = EditorColorsManager.getInstance().globalScheme.defaultForeground
        }

    private fun codeBlockStyle(): CodeBlockStyle {
        val fence = markdownAttributes("MARKDOWN_CODE_FENCE")
        val block = markdownAttributes("MARKDOWN_CODE_BLOCK")
        val background = fence?.backgroundColor
            ?: block?.backgroundColor
            ?: com.intellij.ui.ColorUtil.mix(
                JBColor.PanelBackground,
                JBColor.namedColor("Component.borderColor", JBColor.border()),
                0.22,
            )
        val foreground = fence?.foregroundColor
            ?: block?.foregroundColor
            ?: JBColor.foreground()
        val border = JBColor.namedColor("Component.borderColor", JBColor.border())
        val textAttributes = TextAttributes().apply {
            foregroundColor = foreground
        }
        return CodeBlockStyle(textAttributes, background, border)
    }

    private fun markdownAttributes(name: String): TextAttributes? =
        EditorColorsManager.getInstance().globalScheme.getAttributes(TextAttributesKey.find(name))

    private data class CodeBlockStyle(
        val textAttributes: TextAttributes,
        val background: Color,
        val border: Color,
    )

    private fun normalizeEmbeddedEditorScroll(editor: EditorEx) {
        val scrollingModel = editor.scrollingModel
        scrollingModel.disableAnimation()
        try {
            scrollingModel.scroll(0, 0)
        } finally {
            scrollingModel.enableAnimation()
        }
    }

    private class CodeBlockRenderer(
        private val background: Color,
        private val border: Color,
    ) : CustomHighlighterRenderer {
        override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
            val start = highlighter.startOffset.coerceIn(0, editor.document.textLength)
            val end = highlighter.endOffset.coerceIn(start, editor.document.textLength)
            if (start >= end) return

            val startLine = editor.offsetToVisualPosition(start).line
            val endLine = editor.offsetToVisualPosition((end - 1).coerceAtLeast(start)).line
            val y = editor.visualLineToY(startLine)
            val height = (editor.visualLineToY(endLine) + editor.lineHeight - y)
                .coerceAtLeast(editor.lineHeight)
            val visible = editor.scrollingModel.visibleArea
            val x = visible.x
            val width = visible.width

            val arc = JBUI.scale(6)
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = background
                g2.fillRoundRect(x, y, width, height, arc, arc)
                // Re-paint selection on top: the renderer runs after the editor's selection
                // pass, so the fill above would otherwise cover it.
                paintSelectionOverlay(editor, highlighter, g2, x, blockWidth = width)
            } finally {
                g2.dispose()
            }
        }

        private fun paintSelectionOverlay(
            editor: Editor,
            highlighter: RangeHighlighter,
            g2: Graphics2D,
            blockX: Int,
            blockWidth: Int,
        ) {
            val ex = editor as? EditorEx ?: return
            if (!ex.selectionModel.hasSelection()) return
            val selColor = EditorColorsManager.getInstance().globalScheme
                .getColor(EditorColors.SELECTION_BACKGROUND_COLOR) ?: return
            val selStart = ex.selectionModel.selectionStart
            val selEnd = ex.selectionModel.selectionEnd
            val hlStart = highlighter.startOffset.coerceAtLeast(0)
            val hlEnd = highlighter.endOffset.coerceAtMost(editor.document.textLength)
            val overlapStart = maxOf(selStart, hlStart)
            val overlapEnd = minOf(selEnd, hlEnd)
            if (overlapStart >= overlapEnd) return
            val selStartLine = editor.offsetToVisualPosition(overlapStart).line
            val selEndLine = editor.offsetToVisualPosition((overlapEnd - 1).coerceAtLeast(overlapStart)).line
            g2.color = selColor
            for (line in selStartLine..selEndLine) {
                g2.fillRect(blockX, editor.visualLineToY(line), blockWidth, editor.lineHeight)
            }
        }

    }

    private class SpacerRenderer(private val height: Int) : EditorCustomElementRenderer {
        override fun calcWidthInPixels(inlay: Inlay<*>) = 0
        override fun calcHeightInPixels(inlay: Inlay<*>) = height
        override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) = Unit
    }

    private class InlineCodeBorderRenderer(
        private val border: Color,
        private val background: Color?,
    ) : CustomHighlighterRenderer {
        override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
            val start = highlighter.startOffset.coerceIn(0, editor.document.textLength)
            val end = highlighter.endOffset.coerceIn(start, editor.document.textLength)
            if (start >= end) return

            val startLine = editor.offsetToVisualPosition(start).line
            val endLine = editor.offsetToVisualPosition((end - 1).coerceAtLeast(start)).line
            val visible = editor.scrollingModel.visibleArea
            val arc = JBUI.scale(3)
            val contentPadding = JBUI.scale(INLINE_CODE_CONTENT_PADDING)
            val verticalInset = JBUI.scale(2)
            val visibleLeft = visible.x + JBUI.scale(1)
            val visibleRight = visible.x + visible.width - JBUI.scale(2)
            val boxes = inlineCodeFragmentBoxes(
                characterBoxes = inlineCodeCharacterBoxes(editor, start, end),
                firstLine = startLine,
                firstLineStartGap = inlineCodeExternalGap(contentPadding),
                continuationStartPadding = inlineCodeLeadingPadding(contentPadding),
                endPadding = inlineCodeTrailingPadding(contentPadding),
            )

            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                for (box in boxes) {
                    if (box.line < startLine || box.line > endLine) continue
                    val x = box.left.coerceAtLeast(visibleLeft)
                    val right = box.right.coerceAtMost(visibleRight)
                    if (right <= x) continue
                    val width = (right - x).coerceAtLeast(JBUI.scale(4))
                    val y = editor.visualLineToY(box.line) + verticalInset
                    val height = (editor.lineHeight - verticalInset * 2).coerceAtLeast(JBUI.scale(8))
                    if (background != null) {
                        g2.color = background
                        g2.fillRoundRect(x, y, width, height, arc, arc)
                    }
                    g2.color = border
                    g2.drawRoundRect(x, y, width, height, arc, arc)
                }
            } finally {
                g2.dispose()
            }
        }

        private fun inlineCodeCharacterBoxes(
            editor: Editor,
            start: Int,
            end: Int,
        ): List<InlineCodeCharacterBox> {
            val text = editor.document.charsSequence
            val metrics = editor.contentComponent.getFontMetrics(editor.contentComponent.font)
            return (start until end).mapNotNull { offset ->
                val position = editor.offsetToVisualPosition(offset)
                val left = editor.offsetToXY(offset).x
                val nextOffset = offset + 1
                val nextPosition = if (nextOffset <= editor.document.textLength) {
                    editor.offsetToVisualPosition(nextOffset)
                } else {
                    null
                }
                val right = if (nextPosition != null && nextPosition.line == position.line) {
                    editor.offsetToXY(nextOffset).x
                } else {
                    left + metrics.charWidth(text[offset])
                }
                val normalizedLeft = minOf(left, right)
                val normalizedRight = maxOf(left, right)
                if (normalizedRight <= normalizedLeft) {
                    null
                } else {
                    InlineCodeCharacterBox(position.line, normalizedLeft, normalizedRight)
                }
            }
        }
    }

    private class InlineCodePaddingInlay(private val width: Int) : EditorCustomElementRenderer {
        override fun calcWidthInPixels(inlay: Inlay<*>): Int = width

        override fun paint(
            inlay: Inlay<*>,
            g: Graphics,
            targetRegion: Rectangle,
            textAttributes: TextAttributes,
        ) = Unit
    }

    companion object {
        /**
         * Set while a Cmd+Z / Cmd+Shift+Z initiated from a MarkdownEditablePane
         * is running. StepCard / EditableBodyBlockSection read this flag to
         * bypass the "skip update while focused" guard, so undo can refresh
         * the visible content of the focused field. setTextSuppressing then
         * preserves caret position so the user stays in the field.
         */
        val undoInProgress: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

        internal fun shouldRunMarkdownWysiwygRefresh(
            editorDisposed: Boolean,
            editorIsActive: Boolean,
            refreshAlreadyScheduled: Boolean,
        ): Boolean = !editorDisposed && editorIsActive && !refreshAlreadyScheduled

        internal fun caretOffsetAfterTextSync(
            previousText: String,
            nextText: String,
            previousCaretOffset: Int,
        ): Int {
            val oldCaret = previousCaretOffset.coerceIn(0, previousText.length)
            var prefix = 0
            val minLength = minOf(previousText.length, nextText.length)
            while (prefix < minLength && previousText[prefix] == nextText[prefix]) {
                prefix++
            }
            if (oldCaret <= prefix) return oldCaret.coerceIn(0, nextText.length)

            var suffix = 0
            val maxSuffix = minOf(previousText.length - prefix, nextText.length - prefix)
            while (
                suffix < maxSuffix &&
                previousText[previousText.length - 1 - suffix] == nextText[nextText.length - 1 - suffix]
            ) {
                suffix++
            }

            val oldSuffixStart = previousText.length - suffix
            val newSuffixStart = nextText.length - suffix
            return if (oldCaret >= oldSuffixStart) {
                val offsetFromSuffixStart = oldCaret - oldSuffixStart
                (newSuffixStart + offsetFromSuffixStart).coerceIn(0, nextText.length)
            } else {
                prefix.coerceIn(0, nextText.length)
            }
        }

        internal fun inlineCodeTokenAttributes(
            source: TextAttributes,
            fallbackBackground: Color,
        ): TextAttributes =
            TextAttributes().apply {
                backgroundColor = source.backgroundColor
                foregroundColor = source.foregroundColor
                // Inline code is literal text, but the bundled Markdown highlighting lexer
                // tags a URL inside single backticks as a GFM autolink and paints it with
                // the hyperlink foreground + underline (MARKDOWN_AUTO_LINK falls back to
                // CodeInsightColors.HYPERLINK_ATTRIBUTES). Reasserting the code-span
                // foreground above replaces the hyperlink color; overdrawing the underline
                // slot in the token's own background color hides the underline so no link
                // styling appears inside inline code. Scoped to this embedded editor only.
                effectType = EffectType.LINE_UNDERSCORE
                effectColor = source.backgroundColor ?: fallbackBackground
            }

        internal fun inlineCodeFragmentBoxes(
            characterBoxes: List<InlineCodeCharacterBox>,
            firstLine: Int,
            firstLineStartGap: Int,
            continuationStartPadding: Int,
            endPadding: Int,
        ): List<InlineCodeFragmentBox> =
            characterBoxes
                .groupBy { it.line }
                .toSortedMap()
                .map { (line, boxes) ->
                    val left = boxes.minOf { it.left }
                    InlineCodeFragmentBox(
                        line = line,
                        left = if (line == firstLine) {
                            left + firstLineStartGap
                        } else {
                            left - continuationStartPadding
                        },
                        right = boxes.maxOf { it.right } + endPadding,
                    )
                }

        internal fun inlineCodeLeadingPadding(contentPadding: Int): Int = contentPadding * 2

        internal fun inlineCodeTrailingPadding(contentPadding: Int): Int = contentPadding * 2

        internal fun inlineCodePaddingInlayOffsets(
            contentStart: Int,
            closeEnd: Int,
            contentPadding: Int,
        ): List<InlinePaddingInlayOffset> =
            if (contentStart >= closeEnd) {
                emptyList()
            } else {
                listOf(
                    InlinePaddingInlayOffset(
                        offset = contentStart,
                        relatesToPrecedingText = false,
                        width = inlineCodeLeadingInlayWidth(contentPadding),
                    ),
                    InlinePaddingInlayOffset(
                        offset = closeEnd,
                        relatesToPrecedingText = true,
                        width = inlineCodeTrailingInlayWidth(contentPadding),
                    ),
                )
            }

        internal fun inlineCodeLeadingInlayWidth(contentPadding: Int): Int =
            inlineCodeExternalGap(contentPadding) + inlineCodeLeadingPadding(contentPadding)

        internal fun inlineCodeTrailingInlayWidth(contentPadding: Int): Int =
            inlineCodeTrailingPadding(contentPadding) + inlineCodeExternalGap(contentPadding)

        internal fun inlineCodeExternalGap(contentPadding: Int): Int =
            contentPadding.coerceAtLeast(JBUI.scale(INLINE_CODE_EXTERNAL_GAP))

        internal fun codeBlockContainerWidth(
            renderedLineRightEdges: List<Int>,
            blockLeft: Int,
            trailingPadding: Int,
            maxWidth: Int,
        ): Int {
            val rightEdge = renderedLineRightEdges.maxOrNull() ?: blockLeft
            val preferred = rightEdge - blockLeft + trailingPadding
            return preferred.coerceIn(JBUI.scale(24), maxWidth.coerceAtLeast(JBUI.scale(24)))
        }

        private fun markdownFileType() =
            FileTypeManager.getInstance().getFileTypeByExtension("md")

        private val MarkdownFormatAction.labelKey: String
            get() = when (this) {
                MarkdownFormatAction.BOLD -> "toolbar.markdown.bold.label"
                MarkdownFormatAction.ITALIC -> "toolbar.markdown.italic.label"
                MarkdownFormatAction.STRIKE -> "toolbar.markdown.strike.label"
                MarkdownFormatAction.INLINE_CODE -> "toolbar.markdown.inlineCode.label"
                MarkdownFormatAction.CODE_BLOCK -> "toolbar.markdown.codeBlock.label"
                MarkdownFormatAction.BULLET_LIST -> "toolbar.markdown.bulletList.label"
                MarkdownFormatAction.NUMBERED_LIST -> "toolbar.markdown.numberedList.label"
            }

        private val MarkdownFormatAction.tooltipKey: String
            get() = when (this) {
                MarkdownFormatAction.BOLD -> "toolbar.markdown.bold.tooltip"
                MarkdownFormatAction.ITALIC -> "toolbar.markdown.italic.tooltip"
                MarkdownFormatAction.STRIKE -> "toolbar.markdown.strike.tooltip"
                MarkdownFormatAction.INLINE_CODE -> "toolbar.markdown.inlineCode.tooltip"
                MarkdownFormatAction.CODE_BLOCK -> "toolbar.markdown.codeBlock.tooltip"
                MarkdownFormatAction.BULLET_LIST -> "toolbar.markdown.bulletList.tooltip"
                MarkdownFormatAction.NUMBERED_LIST -> "toolbar.markdown.numberedList.tooltip"
            }

        private fun MarkdownFormatAction.toolbarButtonSize() =
            JBUI.size(
                when (this) {
                    MarkdownFormatAction.CODE_BLOCK,
                    MarkdownFormatAction.NUMBERED_LIST -> 28
                    else -> 24
                },
                24,
            )

        private fun MarkdownFormatAction.toolbarIcon(): Icon =
            when (this) {
                MarkdownFormatAction.BOLD -> MarkdownIcons.EditorActions.Bold
                MarkdownFormatAction.ITALIC -> MarkdownIcons.EditorActions.Italic
                MarkdownFormatAction.STRIKE -> MarkdownIcons.EditorActions.Strike_through
                MarkdownFormatAction.INLINE_CODE -> MarkdownIcons.EditorActions.Code_span
                MarkdownFormatAction.CODE_BLOCK -> CodeBlockToolbarIcon(MarkdownIcons.EditorActions.Code_span)
                MarkdownFormatAction.BULLET_LIST -> MarkdownIcons.EditorActions.BulletList
                MarkdownFormatAction.NUMBERED_LIST -> MarkdownIcons.EditorActions.NumberedList
            }

        private class CodeBlockToolbarIcon(private val base: Icon) : Icon {
            override fun getIconWidth(): Int = JBUI.scale(16)

            override fun getIconHeight(): Int = JBUI.scale(16)

            override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
                val g2 = g.create() as Graphics2D
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.color = c?.foreground ?: JBColor.foreground()
                    val frameX = x + JBUI.scale(1)
                    val frameY = y + JBUI.scale(2)
                    val frameWidth = iconWidth - JBUI.scale(3)
                    val frameHeight = iconHeight - JBUI.scale(5)
                    g2.drawRoundRect(frameX, frameY, frameWidth, frameHeight, JBUI.scale(2), JBUI.scale(2))
                    val scale = 0.75
                    val scaledWidth = (base.iconWidth * scale).toInt()
                    val scaledHeight = (base.iconHeight * scale).toInt()
                    val iconX = x + (iconWidth - scaledWidth) / 2
                    val iconY = y + (iconHeight - scaledHeight) / 2
                    val iconGraphics = g2.create(iconX, iconY, scaledWidth, scaledHeight) as Graphics2D
                    try {
                        iconGraphics.scale(scale, scale)
                        base.paintIcon(c, iconGraphics, 0, 0)
                    } finally {
                        iconGraphics.dispose()
                    }
                } finally {
                    g2.dispose()
                }
            }
        }
    }
}

internal data class InlineCodeCharacterBox(
    val line: Int,
    val left: Int,
    val right: Int,
)

internal data class InlineCodeFragmentBox(
    val line: Int,
    val left: Int,
    val right: Int,
)

internal data class InlinePaddingInlayOffset(
    val offset: Int,
    val relatesToPrecedingText: Boolean,
    val width: Int,
)
