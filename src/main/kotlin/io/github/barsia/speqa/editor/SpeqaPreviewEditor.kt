package io.github.barsia.speqa.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.webview.SpeqaWebViewPreviewPanel
import io.github.barsia.speqa.parser.DocumentPatcher
import io.github.barsia.speqa.parser.PatchOperation
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.Timer

class SpeqaPreviewEditor(
    private val project: Project,
    private val file: VirtualFile,
    private val document: Document,
    private val textEditor: com.intellij.openapi.editor.Editor,
) : UserDataHolderBase(), FileEditor, Disposable {

    private var parsed: ParsedTestCase = parseTestCaseSafely(document.text)
    private var suppressDocumentRefresh = 0
    internal val scrollSync = ScrollSyncController(project, textEditor)
    private val editorCaretVisibility = EditorCaretVisibilityController(textEditor)
    private var previewTextFocusActive = false
    private val editorFocusListener = object : FocusAdapter() {
        override fun focusGained(e: FocusEvent) {
            editorCaretVisibility.setSuppressed(previewTextFocusActive)
        }
    }

    private val webViewPreviewPanel = SpeqaWebViewPreviewPanel(
        project = project,
        file = file,
        initialTestCase = parsed.testCase,
        onPatch = { updated, op ->
            patchFromPreview(updated, op, "Speqa: Update preview")
        },
        onRun = { startTestRun(project, file) },
        onPreviewTextFocusChanged = { active ->
            previewTextFocusActive = active
            editorCaretVisibility.setSuppressed(active)
        },
        onPreviewScrolled = { fraction ->
            scrollSync.onPanelScrolled(fraction)
        },
    )

    private val component: JComponent = webViewPreviewPanel.component

    private val documentListener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            if (suppressDocumentRefresh == 0) {
                refreshTimer.restart()
            }
        }
    }

    private val refreshTimer = Timer(300) {
        parsed = parseTestCaseSafely(document.text)
        webViewPreviewPanel.updateFrom(parsed.testCase)
    }.apply {
        isRepeats = false
    }

    init {
        Disposer.register(this, webViewPreviewPanel)
        document.addDocumentListener(documentListener, this)
        textEditor.contentComponent.addFocusListener(editorFocusListener)
        scrollSync.attachFractionalPanel(webViewPreviewPanel::scrollToFraction)
        scrollSync.syncPanelToEditor()
        webViewPreviewPanel.updateFrom(parsed.testCase)

        val connection = project.messageBus.connect(this)
        connection.subscribe(
            com.intellij.openapi.vfs.VirtualFileManager.VFS_CHANGES,
            object : com.intellij.openapi.vfs.newvfs.BulkFileListener {
                override fun after(events: MutableList<out com.intellij.openapi.vfs.newvfs.events.VFileEvent>) {
                    val relevant = events.any {
                        it is com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent ||
                            it is com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
                    }
                    if (relevant) {
                        ApplicationManager.getApplication().invokeLater {
                            webViewPreviewPanel.updateFrom(parsed.testCase)
                        }
                    }
                }
            },
        )
    }

    private fun patchFromPreview(
        updatedTestCase: io.github.barsia.speqa.model.TestCase,
        operation: PatchOperation,
        commandName: String,
    ) {
        parsed = ParsedTestCase(updatedTestCase)
        suppressDocumentRefresh++
        val preservedEditorOffset = if (!textEditor.isDisposed) {
            textEditor.scrollingModel.verticalScrollOffset
        } else -1
        val preservedPanelOffset = scrollSync.preservedVerticalOffset()
        scrollSync.suppressEditorToPanelSync()
        ApplicationManager.getApplication().invokeLater {
            try {
                com.intellij.openapi.command.CommandProcessor.getInstance().executeCommand(project, {
                    com.intellij.openapi.application.runWriteAction {
                        try {
                            val edits = DocumentPatcher.patch(document.text, operation)
                            for (edit in edits) {
                                document.replaceString(edit.offset, edit.offset + edit.length, edit.replacement)
                            }
                        } catch (_: Exception) {
                            document.setText(io.github.barsia.speqa.parser.TestCaseSerializer.serialize(updatedTestCase))
                        }
                    }
                }, commandName, null)
                if (preservedEditorOffset >= 0 && !textEditor.isDisposed) {
                    textEditor.scrollingModel.disableAnimation()
                    textEditor.scrollingModel.scrollVertically(preservedEditorOffset)
                    textEditor.scrollingModel.enableAnimation()
                }
                scrollSync.restoreVerticalOffset(preservedPanelOffset)
            } finally {
                suppressDocumentRefresh--
            }
        }
    }

    override fun getComponent(): JComponent = component

    override fun getPreferredFocusedComponent(): JComponent? = component

    override fun getName(): String = SpeqaBundle.message("editor.preview.name")

    override fun setState(state: FileEditorState) = Unit

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = file.isValid

    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun getFile(): VirtualFile = file

    override fun dispose() {
        refreshTimer.stop()
        textEditor.contentComponent.removeFocusListener(editorFocusListener)
        editorCaretVisibility.dispose()
        scrollSync.dispose()
        webViewPreviewPanel.dispose()
    }
}

private class EditorCaretVisibilityController(
    private val editor: com.intellij.openapi.editor.Editor,
) : Disposable {
    private var suppressed = false
    private var previousCaretVisible: Boolean? = null
    private var previousCaretEnabled: Boolean? = null

    fun setSuppressed(value: Boolean) {
        if (suppressed == value) return
        suppressed = value
        if (value) suppressCaret() else restoreCaret()
    }

    private fun suppressCaret() {
        if (editor.isDisposed) return
        val editorEx = editor as? EditorEx ?: return
        previousCaretVisible = editorEx.setCaretVisible(false)
        previousCaretEnabled = editorEx.setCaretEnabled(false)
        editor.contentComponent.repaint()
    }

    private fun restoreCaret() {
        if (!editor.isDisposed) {
            val editorEx = editor as? EditorEx
            previousCaretEnabled?.let { editorEx?.setCaretEnabled(it) }
            previousCaretVisible?.let { editorEx?.setCaretVisible(it) }
            editor.contentComponent.repaint()
        }
        previousCaretVisible = null
        previousCaretEnabled = null
    }

    override fun dispose() {
        restoreCaret()
    }
}
