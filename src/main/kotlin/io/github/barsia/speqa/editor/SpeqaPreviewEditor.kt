package io.github.barsia.speqa.editor

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.EditorColorsListener
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
import io.github.barsia.speqa.editor.ui.FloatingHeaderBar
import io.github.barsia.speqa.editor.ui.FloatingHeaderHost
import io.github.barsia.speqa.editor.ui.TestCasePanel
import io.github.barsia.speqa.editor.ui.primitives.MarkdownEditablePane
import io.github.barsia.speqa.model.TestStep
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
    private var lastEditorMutationOffset: Int? = null
    private val refreshController = PreviewRefreshController()
    internal val scrollSync = ScrollSyncController(project, textEditor)

    private val floatingHeaderBar = FloatingHeaderBar()

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
        onHeaderStateChanged = { idPrefix, id, title ->
            floatingHeaderBar.setTitle(idPrefix, id, title)
            floatingHeaderBar.setProgress(null)
        },
    )

    private val scrollPane = JBScrollPane(testCasePanel).apply {
        border = JBUI.Borders.empty()
        background = editorCanvasBackground()
        isOpaque = true
        viewport.isOpaque = true
        viewport.background = background
        horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        transferHandler = buildTransferHandler()
    }

    private val floatingHeaderHost = FloatingHeaderHost(
        scrollPane = scrollPane,
        bar = floatingHeaderBar,
        anchorYProvider = { testCasePanel.titleRowBottomY() },
    )

    private val component: JPanel = JPanel(BorderLayout()).apply {
        background = scrollPane.background
        isOpaque = true
        add(floatingHeaderHost, BorderLayout.CENTER)
    }

    private val documentListener = object : DocumentListener {
        override fun beforeDocumentChange(event: DocumentEvent) {
            if (suppressDocumentRefresh == 0) {
                lastEditorMutationOffset = event.offset
                scrollSync.suppressEditorToPanelForDocumentMutation()
            }
        }

        override fun documentChanged(event: DocumentEvent) {
            if (suppressDocumentRefresh == 0) {
                val timing = refreshController.requestRefresh(MarkdownEditablePane.undoInProgress.get())
                when (timing) {
                    PreviewRefreshTiming.IMMEDIATE -> {
                        refreshTimer.stop()
                        ApplicationManager.getApplication().invokeLater { refreshFromDocument() }
                    }
                    PreviewRefreshTiming.DEBOUNCED -> refreshTimer.restart()
                    PreviewRefreshTiming.NONE -> Unit
                }
            }
        }
    }

    private val refreshTimer = Timer(300) { refreshFromDocument() }.apply {
        isRepeats = false
    }

    private fun refreshFromDocument() {
        // Editor-driven refresh: rebuilding the preview can shrink/grow its
        // total content height, and the fraction-based scroll-sync would
        // then mirror that delta back to the editor — causing both panes to
        // visibly jump on every external text edit. Capture the offsets,
        // suppress the sync in both directions while the panel re-lays out,
        // and restore the panel's vertical position after the layout pass
        // (Swing's revalidate is asynchronous, so setting bar.value before
        // the new maximum is in effect would still snap the viewport).
        try {
            val preservedPanelPosition = scrollSync.preservedVerticalPosition()
            val preservedEditorOffset = if (!textEditor.isDisposed) {
                textEditor.scrollingModel.verticalScrollOffset
            } else -1
            val forceFocusedTextSync = refreshController.consumeForceFocusedTextSync()
            val documentText = document.text
            val nextParsed = parseTestCaseSafely(documentText)
            if (shouldDeferStepShrink(
                    currentSteps = parsed.testCase.steps,
                    nextSteps = nextParsed.testCase.steps,
                    documentText = documentText,
                    lastMutationOffset = lastEditorMutationOffset,
                )
            ) {
                return
            }
            scrollSync.suppressBothDirections()
            parsed = nextParsed
            idState.refresh()
            testCasePanel.updateFrom(parsed.testCase, forceFocusedTextSync = forceFocusedTextSync)
            ApplicationManager.getApplication().invokeLater {
                scrollSync.restoreVerticalPosition(preservedPanelPosition)
                if (preservedEditorOffset >= 0 && !textEditor.isDisposed) {
                    textEditor.scrollingModel.disableAnimation()
                    textEditor.scrollingModel.scrollVertically(preservedEditorOffset)
                    textEditor.scrollingModel.enableAnimation()
                }
            }
        } finally {
            refreshController.markRefreshCompleted()
        }
    }

    init {
        @Suppress("DEPRECATION")
        DataManager.registerDataProvider(component) { dataId ->
            when {
                CommonDataKeys.PROJECT.`is`(dataId) -> project
                CommonDataKeys.VIRTUAL_FILE.`is`(dataId) -> file
                PlatformCoreDataKeys.FILE_EDITOR.`is`(dataId) -> this
                // Do NOT expose HOST_EDITOR / EDITOR keys. See TestRunEditor
                // for the rationale - exposing them routes typed characters
                // into the underlying text editor and breaks input in every
                // Swing text field on the preview side.
                else -> null
            }
        }
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
        val appConnection = ApplicationManager.getApplication().messageBus.connect(this)
        appConnection.subscribe(
            com.intellij.ide.ui.LafManagerListener.TOPIC,
            com.intellij.ide.ui.LafManagerListener { applyEditorTheme() },
        )
        appConnection.subscribe(
            EditorColorsManager.TOPIC,
            EditorColorsListener { applyEditorTheme() },
        )
    }

    private fun applyEditorTheme() {
        val bg = editorCanvasBackground()
        scrollPane.background = bg
        scrollPane.viewport.background = bg
        scrollPane.viewport.view?.background = bg
        component.background = bg
        testCasePanel.refreshTheme()
        floatingHeaderBar.refreshTheme()
        scrollPane.repaint()
        component.repaint()
    }

    private fun editorCanvasBackground() =
        EditorColorsManager.getInstance().let { manager ->
            (manager.activeVisibleScheme ?: manager.globalScheme).defaultBackground
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
        val preservedPanelPosition = scrollSync.preservedVerticalPosition()
        scrollSync.suppressBothDirections()
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
                scrollSync.restoreVerticalPosition(preservedPanelPosition)
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
        val preservedPanelPosition = scrollSync.preservedVerticalPosition()
        scrollSync.suppressBothDirections()
        ApplicationManager.getApplication().invokeLater {
            try {
                com.intellij.openapi.command.CommandProcessor.getInstance().executeCommand(project, {
                    com.intellij.openapi.application.runWriteAction {
                        try {
                            val edits = DocumentPatcher.patch(document.text, operation)
                            DocumentPatcher.applyEditsAsOneReplace(document, edits)
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
                scrollSync.restoreVerticalPosition(preservedPanelPosition)
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
        @Suppress("DEPRECATION")
        DataManager.removeDataProvider(component)
    }

    companion object {
        internal fun shouldDeferStepShrink(
            currentSteps: List<TestStep>,
            nextSteps: List<TestStep>,
            documentText: String,
            lastMutationOffset: Int? = null,
        ): Boolean =
            nextSteps.size < currentSteps.size &&
                (
                    hasTrailingIncompleteTopLevelStepMarker(documentText) ||
                        isTransientEmptyTailStepShrink(
                            currentSteps = currentSteps,
                            nextSteps = nextSteps,
                            lastMutationOffset = lastMutationOffset,
                            documentText = documentText,
                        )
                    )

        internal fun isTransientEmptyTailStepShrink(
            currentSteps: List<TestStep>,
            nextSteps: List<TestStep>,
            lastMutationOffset: Int?,
            documentText: String,
        ): Boolean =
            nextSteps.size < currentSteps.size &&
                isMutationNearDocumentEnd(documentText, lastMutationOffset) &&
                currentSteps.drop(nextSteps.size).all { it.isEmptyTailStep() } &&
                retainedStepsMatchExceptTransientTailLine(
                    currentSteps = currentSteps,
                    nextSteps = nextSteps,
                    documentText = documentText,
                )

        private fun retainedStepsMatchExceptTransientTailLine(
            currentSteps: List<TestStep>,
            nextSteps: List<TestStep>,
            documentText: String,
        ): Boolean {
            if (nextSteps.isEmpty()) return true
            val lastRetainedIndex = nextSteps.lastIndex
            if (currentSteps.take(lastRetainedIndex) != nextSteps.take(lastRetainedIndex)) {
                return false
            }
            val current = currentSteps[lastRetainedIndex]
            val next = nextSteps[lastRetainedIndex]
            return current == next || next.isCurrentStepWithTransientTailLine(current, documentText)
        }

        private fun TestStep.isCurrentStepWithTransientTailLine(current: TestStep, documentText: String): Boolean {
            if (expected != current.expected ||
                expectedGroupSize != current.expectedGroupSize ||
                attachments != current.attachments ||
                tickets != current.tickets ||
                links != current.links
            ) {
                return false
            }
            val tailLine = documentText
                .lineSequence()
                .lastOrNull { it.isNotBlank() }
                ?.trimEnd()
                ?: return false
            val expectedAction = if (current.action.isBlank()) {
                tailLine
            } else {
                current.action + "\n" + tailLine
            }
            return action == expectedAction
        }

        private fun TestStep.isEmptyTailStep(): Boolean =
            action.isBlank() &&
                expected.isNullOrBlank() &&
                attachments.isEmpty() &&
                tickets.isEmpty() &&
                links.isEmpty()

        internal fun isMutationNearDocumentEnd(documentText: String, mutationOffset: Int?): Boolean {
            val offset = mutationOffset ?: return false
            val tailStart = (documentText.length - 64).coerceAtLeast(0)
            return offset >= tailStart
        }

        internal fun hasTrailingIncompleteTopLevelStepMarker(documentText: String): Boolean {
            val lastNonBlankLine = documentText
                .lineSequence()
                .lastOrNull { it.isNotBlank() }
                ?: return false
            return Regex("""^\d+(?:[./ю]\s*)?$""").matches(lastNonBlankLine)
        }
    }
}
