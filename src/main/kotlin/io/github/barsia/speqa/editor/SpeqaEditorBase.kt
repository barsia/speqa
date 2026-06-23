package io.github.barsia.speqa.editor

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.ApplicationManager
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
import io.github.barsia.speqa.editor.ui.FloatingHeaderBar
import io.github.barsia.speqa.editor.ui.FloatingHeaderHost
import io.github.barsia.speqa.editor.ui.primitives.MarkdownEditablePane
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Timer

/**
 * Shared infrastructure for [SpeqaPreviewEditor] and [TestRunEditor].
 *
 * Captures: document-refresh suppression/scheduling, scroll preservation,
 * floating header, theme wiring, and the [suppressedDocumentWrite] helper that
 * guarantees a post-write refresh when [triggerRefresh] is true — eliminating
 * the asymmetry that previously caused fixes applied to one editor to silently
 * miss the other.
 */
abstract class SpeqaEditorBase(
    protected val project: Project,
    protected val virtualFile: VirtualFile,
    protected val document: Document,
    protected val textEditor: com.intellij.openapi.editor.Editor,
) : UserDataHolderBase(), FileEditor, Disposable {

    internal val scrollSync = ScrollSyncController(project, textEditor)
    protected var suppressDocumentRefresh = 0
    protected val refreshController = PreviewRefreshController()

    protected val refreshTimer = Timer(300) { refreshFromDocument() }.apply { isRepeats = false }

    protected val floatingHeaderBar = FloatingHeaderBar()

    /** The main scrollable content (e.g. TestCasePanel). */
    protected abstract fun createInnerPanel(): JComponent

    protected abstract fun panelAnchorY(): Int

    /** Called when the document changes and suppress is not active. */
    protected abstract fun refreshFromDocument()

    /** Re-theme the inner panel on LAF/colors change. */
    protected abstract fun refreshInnerPanelTheme()

    private val innerPanel: JComponent by lazy { createInnerPanel() }

    protected val scrollPane: JBScrollPane by lazy {
        JBScrollPane(innerPanel).apply {
            border = JBUI.Borders.empty()
            background = editorCanvasBackground()
            isOpaque = true
            viewport.isOpaque = true
            viewport.background = background
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            configureScrollPane(this)
        }
    }

    /** Subclass hook to configure the scroll pane (e.g. attach a TransferHandler). */
    protected open fun configureScrollPane(pane: JBScrollPane) = Unit

    private val floatingHeaderHost: FloatingHeaderHost by lazy {
        FloatingHeaderHost(
            scrollPane = scrollPane,
            bar = floatingHeaderBar,
            anchorYProvider = ::panelAnchorY,
        )
    }

    val component: JPanel by lazy {
        JPanel(BorderLayout()).apply {
            background = scrollPane.background
            isOpaque = true
            add(floatingHeaderHost, BorderLayout.CENTER)
        }
    }

    private val documentListener = object : DocumentListener {
        override fun beforeDocumentChange(event: DocumentEvent) {
            if (suppressDocumentRefresh == 0) {
                scrollSync.suppressEditorToPanelForDocumentMutation()
                onBeforeDocumentChange(event)
            }
        }

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

    /** Optional hook for subclasses that need `beforeDocumentChange` data (e.g. mutation offset). */
    protected open fun onBeforeDocumentChange(event: DocumentEvent) = Unit

    /**
     * Writes to the document under refresh suppression, optionally triggering
     * [refreshFromDocument] after the write completes.
     *
     * @param snapshotUpdate runs synchronously before suppression begins — use
     *   to update the in-memory snapshot so a subsequent refresh sees the
     *   correct previous state.
     * @param triggerRefresh when `true`, schedules [refreshFromDocument] via
     *   `invokeLater` after the write. Always pass `true` for patch operations;
     *   pass `false` only when the panel already reflects the new state and a
     *   round-trip parse would be redundant.
     */
    protected fun suppressedDocumentWrite(
        commandName: String,
        snapshotUpdate: () -> Unit = {},
        write: () -> Unit,
        triggerRefresh: Boolean,
    ) {
        snapshotUpdate()
        suppressDocumentRefresh++
        val preservedEditorOffset = if (!textEditor.isDisposed) {
            textEditor.scrollingModel.verticalScrollOffset
        } else -1
        val preservedPanelPosition = scrollSync.preservedVerticalPosition()
        scrollSync.suppressBothDirections()
        ApplicationManager.getApplication().invokeLater {
            try {
                CommandProcessor.getInstance().executeCommand(project, {
                    runWriteAction { write() }
                }, commandName, null)
                if (preservedEditorOffset >= 0 && !textEditor.isDisposed) {
                    textEditor.scrollingModel.disableAnimation()
                    textEditor.scrollingModel.scrollVertically(preservedEditorOffset)
                    textEditor.scrollingModel.enableAnimation()
                }
                scrollSync.restoreVerticalPosition(preservedPanelPosition)
            } finally {
                suppressDocumentRefresh--
                if (triggerRefresh) {
                    ApplicationManager.getApplication().invokeLater { refreshFromDocument() }
                }
            }
        }
    }

    protected fun applyEditorTheme() {
        val bg = editorCanvasBackground()
        scrollPane.background = bg
        scrollPane.viewport.background = bg
        scrollPane.viewport.view?.background = bg
        component.background = bg
        refreshInnerPanelTheme()
        floatingHeaderBar.refreshTheme()
        scrollPane.repaint()
        component.repaint()
    }

    protected fun editorCanvasBackground() =
        EditorColorsManager.getInstance().let { manager ->
            (manager.activeVisibleScheme ?: manager.globalScheme).defaultBackground
        }

    protected fun initBase() {
        @Suppress("DEPRECATION")
        DataManager.registerDataProvider(component) { dataId ->
            when {
                CommonDataKeys.PROJECT.`is`(dataId) -> project
                CommonDataKeys.VIRTUAL_FILE.`is`(dataId) -> virtualFile
                PlatformCoreDataKeys.FILE_EDITOR.`is`(dataId) -> this
                // Do NOT expose HOST_EDITOR / EDITOR keys — they route typed
                // characters into the underlying text editor and break input in
                // every Swing field on the preview side.
                else -> null
            }
        }
        document.addDocumentListener(documentListener, this)
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

    /** Called before the base [dispose] cleanup; subclasses release their own resources here. */
    protected open fun disposeSubclass() = Unit

    // ── FileEditor boilerplate ──────────────────────────────────────────────

    override fun getComponent(): JComponent = component

    override fun getPreferredFocusedComponent(): JComponent? = innerPanel

    override fun setState(state: FileEditorState) = Unit

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = file.isValid

    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun getFile(): VirtualFile = virtualFile

    override fun dispose() {
        disposeSubclass()
        refreshTimer.stop()
        scrollSync.dispose()
        @Suppress("DEPRECATION")
        DataManager.removeDataProvider(component)
    }
}
