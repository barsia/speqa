package io.github.barsia.speqa.run

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
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
import io.github.barsia.speqa.editor.IdStateHolder
import io.github.barsia.speqa.editor.PreviewRefreshController
import io.github.barsia.speqa.editor.PreviewRefreshTiming
import io.github.barsia.speqa.editor.ui.FloatingHeaderBar
import io.github.barsia.speqa.editor.ui.FloatingHeaderHost
import io.github.barsia.speqa.editor.ui.primitives.MarkdownEditablePane
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
    private val refreshController = PreviewRefreshController()

    private val floatingHeaderBar = FloatingHeaderBar()

    private val panel = io.github.barsia.speqa.editor.ui.TestCasePanel(
        project = project,
        file = file,
        mode = io.github.barsia.speqa.editor.ui.PanelMode.RUN,
        onChange = { /* case-side callback never fires in RUN mode */ },
        onPatch = null,
        onRunChange = { updated ->
            current = updated
            // saveToDocument suppresses the document listener, so the panel
            // would otherwise miss the new header timestamps / overall result
            // until the next external refresh. Push them directly.
            refreshHeaderFromCurrent()
            saveToDocument()
        },
        onRunPatch = { updated, op ->
            current = updated
            patchFromPreview(updated, op)
        },
        onHeaderStateChanged = { idPrefix, id, title ->
            floatingHeaderBar.setTitle(idPrefix, id, title)
            // Progress is now derived inside the panel and shown in the progressLabel;
            // we no longer push progress text through the floating header bar.
            floatingHeaderBar.setProgress(null)
        },
    )

    private val scrollPane = JBScrollPane(panel).apply {
        border = JBUI.Borders.empty()
        background = editorCanvasBackground()
        isOpaque = true
        viewport.isOpaque = true
        viewport.background = background
        horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    }

    private val floatingHeaderHost = FloatingHeaderHost(
        scrollPane = scrollPane,
        bar = floatingHeaderBar,
        anchorYProvider = { panel.titleRowBottomY() },
    )

    private val component: JPanel = JPanel(BorderLayout()).apply {
        background = scrollPane.background
        isOpaque = true
        add(floatingHeaderHost, BorderLayout.CENTER)
    }

    private val documentListener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            if (suppressDocumentRefresh == 0) {
                when (refreshController.requestRefresh(MarkdownEditablePane.undoInProgress.get())) {
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
        // Editor-driven refresh: preserve both panes' scroll offsets so the
        // fraction-based scroll-sync does not bounce the editor or preview
        // when rebuilding the panel changes its total content height. The
        // panel restore is deferred to invokeLater so the new layout (and
        // updated verticalScrollBar.maximum) is in effect before we set the
        // bar value — otherwise the viewport would still snap.
        try {
            val preservedPanelOffset = scrollSync.preservedVerticalOffset()
            val preservedEditorOffset = if (!textEditor.isDisposed) {
                textEditor.scrollingModel.verticalScrollOffset
            } else -1
            scrollSync.suppressEditorToPanelSync()
            val forceFocusedTextSync = refreshController.consumeForceFocusedTextSync()
            val parsed = TestRunParser.parse(document.text)
            current = parsed
            idState.refresh()
            panel.updateFromRun(parsed, forceFocusedTextSync = forceFocusedTextSync)
            ApplicationManager.getApplication().invokeLater {
                scrollSync.restoreVerticalOffset(preservedPanelOffset)
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
                // Do NOT expose HOST_EDITOR / EDITOR keys. Doing so makes the
                // IDE route typed characters and editor actions into the
                // underlying text editor on the left, leaving every Swing
                // text field in the preview unable to receive input.
                // `FILE_EDITOR` alone is enough for `UndoAction` /
                // `RedoAction` to identify the document.
                else -> null
            }
        }
        document.addDocumentListener(documentListener, this)
        idState.start()
        panel.updateFromRun(current)
        scrollSync.attachScrollPane(scrollPane)

        val connection = ApplicationManager.getApplication().messageBus.connect(this)
        connection.subscribe(
            com.intellij.ide.ui.LafManagerListener.TOPIC,
            com.intellij.ide.ui.LafManagerListener { applyEditorTheme() },
        )
        connection.subscribe(
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
        panel.refreshTheme()
        floatingHeaderBar.refreshTheme()
        scrollPane.repaint()
        component.repaint()
    }

    private fun editorCanvasBackground() =
        EditorColorsManager.getInstance().let { manager ->
            (manager.activeVisibleScheme ?: manager.globalScheme).defaultBackground
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
                            DocumentPatcher.applyEditsAsOneReplace(document, edits)
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

    private fun refreshHeaderFromCurrent() {
        panel.updateFromRun(current)
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
        @Suppress("DEPRECATION")
        DataManager.removeDataProvider(component)
    }

    override fun getFile(): VirtualFile = file
}
