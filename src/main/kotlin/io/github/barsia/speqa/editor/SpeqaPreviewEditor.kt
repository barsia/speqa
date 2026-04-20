package io.github.barsia.speqa.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.util.Disposer

import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.ui.SpeqaThemeColors
import io.github.barsia.speqa.parser.DocumentPatcher
import io.github.barsia.speqa.parser.PatchOperation
import io.github.barsia.speqa.registry.IdType
import io.github.barsia.speqa.editor.ui.TestCasePreview
import io.github.barsia.speqa.editor.ui.editorBackgroundAwt
import org.jetbrains.jewel.bridge.JewelComposePanel
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import java.awt.BorderLayout
import java.awt.event.HierarchyEvent
import java.beans.PropertyChangeListener
import javax.swing.KeyStroke
import javax.swing.Timer
import javax.swing.JComponent
import javax.swing.JPanel

class SpeqaPreviewEditor(
    private val project: Project,
    private val file: VirtualFile,
    private val document: Document,
    private val textEditor: com.intellij.openapi.editor.Editor,
) : UserDataHolderBase(), FileEditor, Disposable {
    private var parsed by mutableStateOf(parseTestCaseSafely(document.text))
    private var headerMeta by mutableStateOf(resolveTestCaseHeaderMeta(project, file))
    private val idState = IdStateHolder(project, IdType.TEST_CASE) { parsed.testCase.id }
    private var suppressDocumentRefresh = false
    private var attachmentRevision by mutableStateOf(0L)
    private var themeRevision by mutableStateOf(0L)
    private val composeMountController = LazyComposeMountController()
    internal val scrollSync = ScrollSyncController(project, textEditor)

    private val documentListener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            if (!suppressDocumentRefresh) {
                refreshTimer.restart()
            }
        }
    }

    private val refreshTimer = Timer(300) {
        val newParsed = parseTestCaseSafely(document.text)
        parsed = newParsed
        headerMeta = resolveTestCaseHeaderMeta(project, file)
        idState.refresh()
    }.apply {
        isRepeats = false
    }

    private var ideBackground = editorBackgroundAwt()
    private val placeholderPanel = JPanel().apply {
        background = ideBackground
        isOpaque = true
    }
    private var composePanel: JComponent? = null

    private val component = JPanel(BorderLayout()).apply {
        background = ideBackground
        isOpaque = true
        add(placeholderPanel, BorderLayout.CENTER)
    }

    @OptIn(ExperimentalJewelApi::class)
    private fun buildComposePanel(): JComponent {
        return JewelComposePanel(
            true,
            config = {
                background = ideBackground
                isOpaque = true
            },
        ) {
            SwingBridgeTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SpeqaThemeColors.surface),
                ) {
                    val tagRegistry = io.github.barsia.speqa.registry.SpeqaTagRegistry.getInstance(project)
                    TestCasePreview(
                        testCase = parsed.testCase,
                        headerMeta = headerMeta,
                        project = project,
                        file = file,
                        nextFreeTestCaseId = idState.nextFreeId,
                        isIdDuplicate = idState.isDuplicate,
                        isIdEditing = idState.isEditing,
                        onIdEditingChange = { idState.isEditing = it },
                        onRun = { startTestRun(project, file) },
                        allKnownTags = tagRegistry.allTags,
                        allKnownEnvironments = tagRegistry.allEnvironments,
                        scrollSyncController = scrollSync,
                        onIdAssign = { newId ->
                            val updated = parsed.testCase.copy(id = newId)
                            patchFromPreview(updated, PatchOperation.SetFrontmatterField("id", newId.toString()), "Speqa: Assign ID")
                            idState.refresh()
                        },
                        onTitleCommit = { updatedTitle ->
                            val updated = parsed.testCase.copy(title = updatedTitle)
                            patchFromPreview(updated, PatchOperation.SetFrontmatterField("title", updatedTitle), "Speqa: Update title")
                        },
                        onPriorityChange = { priority ->
                            val updated = parsed.testCase.copy(priority = priority)
                            patchFromPreview(updated, PatchOperation.SetFrontmatterField("priority", priority.label), "Speqa: Update preview")
                        },
                        onStatusChange = { status ->
                            val updated = parsed.testCase.copy(status = status)
                            patchFromPreview(updated, PatchOperation.SetFrontmatterField("status", status.label), "Speqa: Update preview")
                        },
                        onPatch = { updated, operation ->
                            patchFromPreview(updated, operation, "Speqa: Update preview")
                        },
                        attachmentRevision = attachmentRevision,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }.apply {
            background = ideBackground
            isOpaque = true
            transferHandler = buildTransferHandler()
        }
    }

    private fun scheduleComposeMountIfNeeded() {
        if (!composeMountController.shouldRequestMount(component.isDisplayable)) return
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(
            {
                @Suppress("DEPRECATION")
                if (Disposer.isDisposed(this)) return@invokeLater
                mountComposePanelIfNeeded()
            },
            ModalityState.any(),
        )
    }

    private fun mountComposePanelIfNeeded() {
        if (!composeMountController.shouldMount()) return
        val panel = buildComposePanel()
        composePanel = panel
        suppressPlatformEnterShortcuts(panel)
        component.remove(placeholderPanel)
        component.add(panel, BorderLayout.CENTER)
        component.revalidate()
        component.repaint()
    }

    private fun suppressPlatformEnterShortcuts(component: JComponent) {
        val insertNewline = object : AnAction() {
            override fun actionPerformed(e: AnActionEvent) {
                io.github.barsia.speqa.editor.ui.FocusedMultilineInsertion.invokeNewline()
            }
        }
        val shortcuts = CustomShortcutSet(
            KeyboardShortcut(KeyStroke.getKeyStroke("control ENTER"), null),
            KeyboardShortcut(KeyStroke.getKeyStroke("control shift ENTER"), null),
        )
        insertNewline.registerCustomShortcutSet(shortcuts, component)
    }

    private fun writeFromPreview(testCase: io.github.barsia.speqa.model.TestCase, commandName: String) {
        val serialized = io.github.barsia.speqa.parser.TestCaseSerializer.serialize(testCase)
        if (serialized == document.text) {
            return
        }
        suppressDocumentRefresh = true
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
            try {
                com.intellij.openapi.command.CommandProcessor.getInstance().executeCommand(project, {
                    com.intellij.openapi.application.runWriteAction {
                        document.setText(serialized)
                    }
                }, commandName, null)
            } finally {
                suppressDocumentRefresh = false
            }
        }
    }

    private fun patchFromPreview(updatedTestCase: io.github.barsia.speqa.model.TestCase, operation: PatchOperation, commandName: String) {
        parsed = ParsedTestCase(updatedTestCase)
        headerMeta = resolveTestCaseHeaderMeta(project, file)
        suppressDocumentRefresh = true
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
            try {
                // Patches like step reorder move the caret (IntelliJ adjusts its offset when
                // the surrounding text is replaced). With default settings the editor then
                // auto-scrolls to follow the caret — visually the text editor jumps to the
                // edited region even though the user was only interacting with the preview.
                // Snapshot the scroll offset and restore it after the write.
                val preservedScrollOffset = if (!textEditor.isDisposed) {
                    textEditor.scrollingModel.verticalScrollOffset
                } else -1
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
                if (preservedScrollOffset >= 0 && !textEditor.isDisposed) {
                    textEditor.scrollingModel.disableAnimation()
                    textEditor.scrollingModel.scrollVertically(preservedScrollOffset)
                    textEditor.scrollingModel.enableAnimation()
                }
            } finally {
                suppressDocumentRefresh = false
            }
        }
    }

    init {
        document.addDocumentListener(documentListener, this)
        idState.start()
        component.addHierarchyListener { event ->
            if ((event.changeFlags and HierarchyEvent.DISPLAYABILITY_CHANGED.toLong()) != 0L) {
                scheduleComposeMountIfNeeded()
            }
        }
        if (component.isDisplayable) {
            scheduleComposeMountIfNeeded()
        }

        val connection = project.messageBus.connect(this)
        connection.subscribe(
            com.intellij.openapi.vfs.VirtualFileManager.VFS_CHANGES,
            object : com.intellij.openapi.vfs.newvfs.BulkFileListener {
                override fun after(events: MutableList<out com.intellij.openapi.vfs.newvfs.events.VFileEvent>) {
                    val relevant = events.any { it is com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent || it is com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent }
                    if (relevant) {
                        attachmentRevision++
                    }
                }
            },
        )
        connection.subscribe(
            com.intellij.ide.ui.LafManagerListener.TOPIC,
            com.intellij.ide.ui.LafManagerListener {
                themeRevision++
                ideBackground = editorBackgroundAwt()
                composePanel?.let { panel ->
                    panel.background = ideBackground
                    panel.isOpaque = true
                }
                placeholderPanel.background = ideBackground
                placeholderPanel.isOpaque = true
                component.background = ideBackground
                component.isOpaque = true
                component.repaint()
            },
        )
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

                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
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
                        patchFromPreview(updated, PatchOperation.SetAttachments(allAttachments), "Speqa: Add attachments")
                    }
                }
                return true
            }
        }

    override fun getComponent(): JComponent = component

    override fun getPreferredFocusedComponent(): JComponent? = null

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
