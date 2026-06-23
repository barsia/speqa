package io.github.barsia.speqa.run

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.IdStateHolder
import io.github.barsia.speqa.editor.SpeqaEditorBase
import io.github.barsia.speqa.model.TestRun
import io.github.barsia.speqa.parser.DocumentPatcher
import io.github.barsia.speqa.parser.PatchOperation
import io.github.barsia.speqa.parser.TestRunParser
import io.github.barsia.speqa.parser.TestRunSerializer
import io.github.barsia.speqa.registry.IdType
import javax.swing.JComponent

class TestRunEditor(
    project: Project,
    file: VirtualFile,
    document: Document,
    initialRun: TestRun,
    textEditor: com.intellij.openapi.editor.Editor,
) : SpeqaEditorBase(project, file, document, textEditor) {

    private var current: TestRun = initialRun.copy(
        runner = initialRun.runner.ifBlank { TestRunSupport.defaultRunner() },
    )
    private val idState = IdStateHolder(project, IdType.TEST_RUN) { current.id }

    private val panel = io.github.barsia.speqa.editor.ui.TestCasePanel(
        project = project,
        file = virtualFile,
        mode = io.github.barsia.speqa.editor.ui.PanelMode.RUN,
        onChange = { /* case-side callback never fires in RUN mode */ },
        onPatch = null,
        onRunChange = { updated ->
            current = updated
            refreshHeaderFromCurrent()
            saveToDocument()
        },
        onRunPatch = { updated, op ->
            current = updated
            patchFromPreview(updated, op)
        },
        onHeaderStateChanged = { idPrefix, id, title ->
            floatingHeaderBar.setTitle(idPrefix, id, title)
            floatingHeaderBar.setProgress(null)
        },
    )

    override fun createInnerPanel(): JComponent = panel

    override fun panelAnchorY(): Int = panel.titleRowBottomY()

    override fun refreshInnerPanelTheme() = panel.refreshTheme()

    override fun getName(): String = SpeqaBundle.message("editor.testRun.name")

    override fun getPreferredFocusedComponent(): JComponent? = panel

    override fun refreshFromDocument() {
        try {
            val preservedPanelPosition = scrollSync.preservedVerticalPosition()
            val preservedEditorOffset = if (!textEditor.isDisposed) {
                textEditor.scrollingModel.verticalScrollOffset
            } else -1
            scrollSync.suppressBothDirections()
            val forceFocusedTextSync = refreshController.consumeForceFocusedTextSync()
            val parsed = TestRunParser.parse(document.text)
            current = parsed
            idState.refresh()
            panel.updateFromRun(parsed, forceFocusedTextSync = forceFocusedTextSync)
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

    private fun patchFromPreview(updated: TestRun, operation: PatchOperation) {
        suppressedDocumentWrite(
            commandName = "Speqa: Update test run",
            write = {
                try {
                    val edits = DocumentPatcher.patch(document.text, operation)
                    DocumentPatcher.applyEditsAsOneReplace(document, edits)
                } catch (_: Exception) {
                    TestRunSupport.updateDocument(document, TestRunSerializer.serialize(updated))
                }
            },
            triggerRefresh = true,
        )
    }

    private fun saveToDocument() {
        val content = TestRunSerializer.serialize(current)
        if (content == document.text) return
        suppressedDocumentWrite(
            commandName = "Speqa: Update test run",
            write = { TestRunSupport.updateDocument(document, content) },
            triggerRefresh = false,
        )
    }

    private fun refreshHeaderFromCurrent() {
        panel.updateFromRun(current)
    }

    init {
        initBase()
        idState.start()
        panel.updateFromRun(current)
        scrollSync.attachScrollPane(scrollPane)
    }
}
