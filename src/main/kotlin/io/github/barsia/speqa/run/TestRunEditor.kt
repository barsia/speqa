package io.github.barsia.speqa.run

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.IdStateHolder
import io.github.barsia.speqa.model.TestRun
import io.github.barsia.speqa.parser.DocumentPatcher
import io.github.barsia.speqa.parser.PatchOperation
import io.github.barsia.speqa.parser.TestRunParser
import io.github.barsia.speqa.parser.TestRunSerializer
import io.github.barsia.speqa.registry.IdType
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Timer

class TestRunEditor(
    private val project: Project,
    private val file: VirtualFile,
    private val document: Document,
    private val initialRun: TestRun,
    private val textEditor: com.intellij.openapi.editor.Editor,
) : UserDataHolderBase(), FileEditor, Disposable {

    internal val scrollSync = io.github.barsia.speqa.editor.ScrollSyncController(project, textEditor)

    private var current: TestRun = initialRun.copy(
        runner = initialRun.runner.ifBlank { TestRunSupport.defaultRunner() },
    )
    private val idState = IdStateHolder(project, IdType.TEST_RUN) { current.id }
    private var suppressDocumentRefresh = 0

    private val panel = TestRunPanel(
        project = project,
        file = file,
        sourceCaseTitle = current.title,
        onChange = { updated ->
            current = updated
            saveToDocument()
        },
        onPatch = { updated, op ->
            current = updated
            patchFromPreview(updated, op)
        },
    )

    private val scrollPane = JBScrollPane(panel).apply {
        border = JBUI.Borders.empty()
        background = EditorColorsManager.getInstance().globalScheme.defaultBackground
        isOpaque = true
        viewport.isOpaque = true
        viewport.background = background
        horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    }

    private val component: JPanel = JPanel(BorderLayout()).apply {
        background = scrollPane.background
        isOpaque = true
        add(scrollPane, BorderLayout.CENTER)
    }

    private val documentListener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            if (suppressDocumentRefresh == 0) {
                refreshTimer.restart()
            }
        }
    }

    private val refreshTimer = Timer(300) {
        val parsed = TestRunParser.parse(document.text)
        current = parsed
        idState.refresh()
        panel.updateFrom(parsed)
    }.apply {
        isRepeats = false
    }

    init {
        document.addDocumentListener(documentListener, this)
        idState.start()
        panel.updateFrom(current)
        scrollSync.attachScrollPane(scrollPane)

        project.messageBus.connect(this).subscribe(
            com.intellij.ide.ui.LafManagerListener.TOPIC,
            com.intellij.ide.ui.LafManagerListener {
                val bg = EditorColorsManager.getInstance().globalScheme.defaultBackground
                scrollPane.background = bg
                scrollPane.viewport.background = bg
                component.background = bg
                component.repaint()
            },
        )
    }

    private fun patchFromPreview(updated: TestRun, operation: PatchOperation) {
        suppressDocumentRefresh++
        val preservedEditorOffset = if (!textEditor.isDisposed) {
            textEditor.scrollingModel.verticalScrollOffset
        } else -1
        val preservedPanelOffset = scrollSync.preservedVerticalOffset()
        scrollSync.suppressEditorToPanelSync()
        ApplicationManager.getApplication().invokeLater({
            try {
                CommandProcessor.getInstance().executeCommand(project, {
                    runWriteAction {
                        try {
                            val edits = DocumentPatcher.patch(document.text, operation)
                            for (edit in edits.sortedByDescending { it.offset }) {
                                document.replaceString(edit.offset, edit.offset + edit.length, edit.replacement)
                            }
                        } catch (_: Exception) {
                            TestRunSupport.updateDocument(document, TestRunSerializer.serialize(updated))
                        }
                    }
                }, "Speqa: Update test run", null)
                if (preservedEditorOffset >= 0 && !textEditor.isDisposed) {
                    textEditor.scrollingModel.disableAnimation()
                    textEditor.scrollingModel.scrollVertically(preservedEditorOffset)
                    textEditor.scrollingModel.enableAnimation()
                }
                scrollSync.restoreVerticalOffset(preservedPanelOffset)
            } finally {
                suppressDocumentRefresh--
            }
        }, ModalityState.defaultModalityState())
    }

    private fun saveToDocument() {
        val content = TestRunSerializer.serialize(current)
        if (content == document.text) return
        suppressDocumentRefresh++
        val preservedEditorOffset = if (!textEditor.isDisposed) {
            textEditor.scrollingModel.verticalScrollOffset
        } else -1
        val preservedPanelOffset = scrollSync.preservedVerticalOffset()
        scrollSync.suppressEditorToPanelSync()
        ApplicationManager.getApplication().invokeLater({
            try {
                CommandProcessor.getInstance().executeCommand(project, {
                    runWriteAction {
                        TestRunSupport.updateDocument(document, content)
                    }
                }, "Speqa: Update test run", null)
                if (preservedEditorOffset >= 0 && !textEditor.isDisposed) {
                    textEditor.scrollingModel.disableAnimation()
                    textEditor.scrollingModel.scrollVertically(preservedEditorOffset)
                    textEditor.scrollingModel.enableAnimation()
                }
                scrollSync.restoreVerticalOffset(preservedPanelOffset)
            } finally {
                suppressDocumentRefresh--
            }
        }, ModalityState.defaultModalityState())
    }

    override fun getComponent(): JComponent = component

    override fun getPreferredFocusedComponent(): JComponent? = panel

    override fun getName(): String = SpeqaBundle.message("editor.testRun.name")

    override fun setState(state: FileEditorState) = Unit

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = file.isValid

    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun dispose() {
        refreshTimer.stop()
        idState.stop()
        scrollSync.dispose()
    }

    override fun getFile(): VirtualFile = file
}
