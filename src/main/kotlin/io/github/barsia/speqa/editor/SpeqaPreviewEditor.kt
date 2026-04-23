package io.github.barsia.speqa.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
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
import io.github.barsia.speqa.editor.ui.TestCasePanel
import io.github.barsia.speqa.parser.DocumentPatcher
import io.github.barsia.speqa.parser.PatchOperation
import io.github.barsia.speqa.registry.IdType
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Timer

class SpeqaPreviewEditor(
    private val project: Project,
    private val file: VirtualFile,
    private val document: Document,
    private val textEditor: com.intellij.openapi.editor.Editor,
) : UserDataHolderBase(), FileEditor, Disposable {

    private var parsed: ParsedTestCase = parseTestCaseSafely(document.text)
    private val idState = IdStateHolder(project, IdType.TEST_CASE) { parsed.testCase.id }
    private var suppressDocumentRefresh = 0
    internal val scrollSync = ScrollSyncController(project, textEditor)

    private val testCasePanel = TestCasePanel(
        project = project,
        file = file,
        onChange = { updated ->
            writeFromPreview(updated, "Speqa: Update preview")
        },
        onPatch = { updated, op ->
            patchFromPreview(updated, op, "Speqa: Update preview")
        },
        onRun = { startTestRun(project, file) },
    )

    private val scrollPane = JBScrollPane(testCasePanel).apply {
        border = JBUI.Borders.empty()
        background = EditorColorsManager.getInstance().globalScheme.defaultBackground
        isOpaque = true
        viewport.isOpaque = true
        viewport.background = background
        horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        transferHandler = buildTransferHandler()
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
        parsed = parseTestCaseSafely(document.text)
        idState.refresh()
        testCasePanel.updateFrom(parsed.testCase)
    }.apply {
        isRepeats = false
    }

    init {
        document.addDocumentListener(documentListener, this)
        idState.start()
        testCasePanel.updateFrom(parsed.testCase)
        scrollSync.attachScrollPane(scrollPane)

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
                            testCasePanel.updateFrom(parsed.testCase)
                        }
                    }
                }
            },
        )
        connection.subscribe(
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

    private fun writeFromPreview(testCase: io.github.barsia.speqa.model.TestCase, commandName: String) {
        val serialized = io.github.barsia.speqa.parser.TestCaseSerializer.serialize(testCase)
        if (serialized == document.text) {
            return
        }
        // Update the local snapshot immediately; the 300 ms refresh timer will no-op
        // when the round-tripped document text equals our serialization.
        parsed = ParsedTestCase(testCase)
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
                        document.setText(serialized)
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

    private fun buildTransferHandler() = object : javax.swing.TransferHandler() {
        override fun canImport(support: TransferSupport): Boolean {
            return support.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor)
        }

        @Suppress("UNCHECKED_CAST")
        override fun importData(support: TransferSupport): Boolean {
            val files = try {
                support.transferable.getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor) as List<java.io.File>
            } catch (_: Exception) {
                return false
            }
            if (files.isEmpty()) return false

            ApplicationManager.getApplication().invokeLater {
                val newAttachments = com.intellij.openapi.application.runWriteAction<List<io.github.barsia.speqa.model.Attachment>> {
                    files.mapNotNull { javaFile ->
                        val vf = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByIoFile(javaFile)
                            ?: return@mapNotNull null
                        AttachmentSupport.copyFileToAttachments(project, file, vf)
                    }
                }
                if (newAttachments.isNotEmpty()) {
                    val allAttachments = parsed.testCase.attachments + newAttachments
                    val updated = parsed.testCase.copy(attachments = allAttachments)
                    writeFromPreview(updated, "Speqa: Add attachments")
                }
            }
            return true
        }
    }

    override fun getComponent(): JComponent = component

    override fun getPreferredFocusedComponent(): JComponent? = testCasePanel

    override fun getName(): String = SpeqaBundle.message("editor.preview.name")

    override fun setState(state: FileEditorState) = Unit

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = file.isValid

    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun getFile(): VirtualFile = file

    override fun dispose() {
        refreshTimer.stop()
        idState.stop()
        scrollSync.dispose()
    }
}
