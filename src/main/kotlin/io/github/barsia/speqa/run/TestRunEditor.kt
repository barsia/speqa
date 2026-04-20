package io.github.barsia.speqa.run

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase

import com.intellij.openapi.vfs.VirtualFile
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.IdStateHolder
import io.github.barsia.speqa.editor.resolveTestCaseHeaderMeta
import io.github.barsia.speqa.editor.ui.editorBackgroundAwt
import io.github.barsia.speqa.model.Attachment
import io.github.barsia.speqa.model.DescriptionBlock
import io.github.barsia.speqa.model.Link
import io.github.barsia.speqa.model.PreconditionsBlock
import io.github.barsia.speqa.model.RunResult
import io.github.barsia.speqa.model.StepVerdict
import io.github.barsia.speqa.model.TestCaseBodyBlock
import io.github.barsia.speqa.model.TestRun
import io.github.barsia.speqa.parser.TestRunParser
import io.github.barsia.speqa.parser.TestRunSerializer
import io.github.barsia.speqa.registry.IdType
import org.jetbrains.jewel.bridge.JewelComposePanel
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme
import java.awt.BorderLayout
import java.awt.event.HierarchyEvent
import java.beans.PropertyChangeListener
import java.time.LocalDateTime
import javax.swing.JComponent
import javax.swing.JPanel

class TestRunEditor(
    private val project: Project,
    private val file: VirtualFile,
    private val document: Document,
    private val initialRun: TestRun,
    private val textEditor: com.intellij.openapi.editor.Editor,
) : UserDataHolderBase(), FileEditor, Disposable {

    internal val scrollSync = io.github.barsia.speqa.editor.ScrollSyncController(project, textEditor)

    private var title by mutableStateOf(initialRun.title)
    private var tags by mutableStateOf(initialRun.tags)
    private var runId by mutableStateOf(initialRun.id)
    private var stepResults by mutableStateOf(initialRun.stepResults)
    private var environment by mutableStateOf(initialRun.environment)
    private var links by mutableStateOf(initialRun.links)
    private var attachments by mutableStateOf(initialRun.attachments)
    private var bodyBlocks by mutableStateOf(initialRun.bodyBlocks)
    private var runner by mutableStateOf(initialRun.runner.ifBlank { TestRunSupport.defaultRunner() })
    private var startedAt by mutableStateOf(initialRun.startedAt)
    private var finishedAt by mutableStateOf(initialRun.finishedAt)
    private var manualResult by mutableStateOf(initialRun.manualResult)
    private var overriddenResult by mutableStateOf(initialRun.result)
    private var comment by mutableStateOf(initialRun.comment)
    private val idState = IdStateHolder(project, IdType.TEST_RUN) { runId }
    private var suppressDocumentRefresh = false
    private var themeRevision by mutableStateOf(0L)
    private val composeMountController = io.github.barsia.speqa.editor.LazyComposeMountController()

    private val createdLabel: String = run {
        val meta = resolveTestCaseHeaderMeta(project, file)
        meta.createdLabel
    }

    private val documentListener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            if (!suppressDocumentRefresh) {
                refreshTimer.restart()
            }
        }
    }

    private val refreshTimer = javax.swing.Timer(300) {
        val parsed = TestRunParser.parse(document.text)
        title = parsed.title
        tags = parsed.tags
        runId = parsed.id
        stepResults = parsed.stepResults
        environment = parsed.environment
        links = parsed.links
        attachments = parsed.attachments
        bodyBlocks = parsed.bodyBlocks
        runner = parsed.runner
        startedAt = parsed.startedAt
        finishedAt = parsed.finishedAt
        manualResult = parsed.manualResult
        overriddenResult = parsed.result
        comment = parsed.comment
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

    private fun buildComposePanel(): JComponent {
        return JewelComposePanel(
            true,
            config = {
                background = ideBackground
                isOpaque = true
            },
        ) {
            SwingBridgeTheme {
                @Suppress("UNUSED_VARIABLE")
                val currentTheme = themeRevision
                val tagRegistry = io.github.barsia.speqa.registry.SpeqaTagRegistry.getInstance(project)
                TestRunPanel(
                    project = project,
                    file = file,
                    scrollSyncController = scrollSync,
                    title = title,
                    onTitleCommit = {
                        title = it
                        saveToDocument()
                    },
                    tags = tags,
                    allKnownTags = tagRegistry.allTags,
                    onTagsChange = {
                        tags = it
                        saveToDocument()
                    },
                    runId = runId,
                    nextFreeRunId = idState.nextFreeId,
                    isRunIdDuplicate = idState.isDuplicate,
                    isRunIdEditing = idState.isEditing,
                    onRunIdEditingChange = { idState.isEditing = it },
                    onRunIdAssign = { newId ->
                        runId = newId
                        saveToDocument()
                        idState.refresh()
                    },
                    createdLabel = createdLabel,
                    startedAt = startedAt,
                    finishedAt = finishedAt,
                    result = if (manualResult) overriddenResult else TestRunSupport.deriveRunResult(stepResults),
                    manualResult = manualResult,
                    onResultOverride = { newResult ->
                        manualResult = true
                        overriddenResult = newResult
                        saveToDocument()
                    },
                    stepResults = stepResults,
                    environment = environment,
                    environmentOptions = tagRegistry.allEnvironments,
                    links = links,
                    attachments = attachments,
                    runner = runner,
                    onEnvironmentChange = {
                        environment = it
                        saveToDocument()
                    },
                    onLinksChange = {
                        links = it
                        saveToDocument()
                    },
                    onAttachmentsChange = {
                        attachments = it
                        saveToDocument()
                    },
                    onRunnerChange = {
                        runner = it
                        saveToDocument()
                    },
                    onStepActionChange = { index, action ->
                        stepResults = stepResults.toMutableList().also { results ->
                            results[index] = results[index].copy(action = action)
                        }
                        saveToDocument()
                    },
                    onStepExpectedChange = { index, expected ->
                        stepResults = stepResults.toMutableList().also { results ->
                            results[index] = results[index].copy(expected = expected)
                        }
                        saveToDocument()
                    },
                    onStepVerdictChange = { index, verdict ->
                        stepResults = stepResults.toMutableList().also { results ->
                            results[index] = results[index].copy(verdict = verdict)
                        }
                        maybeSetStartedAt()
                        updateFinishedAt()
                        saveToDocument()
                    },
                    onStepCommentChange = { index, stepComment ->
                        stepResults = stepResults.toMutableList().also { results ->
                            results[index] = results[index].copy(comment = stepComment)
                        }
                        maybeSetStartedAt()
                        saveToDocument()
                    },
                    onStepTicketChange = { index, tickets ->
                        stepResults = stepResults.toMutableList().also { results ->
                            results[index] = results[index].copy(tickets = tickets)
                        }
                        saveToDocument()
                    },
                    onStepLinkChange = { index, links ->
                        stepResults = stepResults.toMutableList().also { results ->
                            results[index] = results[index].copy(links = links)
                        }
                        saveToDocument()
                    },
                    onStepAttachmentsChange = { index, attachments ->
                        stepResults = stepResults.toMutableList().also { results ->
                            results[index] = results[index].copy(attachments = attachments)
                        }
                        saveToDocument()
                    },
                    priority = initialRun.priority,
                    bodyBlocks = bodyBlocks,
                    onBodyBlocksChange = {
                        bodyBlocks = it
                        saveToDocument()
                    },
                    onOpenAttachment = { attachment ->
                        io.github.barsia.speqa.editor.AttachmentSupport.resolveFile(project, file, attachment)?.let { vf ->
                            com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFile(vf, true)
                        }
                    },
                    comment = comment,
                    onCommentChange = {
                        comment = it
                        maybeSetStartedAt()
                        saveToDocument()
                    },
                )
            }
        }.apply {
            background = ideBackground
            isOpaque = true
        }
    }

    private fun scheduleComposeMountIfNeeded() {
        if (!composeMountController.shouldRequestMount(component.isDisplayable)) return
        ApplicationManager.getApplication().invokeLater(
            {
                @Suppress("DEPRECATION")
                if (com.intellij.openapi.util.Disposer.isDisposed(this)) return@invokeLater
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
        val insertNewline = object : com.intellij.openapi.actionSystem.AnAction() {
            override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                io.github.barsia.speqa.editor.ui.FocusedMultilineInsertion.invokeNewline()
            }
        }
        val shortcuts = com.intellij.openapi.actionSystem.CustomShortcutSet(
            com.intellij.openapi.actionSystem.KeyboardShortcut(javax.swing.KeyStroke.getKeyStroke("control ENTER"), null),
            com.intellij.openapi.actionSystem.KeyboardShortcut(javax.swing.KeyStroke.getKeyStroke("control shift ENTER"), null),
        )
        insertNewline.registerCustomShortcutSet(shortcuts, component)
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
        project.messageBus.connect(this).subscribe(
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

    private fun maybeSetStartedAt() {
        if (startedAt == null) {
            startedAt = LocalDateTime.now()
        }
    }

    private fun updateFinishedAt() {
        val allHaveVerdict = stepResults.isNotEmpty() && stepResults.all { it.verdict != StepVerdict.NONE }
        finishedAt = if (allHaveVerdict) finishedAt ?: LocalDateTime.now() else null
    }

    private fun saveToDocument() {
        val snapshotRunId = runId
        val snapshotTitle = title
        val snapshotTags = tags
        val snapshotManualResult = manualResult
        val snapshotOverriddenResult = overriddenResult
        val snapshotEnvironment = environment
        val snapshotLinks = links
        val snapshotAttachments = attachments
        val snapshotBodyBlocks = bodyBlocks
        val snapshotRunner = runner
        val snapshotStepResults = stepResults
        val snapshotStartedAt = startedAt
        val snapshotFinishedAt = finishedAt
        val snapshotComment = comment
        val autoResult = TestRunSupport.deriveRunResult(snapshotStepResults)
        val run = TestRun(
            id = snapshotRunId,
            title = snapshotTitle,
            tags = snapshotTags,
            startedAt = snapshotStartedAt,
            finishedAt = snapshotFinishedAt,
            result = if (snapshotManualResult) snapshotOverriddenResult else autoResult,
            manualResult = snapshotManualResult,
            environment = snapshotEnvironment,
            runner = snapshotRunner,
            priority = initialRun.priority,
            bodyBlocks = snapshotBodyBlocks,
            links = snapshotLinks,
            attachments = snapshotAttachments,
            stepResults = snapshotStepResults,
            comment = snapshotComment,
        )
        val content = TestRunSerializer.serialize(run)
        suppressDocumentRefresh = true
        ApplicationManager.getApplication().invokeLater({
            try {
                CommandProcessor.getInstance().executeCommand(project, {
                    runWriteAction {
                        TestRunSupport.updateDocument(document, content)
                    }
                }, "Speqa: Update test run", null)
            } finally {
                suppressDocumentRefresh = false
            }
        }, ModalityState.defaultModalityState())
    }

    override fun getComponent(): JComponent = component

    override fun getPreferredFocusedComponent(): JComponent? = composePanel

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
