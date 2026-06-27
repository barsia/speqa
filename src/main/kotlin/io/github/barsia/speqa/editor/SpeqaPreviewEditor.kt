package io.github.barsia.speqa.editor

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBScrollPane
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.ui.FloatingHeaderBar
import io.github.barsia.speqa.editor.ui.TestCasePanel
import io.github.barsia.speqa.editor.ui.primitives.MarkdownEditablePane
import io.github.barsia.speqa.model.TestStep
import io.github.barsia.speqa.parser.DocumentPatcher
import io.github.barsia.speqa.parser.PatchOperation
import io.github.barsia.speqa.registry.IdType
import javax.swing.JComponent

class SpeqaPreviewEditor(
    project: Project,
    file: VirtualFile,
    document: Document,
    textEditor: com.intellij.openapi.editor.Editor,
) : SpeqaEditorBase(project, file, document, textEditor) {

    private var parsed: ParsedTestCase = parseTestCaseSafely(document.text)
    private val idState = IdStateHolder(project, IdType.TEST_CASE) { parsed.testCase.id }
    private var lastEditorMutationOffset: Int? = null

    private val testCasePanel = TestCasePanel(
        project = project,
        file = virtualFile,
        onChange = { updated ->
            suppressedDocumentWrite(
                commandName = "Speqa: Update preview",
                snapshotUpdate = { parsed = ParsedTestCase(updated) },
                write = {
                    val serialized = io.github.barsia.speqa.parser.TestCaseSerializer.serialize(updated)
                    if (serialized != document.text) document.replaceString(0, document.textLength, serialized)
                },
                triggerRefresh = false,
            )
        },
        onPatch = { updated, op ->
            suppressedDocumentWrite(
                commandName = "Speqa: Update preview",
                snapshotUpdate = { parsed = ParsedTestCase(updated) },
                write = {
                    try {
                        val edits = DocumentPatcher.patch(document.text, op)
                        DocumentPatcher.applyEditsAsOneReplace(document, edits)
                    } catch (_: Exception) {
                        document.setText(io.github.barsia.speqa.parser.TestCaseSerializer.serialize(updated))
                    }
                },
                triggerRefresh = true,
            )
        },
        onRun = { startTestRun(project, virtualFile) },
        onHeaderStateChanged = { idPrefix, id, title ->
            floatingHeaderBar.setTitle(idPrefix, id, title)
            floatingHeaderBar.setProgress(null)
        },
    )

    override fun createInnerPanel(): JComponent = testCasePanel

    override fun panelAnchorY(): Int = testCasePanel.titleRowBottomY()

    override fun refreshInnerPanelTheme() = testCasePanel.refreshTheme()

    override fun getName(): String = SpeqaBundle.message("editor.preview.name")

    override fun configureScrollPane(pane: JBScrollPane) {
        pane.transferHandler = buildTransferHandler()
    }

    override fun onBeforeDocumentChange(event: DocumentEvent) {
        lastEditorMutationOffset = event.offset
    }

    override fun refreshFromDocument() {
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
                // The shrink guard defers a transient empty tail step while the user is
                // typing a new one. But undoing "Add step" is a PERMANENT shrink that would
                // otherwise bail here forever (the snapshot stays at N+1 while the document is
                // N, desyncing the preview). Clear the mutation offset and re-run once: with no
                // offset, isMutationNearDocumentEnd is false so the retry refreshes correctly.
                // If the user is actually typing, the next keystroke restores the offset and
                // restarts the timer, so the original defer-while-typing behavior is preserved.
                lastEditorMutationOffset = null
                if (!refreshTimer.isRunning) refreshTimer.restart()
                return
            }
            scrollSync.suppressBothDirections()
            parsed = nextParsed
            idState.refresh()
            testCasePanel.updateFrom(parsed.testCase, forceFocusedTextSync = forceFocusedTextSync)
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
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

    override fun disposeSubclass() {
        idState.stop()
    }

    override fun getPreferredFocusedComponent(): JComponent? = testCasePanel

    init {
        initBase()
        idState.start()
        testCasePanel.updateFrom(parsed.testCase)
        scrollSync.attachScrollPane(scrollPane)
        scrollSync.attachStepsSection(testCasePanel.stepsSection)

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
                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                            testCasePanel.updateFrom(parsed.testCase)
                        }
                    }
                }
            },
        )
    }

    private fun buildTransferHandler() = object : javax.swing.TransferHandler() {
        override fun canImport(support: TransferSupport): Boolean =
            support.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor)

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
                        AttachmentSupport.copyFileToAttachments(project, virtualFile, vf)
                    }
                }
                if (newAttachments.isNotEmpty()) {
                    val allAttachments = parsed.testCase.attachments + newAttachments
                    val updated = parsed.testCase.copy(attachments = allAttachments)
                    suppressedDocumentWrite(
                        commandName = "Speqa: Add attachments",
                        snapshotUpdate = { parsed = ParsedTestCase(updated) },
                        write = { document.setText(io.github.barsia.speqa.parser.TestCaseSerializer.serialize(updated)) },
                        triggerRefresh = false,
                    )
                }
            }
            return true
        }
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
            if (currentSteps.take(lastRetainedIndex) != nextSteps.take(lastRetainedIndex)) return false
            val current = currentSteps[lastRetainedIndex]
            val next = nextSteps[lastRetainedIndex]
            return current == next || next.isCurrentStepWithTransientTailLine(current, documentText)
        }

        private fun TestStep.isCurrentStepWithTransientTailLine(current: TestStep, documentText: String): Boolean {
            if (expected != current.expected || expectedGroupSize != current.expectedGroupSize ||
                attachments != current.attachments || tickets != current.tickets || links != current.links
            ) return false
            val tailLine = documentText.lineSequence().lastOrNull { it.isNotBlank() }?.trimEnd() ?: return false
            val expectedAction = if (current.action.isBlank()) tailLine else current.action + "\n" + tailLine
            return action == expectedAction
        }

        private fun TestStep.isEmptyTailStep(): Boolean =
            action.isBlank() && expected.isNullOrBlank() && attachments.isEmpty() && tickets.isEmpty() && links.isEmpty()

        internal fun isMutationNearDocumentEnd(documentText: String, mutationOffset: Int?): Boolean {
            val offset = mutationOffset ?: return false
            return offset >= (documentText.length - 64).coerceAtLeast(0)
        }

        internal fun hasTrailingIncompleteTopLevelStepMarker(documentText: String): Boolean {
            val lastNonBlankLine = documentText.lineSequence().lastOrNull { it.isNotBlank() } ?: return false
            return Regex("""^\d+(?:[./ю]\s*)?$""").matches(lastNonBlankLine)
        }
    }
}
