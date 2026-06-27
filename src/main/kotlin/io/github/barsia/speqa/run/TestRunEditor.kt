package io.github.barsia.speqa.run

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.IdStateHolder
import io.github.barsia.speqa.editor.SpeqaEditorBase
import io.github.barsia.speqa.editor.ui.run.RunCasesContainer
import io.github.barsia.speqa.editor.ui.run.RunLayout
import io.github.barsia.speqa.editor.ui.run.RunMultiCasePanel
import io.github.barsia.speqa.model.TestRun
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

    // Layout is decided once from the initial parse. Multi-case runs render as a
    // sectioned [RunMultiCasePanel]; single-case (and empty) runs keep the flat
    // [TestCasePanel] editor unchanged. Only the matching panel is created.
    // External text edits that change the case count cannot swap the live panel
    // (the UI never changes case count itself); this is the documented limitation.
    private val layout: RunLayout = RunCasesContainer.layoutFor(current)

    private val onRunChange: (TestRun) -> Unit = { updated ->
        current = updated
        refreshHeaderFromCurrent()
        saveToDocument()
    }

    private val onHeaderStateChanged: (String, String, String) -> Unit = { idPrefix, id, title ->
        floatingHeaderBar.setTitle(idPrefix, id, title)
        floatingHeaderBar.setProgress(null)
    }

    private val flatPanel: io.github.barsia.speqa.editor.ui.TestCasePanel? =
        if (layout == RunLayout.FLAT) {
            io.github.barsia.speqa.editor.ui.TestCasePanel(
                project = project,
                file = virtualFile,
                mode = io.github.barsia.speqa.editor.ui.PanelMode.RUN,
                onChange = { /* case-side callback never fires in RUN mode */ },
                onPatch = null,
                onRunChange = onRunChange,
                onHeaderStateChanged = onHeaderStateChanged,
            )
        } else null

    private val multiPanel: RunMultiCasePanel? =
        if (layout == RunLayout.SECTIONED) {
            RunMultiCasePanel(
                project = project,
                file = virtualFile,
                initial = current,
                onRunChange = onRunChange,
                onHeaderStateChanged = onHeaderStateChanged,
            )
        } else null

    override fun createInnerPanel(): JComponent = flatPanel ?: multiPanel!!

    override fun panelAnchorY(): Int = flatPanel?.titleRowBottomY() ?: multiPanel!!.titleRowBottomY()

    override fun refreshInnerPanelTheme() {
        flatPanel?.refreshTheme()
        multiPanel?.refreshTheme()
    }

    override fun getName(): String = SpeqaBundle.message("editor.testRun.name")

    override fun getPreferredFocusedComponent(): JComponent? = flatPanel ?: multiPanel

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
            flatPanel?.updateFromRun(parsed, forceFocusedTextSync = forceFocusedTextSync)
            multiPanel?.updateFromRun(parsed, forceFocusedTextSync = forceFocusedTextSync)
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
        flatPanel?.updateFromRun(current)
        multiPanel?.updateFromRun(current)
    }

    init {
        initBase()
        idState.start()
        flatPanel?.updateFromRun(current)
        multiPanel?.updateFromRun(current)
        scrollSync.attachScrollPane(scrollPane)
    }
}
