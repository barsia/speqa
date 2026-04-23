package io.github.barsia.speqa.run

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.ui.HeaderUtilityRow
import io.github.barsia.speqa.editor.ui.InlineEditableTitleRow
import io.github.barsia.speqa.editor.ui.attachments.AttachmentList
import io.github.barsia.speqa.editor.ui.chips.InlineEditableIdRow
import io.github.barsia.speqa.editor.ui.chips.MetadataKind
import io.github.barsia.speqa.editor.ui.chips.MetadataScope
import io.github.barsia.speqa.editor.ui.chips.TagCloud
import io.github.barsia.speqa.editor.ui.links.LinkList
import io.github.barsia.speqa.editor.ui.primitives.CommitFlash
import io.github.barsia.speqa.editor.ui.primitives.MarkdownReadOnlyPane
import io.github.barsia.speqa.editor.ui.primitives.SpeqaFocusTraversalPolicy
import io.github.barsia.speqa.editor.ui.primitives.handCursor
import io.github.barsia.speqa.editor.ui.primitives.headerAddIconButton
import io.github.barsia.speqa.editor.ui.primitives.multiLineInput
import io.github.barsia.speqa.editor.ui.primitives.sectionCaption
import io.github.barsia.speqa.editor.ui.primitives.singleLineInput
import io.github.barsia.speqa.editor.ui.primitives.surfaceDivider
import io.github.barsia.speqa.editor.ui.primitives.twoColumnRow
import io.github.barsia.speqa.editor.ui.steps.mergeBodyBlocks
import io.github.barsia.speqa.model.DescriptionBlock
import io.github.barsia.speqa.model.PreconditionsBlock
import io.github.barsia.speqa.model.RunResult
import io.github.barsia.speqa.model.StepResult
import io.github.barsia.speqa.model.StepVerdict
import io.github.barsia.speqa.model.TestRun
import io.github.barsia.speqa.registry.IdType
import java.awt.Component
import java.awt.Dimension
import java.awt.Rectangle
import java.time.format.DateTimeFormatter
import javax.swing.BoxLayout
import javax.swing.DefaultListCellRenderer
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.Scrollable
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

/**
 * Swing test-run preview panel. See spec §15e for the layout contract.
 */
class TestRunPanel(
    private val project: Project?,
    private val file: VirtualFile?,
    private val sourceCaseTitle: String,
    private val onChange: (TestRun) -> Unit,
    private val onPatch: ((TestRun, io.github.barsia.speqa.parser.PatchOperation) -> Unit)? = null,
) : JPanel(), Scrollable {

    private var suppressProgrammaticSync = false

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
    override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int =
        JBUI.scale(16)
    override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int =
        if (orientation == SwingConstants.VERTICAL) visibleRect.height else visibleRect.width
    override fun getScrollableTracksViewportWidth(): Boolean = true
    override fun getScrollableTracksViewportHeight(): Boolean = false

    private fun emit(updated: TestRun, op: io.github.barsia.speqa.parser.PatchOperation) {
        current = updated
        val sink = onPatch
        if (sink != null) sink(updated, op) else onChange(updated)
    }

    private var current: TestRun = TestRun()

    private val idRow = InlineEditableIdRow(IdType.TEST_RUN) { newId ->
        onChange(current.copy(id = newId))
    }

    private val verdictCombo = ComboBox(RunResult.entries.toTypedArray()).apply {
        toolTipText = SpeqaBundle.message("panel.run.verdict")
        handCursor()
        renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean,
            ): Component {
                val text = (value as? RunResult)?.label?.replaceFirstChar { it.uppercase() } ?: ""
                return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus)
            }
        }
        addActionListener {
            if (suppressProgrammaticSync) return@addActionListener
            val picked = selectedItem as? RunResult ?: return@addActionListener
            if (picked != current.result) {
                emit(
                    current.copy(result = picked),
                    io.github.barsia.speqa.parser.PatchOperation.SetRunVerdict(picked),
                )
            }
        }
    }

    private val headerUtilityRow: HeaderUtilityRow =
        HeaderUtilityRow.forTestRun(
            idChip = idRow,
            startedLabel = "",
            finishedLabel = "",
            trailing = verdictCombo,
        )

    private val titleRow = InlineEditableTitleRow(
        initialTitle = sourceCaseTitle,
        placeholder = SpeqaBundle.message("panel.run.title.placeholder"),
        onCommit = { /* source title is read-only for runs */ },
    )

    private val runnerField = singleLineInput(
        placeholder = SpeqaBundle.message("placeholder.runner"),
        onChange = { text ->
            if (suppressProgrammaticSync) return@singleLineInput
            if (text != current.runner) {
                emit(
                    current.copy(runner = text),
                    io.github.barsia.speqa.parser.PatchOperation.SetRunner(text),
                )
            }
        },
    )

    private val environmentCloud = TagCloud(
        coloredChips = false,
        metadataScope = MetadataScope.TEST_RUNS,
        metadataKind = MetadataKind.ENVIRONMENT,
        metadataProject = project,
        hideAddButton = true,
        onAdd = { env ->
            val next = current.environment + env
            emit(current.copy(environment = next), io.github.barsia.speqa.parser.PatchOperation.SetRunEnvironment(next))
        },
        onRemove = { env ->
            val next = current.environment - env
            emit(current.copy(environment = next), io.github.barsia.speqa.parser.PatchOperation.SetRunEnvironment(next))
        },
    ).also { cloud ->
        if (project != null) {
            cloud.setAllKnownTags {
                io.github.barsia.speqa.registry.SpeqaTagRegistry.getInstance(project).allEnvironments.toSet()
            }
        }
    }

    private val tagCloud = TagCloud(
        coloredChips = true,
        metadataScope = MetadataScope.TEST_RUNS,
        metadataKind = MetadataKind.TAG,
        metadataProject = project,
        hideAddButton = true,
        onAdd = { tag ->
            val next = current.tags + tag
            emit(current.copy(tags = next), io.github.barsia.speqa.parser.PatchOperation.SetRunTags(next))
        },
        onRemove = { tag ->
            val next = current.tags - tag
            emit(current.copy(tags = next), io.github.barsia.speqa.parser.PatchOperation.SetRunTags(next))
        },
    ).also { cloud ->
        if (project != null) {
            cloud.setAllKnownTags {
                io.github.barsia.speqa.registry.SpeqaTagRegistry.getInstance(project).allTags.toSet()
            }
        }
    }

    private val attachmentList: AttachmentList? = if (project != null && file != null) {
        AttachmentList(project, file, hideAddButton = true) { next ->
            emit(
                current.copy(attachments = next),
                io.github.barsia.speqa.parser.PatchOperation.SetRunAttachments(next),
            )
        }
    } else null

    private val linkList = LinkList(project, hideAddButton = true) { next ->
        emit(
            current.copy(links = next),
            io.github.barsia.speqa.parser.PatchOperation.SetRunLinks(next),
        )
    }

    private val descriptionPane = MarkdownReadOnlyPane()
    private val preconditionsPane = MarkdownReadOnlyPane()

    private val stepResultsContainer = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
    }
    private val stepRowCards = mutableListOf<StepResultCard>()

    private var connection: MessageBusConnection? = null

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(12)
        applyBackground()
        buildLayout()
        focusTraversalPolicy = SpeqaFocusTraversalPolicy()
        isFocusCycleRoot = true
    }

    private fun buildLayout() {
        val sectionGap = JBUI.scale(10)

        headerUtilityRow.alignmentX = Component.LEFT_ALIGNMENT
        add(headerUtilityRow)
        add(javax.swing.Box.createVerticalStrut(JBUI.scale(6)))

        titleRow.alignmentX = Component.LEFT_ALIGNMENT
        add(titleRow)
        add(javax.swing.Box.createVerticalStrut(JBUI.scale(4)))

        val divider = surfaceDivider()
        divider.alignmentX = Component.LEFT_ALIGNMENT
        add(divider)
        add(javax.swing.Box.createVerticalStrut(sectionGap))

        val runnerEnvRow = twoColumnRow(
            leftCaption = SpeqaBundle.message("run.runner"),
            rightCaption = SpeqaBundle.message("label.environment"),
            leftBody = runnerField,
            rightBody = environmentCloud,
            rightHeaderAction = headerAddIconButton(
                tooltip = SpeqaBundle.message("panel.header.addEnvironment"),
                onClick = { environmentCloud.startAdd() },
            ),
        )
        runnerEnvRow.alignmentX = Component.LEFT_ALIGNMENT
        add(runnerEnvRow)
        add(javax.swing.Box.createVerticalStrut(sectionGap))

        val tagsSection = captionedSection(SpeqaBundle.message("label.tags"), tagCloud, actions = headerAddIconButton(
            tooltip = SpeqaBundle.message("panel.header.addTag"),
            onClick = { tagCloud.startAdd() },
        ))
        add(tagsSection)
        add(javax.swing.Box.createVerticalStrut(sectionGap))

        val linksAttachmentsBody: JComponent = attachmentList ?: emptyColumn()
        val linksAttachmentsRow = twoColumnRow(
            leftCaption = SpeqaBundle.message("label.links"),
            rightCaption = SpeqaBundle.message("label.attachments"),
            leftBody = linkList,
            rightBody = linksAttachmentsBody,
            leftHeaderAction = headerAddIconButton(
                tooltip = SpeqaBundle.message("panel.header.addLink"),
                onClick = { linkList.startAdd() },
            ),
            rightHeaderAction = if (attachmentList != null) headerAddIconButton(
                tooltip = SpeqaBundle.message("panel.header.addAttachment"),
                onClick = { attachmentList.startAdd() },
            ) else null,
        )
        linksAttachmentsRow.alignmentX = Component.LEFT_ALIGNMENT
        add(linksAttachmentsRow)
        add(javax.swing.Box.createVerticalStrut(sectionGap))

        add(captionedSection(SpeqaBundle.message("label.description"), descriptionPane))
        add(javax.swing.Box.createVerticalStrut(sectionGap))

        add(captionedSection(SpeqaBundle.message("label.preconditions"), preconditionsPane))
        add(javax.swing.Box.createVerticalStrut(sectionGap))

        add(captionedSection(SpeqaBundle.message("label.steps"), stepResultsContainer))
    }

    private fun emptyColumn(): JComponent {
        val p = JPanel()
        p.isOpaque = false
        return p
    }

    private fun captionedSection(caption: String, body: JComponent, actions: JComponent? = null): JPanel {
        val wrapper = JPanel()
        wrapper.layout = BoxLayout(wrapper, BoxLayout.Y_AXIS)
        wrapper.isOpaque = false
        wrapper.alignmentX = Component.LEFT_ALIGNMENT
        val headerLine = JPanel(java.awt.BorderLayout()).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(sectionCaption(caption), java.awt.BorderLayout.WEST)
            if (actions != null) add(actions, java.awt.BorderLayout.EAST)
        }
        wrapper.add(headerLine)
        wrapper.add(javax.swing.Box.createVerticalStrut(JBUI.scale(4)))
        body.alignmentX = Component.LEFT_ALIGNMENT
        wrapper.add(body)
        return wrapper
    }

    fun updateFrom(run: TestRun) {
        val previous = current
        current = run

        idRow.update(run.id, nextFreeId = (run.id ?: 0) + 1, isDuplicate = false)

        val fmt = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
        val startedText = run.startedAt?.format(fmt).orEmpty()
        val finishedText = run.finishedAt?.format(fmt).orEmpty()
        headerUtilityRow.setDates(
            leftText = if (startedText.isBlank()) "" else SpeqaBundle.message("panel.header.started", startedText),
            rightText = if (finishedText.isBlank()) "" else SpeqaBundle.message("panel.header.finished", finishedText),
        )

        if (previous.tags != run.tags) {
            tagCloud.setTags(run.tags)
            CommitFlash.flash(tagCloud)
        }
        if (previous.environment != run.environment) {
            environmentCloud.setTags(run.environment)
            CommitFlash.flash(environmentCloud)
        }
        if (previous.result != run.result && verdictCombo.selectedItem != run.result) {
            syncProgrammaticUiChange {
                verdictCombo.selectedItem = run.result
            }
            CommitFlash.flash(verdictCombo)
        }
        if (previous.runner != run.runner && runnerField.text != run.runner) {
            syncProgrammaticUiChange {
                runnerField.text = run.runner
            }
            CommitFlash.flash(runnerField)
        }
        if (previous.attachments != run.attachments) {
            attachmentList?.setAttachments(run.attachments)
            attachmentList?.let { CommitFlash.flash(it) }
        }
        if (previous.links != run.links) {
            linkList.setLinks(run.links)
            CommitFlash.flash(linkList)
        }
        if (previous.bodyBlocks != run.bodyBlocks) {
            val prevDescription = mergeBodyBlocks(previous.bodyBlocks, DescriptionBlock::class.java)
            val newDescription = mergeBodyBlocks(run.bodyBlocks, DescriptionBlock::class.java)
            if (prevDescription != newDescription) {
                descriptionPane.setMarkdown(newDescription.ifBlank { "_—_" })
                CommitFlash.flash(descriptionPane)
            }
            val prevPreconditions = mergeBodyBlocks(previous.bodyBlocks, PreconditionsBlock::class.java)
            val newPreconditions = mergeBodyBlocks(run.bodyBlocks, PreconditionsBlock::class.java)
            if (prevPreconditions != newPreconditions) {
                preconditionsPane.setMarkdown(newPreconditions.ifBlank { "_—_" })
                CommitFlash.flash(preconditionsPane)
            }
        }
        if (stepResultsStructurallyChanged(previous.stepResults, run.stepResults)) {
            rebuildStepCards(run.stepResults)
            CommitFlash.flash(stepResultsContainer)
        }
    }

    private fun stepResultsStructurallyChanged(old: List<StepResult>, new: List<StepResult>): Boolean {
        if (old.size != new.size) return true
        for (i in old.indices) {
            if (old[i].action != new[i].action) return true
            if (old[i].expected != new[i].expected) return true
            if (old[i].verdict != new[i].verdict) return true
            if (old[i].comment != new[i].comment) return true
            if (old[i].tickets != new[i].tickets) return true
            if (old[i].links != new[i].links) return true
            if (old[i].attachments != new[i].attachments) return true
        }
        return false
    }

    private fun rebuildStepCards(results: List<StepResult>) {
        stepResultsContainer.removeAll()
        stepRowCards.clear()
        results.forEachIndexed { index, result ->
            val card = StepResultCard(
                index = index,
                initial = result,
                onVerdictChange = { verdict ->
                    val list = current.stepResults.toMutableList()
                    if (index in list.indices) {
                        list[index] = list[index].copy(verdict = verdict)
                        emit(
                            current.copy(stepResults = list),
                            io.github.barsia.speqa.parser.PatchOperation.SetRunStepVerdict(index, verdict),
                        )
                    }
                },
                onCommentChange = { comment ->
                    val list = current.stepResults.toMutableList()
                    if (index in list.indices) {
                        list[index] = list[index].copy(comment = comment)
                        emit(
                            current.copy(stepResults = list),
                            io.github.barsia.speqa.parser.PatchOperation.SetRunStepComment(index, comment),
                        )
                    }
                },
            )
            card.alignmentX = Component.LEFT_ALIGNMENT
            stepRowCards.add(card)
            stepResultsContainer.add(card)
        }
        stepResultsContainer.revalidate()
        stepResultsContainer.repaint()
    }

    private inline fun syncProgrammaticUiChange(block: () -> Unit) {
        suppressProgrammaticSync = true
        try {
            block()
        } finally {
            suppressProgrammaticSync = false
        }
    }

    override fun addNotify() {
        super.addNotify()
        val bus = ApplicationManager.getApplication().messageBus.connect()
        bus.subscribe(LafManagerListener.TOPIC, LafManagerListener { onThemeChanged() })
        bus.subscribe(EditorColorsManager.TOPIC, EditorColorsListener { onThemeChanged() })
        connection = bus
    }

    override fun removeNotify() {
        connection?.disconnect()
        connection = null
        super.removeNotify()
    }

    private fun onThemeChanged() {
        applyBackground()
        SwingUtilities.updateComponentTreeUI(this)
    }

    private fun applyBackground() {
        background = EditorColorsManager.getInstance().globalScheme.defaultBackground
        isOpaque = true
    }

    /**
     * One result card per step. Displays action + expected in read-only
     * [MarkdownReadOnlyPane] form, exposes a verdict combo and a comment field.
     */
    private class StepResultCard(
        index: Int,
        initial: StepResult,
        onVerdictChange: (StepVerdict) -> Unit,
        onCommentChange: (String) -> Unit,
    ) : JPanel() {

        private var suppressProgrammaticSync = false
        private var current: StepResult = initial
        private val verdictCombo = ComboBox(StepVerdict.entries.toTypedArray()).apply {
            selectedItem = initial.verdict
            handCursor()
            addActionListener {
                if (suppressProgrammaticSync) return@addActionListener
                val picked = selectedItem as? StepVerdict ?: return@addActionListener
                if (picked != current.verdict) {
                    current = current.copy(verdict = picked)
                    onVerdictChange(picked)
                }
            }
        }
        private val commentArea = multiLineInput(
            rows = 2,
            placeholder = SpeqaBundle.message("panel.run.stepComment"),
            onChange = { text ->
                if (suppressProgrammaticSync) return@multiLineInput
                if (text != current.comment) {
                    current = current.copy(comment = text)
                    onCommentChange(text)
                }
            },
        ).also { area ->
            syncProgrammaticUiChange {
                area.text = initial.comment
            }
        }

        init {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border = JBUI.Borders.empty(6, 0)

            add(JBLabel("${(index + 1).toString().padStart(2, '0')}. ${initial.action.ifBlank { SpeqaBundle.message("run.emptyStep") }}")
                .apply { alignmentX = Component.LEFT_ALIGNMENT })

            if (initial.expected.isNotBlank()) {
                val expected = MarkdownReadOnlyPane()
                expected.setMarkdown("_${SpeqaBundle.message("label.expectedResult")}:_ ${initial.expected}")
                expected.alignmentX = Component.LEFT_ALIGNMENT
                add(expected)
            }

            val verdictStrip = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                isOpaque = false
                alignmentX = Component.LEFT_ALIGNMENT
                add(JBLabel(SpeqaBundle.message("panel.run.verdict") + ": "))
                add(verdictCombo)
            }
            add(verdictStrip)

            val scroll = JBScrollPane(commentArea).apply {
                alignmentX = Component.LEFT_ALIGNMENT
                border = JBUI.Borders.empty()
            }
            add(scroll)
        }

        private inline fun syncProgrammaticUiChange(block: () -> Unit) {
            suppressProgrammaticSync = true
            try {
                block()
            } finally {
                suppressProgrammaticSync = false
            }
        }
    }
}
