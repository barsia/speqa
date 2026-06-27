package io.github.barsia.speqa.editor.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.ui.JBUI
import com.intellij.openapi.ui.Messages
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.ui.chips.editValueResult
import io.github.barsia.speqa.editor.resolveTestCaseHeaderMeta
import io.github.barsia.speqa.editor.ui.attachments.AttachmentList
import io.github.barsia.speqa.editor.ui.chips.InlineEditableIdRow
import io.github.barsia.speqa.editor.ui.chips.MetadataKind
import io.github.barsia.speqa.editor.ui.chips.MetadataScope
import io.github.barsia.speqa.editor.ui.chips.TagCloud
import io.github.barsia.speqa.editor.ui.links.LinkList
import io.github.barsia.speqa.editor.ui.primitives.CommitFlash
import io.github.barsia.speqa.editor.ui.primitives.SpeqaFocusTraversalPolicy
import io.github.barsia.speqa.editor.ui.primitives.handCursor
import io.github.barsia.speqa.editor.ui.primitives.headerAddIconButton
import io.github.barsia.speqa.editor.ui.primitives.manualResultIndicator
import io.github.barsia.speqa.editor.ui.primitives.sectionCaption
import io.github.barsia.speqa.editor.ui.primitives.singleLineInput
import io.github.barsia.speqa.editor.ui.primitives.speqaIconButton
import io.github.barsia.speqa.editor.ui.primitives.twoColumnRow
import io.github.barsia.speqa.run.RunAutoTimestamps
import io.github.barsia.speqa.run.TestRunSupport
import io.github.barsia.speqa.editor.ui.steps.EditableBodyBlockSection
import io.github.barsia.speqa.editor.ui.steps.StepsSection
import io.github.barsia.speqa.editor.ui.steps.mergeBodyBlocks
import io.github.barsia.speqa.editor.ui.steps.replaceBodyBlocks
import io.github.barsia.speqa.model.DescriptionBlock
import io.github.barsia.speqa.model.PreconditionsBlock
import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.RunResult
import io.github.barsia.speqa.model.Status
import io.github.barsia.speqa.model.StepResult
import io.github.barsia.speqa.model.StepVerdict
import io.github.barsia.speqa.model.TestCase
import io.github.barsia.speqa.model.TestRun
import io.github.barsia.speqa.model.TestStep
import io.github.barsia.speqa.parser.PatchOperation
import io.github.barsia.speqa.registry.IdType
import java.awt.BorderLayout
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
 * Top-level mode selector for [TestCasePanel]. In `CASE` the panel renders
 * a `.tc.md` test case; in `RUN` the same panel renders a `.tr.md` test
 * run, swapping the status combo for a run-result combo, adding a
 * runner+progress row, and routing emits through the run callbacks.
 */
enum class PanelMode { CASE, RUN }

/**
 * Top-level Swing panel rendering a `.tc.md` test-case preview or a `.tr.md`
 * test-run preview (selected via [mode]).
 *
 * Layout contract is documented in the spec (`docs/specs/2026-04-06-speqa-design.md`
 * §15e "Swing panel layout"). The caller wraps the panel in a `JBScrollPane`.
 */
class TestCasePanel(
    private val project: Project?,
    private val file: VirtualFile?,
    private val mode: PanelMode = PanelMode.CASE,
    private val onChange: (TestCase) -> Unit,
    private val onPatch: ((TestCase, PatchOperation) -> Unit)? = null,
    private val onRun: () -> Unit = {},
    private val onHeaderStateChanged: (idPrefix: String, id: String, title: String) -> Unit = { _, _, _ -> },
    private val onRunChange: ((TestRun) -> Unit)? = null,
) : JPanel(), Scrollable {

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
    override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int =
        JBUI.scale(16)
    override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int =
        if (orientation == SwingConstants.VERTICAL) visibleRect.height else visibleRect.width
    override fun getScrollableTracksViewportWidth(): Boolean = true
    override fun getScrollableTracksViewportHeight(): Boolean = false

    private var currentCase: TestCase = TestCase()
    // Tracks what the UI actually displays, updated only inside updateFrom/updateFromRun.
    // Kept separate from currentCase so emit() can advance currentCase optimistically
    // while updateFrom() still sees the correct previous-vs-next diff for UI components
    // that don't update themselves immediately (tag cloud, link list, attachments, etc.).
    private var displayedCase: TestCase = TestCase()
    private var currentRun: TestRun = TestRun()
    private var displayedRun: TestRun = TestRun()
    private var suppressProgrammaticSync: Boolean = false

    private fun emit(updated: TestCase, op: PatchOperation? = null) {
        currentCase = updated
        val patch = onPatch
        if (op != null && patch != null) patch(updated, op) else onChange(updated)
    }

    private fun emitRun(updated: TestRun) {
        currentRun = updated
        onRunChange?.invoke(updated)
    }

    // --- UI leaves ---------------------------------------------------------
    private val idRow = InlineEditableIdRow(
        if (mode == PanelMode.RUN) IdType.TEST_RUN else IdType.TEST_CASE,
    ) { newId ->
        if (mode == PanelMode.RUN) {
            emitRun(currentRun.copy(id = newId))
        } else {
            emit(
                currentCase.copy(id = newId),
                PatchOperation.SetFrontmatterField("id", newId.toString()),
            )
        }
    }

    private val titleRow = InlineEditableTitleRow(
        initialTitle = "",
        placeholder = SpeqaBundle.message(
            if (mode == PanelMode.RUN) "panel.run.title.placeholder" else "panel.title.placeholder",
        ),
        onCommit = { newTitle ->
            if (mode == PanelMode.RUN) {
                if (newTitle != currentRun.title) {
                    emitRun(currentRun.copy(title = newTitle))
                }
            } else {
                if (newTitle != currentCase.title) {
                    emit(
                        currentCase.copy(title = newTitle),
                        PatchOperation.SetFrontmatterField("title", newTitle),
                    )
                }
            }
        },
    )

    // RUN-mode-only widgets created before they are referenced by the header
    // utility row / layout.
    private val runResultCombo: ComboBox<RunResult>? = if (mode == PanelMode.RUN) {
        ComboBox(RunResult.entries.toTypedArray()).apply {
            toolTipText = SpeqaBundle.message("panel.run.verdict")
            handCursor()
            renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean,
                ): Component {
                    val text = (value as? RunResult)
                        ?.label
                        ?.replace('_', ' ')
                        ?.replaceFirstChar { it.uppercase() }
                        ?: ""
                    return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus)
                }
            }
            addActionListener {
                if (suppressProgrammaticSync) return@addActionListener
                val picked = selectedItem as? RunResult ?: return@addActionListener
                if (picked != currentRun.result) {
                    val manual = picked != RunResult.NOT_STARTED
                    val updated = RunAutoTimestamps.apply(
                        currentRun.copy(result = picked),
                        manualResultOverride = manual,
                    )
                    emitRun(updated)
                }
            }
        }
    } else null

    /** Icon-only marker next to the run-result combo, shown while the run result was set manually. */
    private val runManualIndicator: JBLabel? = if (mode == PanelMode.RUN) {
        manualResultIndicator(SpeqaBundle.message("runResult.manual.tooltip")).apply { isVisible = false }
    } else null

    private val runnerField: JBTextField? = if (mode == PanelMode.RUN) {
        singleLineInput(
            placeholder = SpeqaBundle.message("placeholder.runner"),
            onChange = { text ->
                if (suppressProgrammaticSync) return@singleLineInput
                if (text != currentRun.runner) {
                    emitRun(currentRun.copy(runner = text))
                }
            },
        )
    } else null

    private val progressLabel: JBLabel? = if (mode == PanelMode.RUN) {
        JBLabel(SpeqaBundle.message("panel.run.progress.notStarted"))
    } else null

    private val headerUtilityRow: HeaderUtilityRow = if (mode == PanelMode.RUN) {
        HeaderUtilityRow.forTestRun(
            idChip = idRow,
            createdLabel = "",
            startedLabel = "",
            finishedLabel = "",
            // No Run button in run mode - the trailing slot is a no-op filler.
            trailing = emptyHeaderTrailing(),
        )
    } else {
        HeaderUtilityRow.forTestCase(
            idChip = idRow,
            createdLabel = "",
            updatedLabel = "",
            onRun = onRun,
        )
    }

    private val priorityCombo = PriorityComboBox { picked ->
        if (mode == PanelMode.RUN) {
            if (picked != currentRun.priority) {
                emitRun(currentRun.withSingleCase { it.copy(priority = picked) })
            }
        } else {
            if (picked != currentCase.priority) {
                emit(
                    currentCase.copy(priority = picked),
                    PatchOperation.SetFrontmatterField("priority", picked.label),
                )
            }
        }
    }

    private val statusCombo: StatusComboBox? = if (mode == PanelMode.CASE) {
        StatusComboBox { picked ->
            if (picked != currentCase.status) {
                emit(
                    currentCase.copy(status = picked),
                    PatchOperation.SetFrontmatterField("status", picked.label),
                )
            }
        }
    } else null

    private val tagCloud = TagCloud(
        coloredChips = false,
        metadataScope = if (mode == PanelMode.RUN) MetadataScope.TEST_RUNS else MetadataScope.TEST_CASES,
        metadataKind = MetadataKind.TAG,
        metadataProject = project,
        hideAddButton = true,
        onAdd = { tag ->
            if (mode == PanelMode.RUN) {
                val next = currentRun.tags + tag
                emitRun(currentRun.withSingleCase { it.copy(tags = next) })
            } else {
                val next = (currentCase.tags ?: emptyList()) + tag
                emit(currentCase.copy(tags = next), PatchOperation.SetFrontmatterList("tags", next))
            }
        },
        onRemove = { tag ->
            if (mode == PanelMode.RUN) {
                val next = currentRun.tags - tag
                emitRun(currentRun.withSingleCase { it.copy(tags = next) })
            } else {
                val next = (currentCase.tags ?: emptyList()) - tag
                emit(currentCase.copy(tags = next), PatchOperation.SetFrontmatterList("tags", next))
            }
        },
        onEditValue = { oldTag -> editTag(oldTag) },
    ).also { cloud ->
        if (project != null) {
            cloud.setAllKnownTags {
                io.github.barsia.speqa.registry.SpeqaTagRegistry.getInstance(project).allTags.toSet()
            }
        }
    }

    private val environmentCloud = TagCloud(
        coloredChips = false,
        metadataScope = if (mode == PanelMode.RUN) MetadataScope.TEST_RUNS else MetadataScope.TEST_CASES,
        metadataKind = MetadataKind.ENVIRONMENT,
        metadataProject = project,
        hideAddButton = true,
        onAdd = { env ->
            if (mode == PanelMode.RUN) {
                val next = currentRun.environment + env
                emitRun(currentRun.withSingleCase { it.copy(environment = next) })
            } else {
                val next = (currentCase.environment ?: emptyList()) + env
                emit(
                    currentCase.copy(environment = next),
                    PatchOperation.SetFrontmatterList("environment", next),
                )
            }
        },
        onRemove = { env ->
            if (mode == PanelMode.RUN) {
                val next = currentRun.environment - env
                emitRun(currentRun.withSingleCase { it.copy(environment = next) })
            } else {
                val next = (currentCase.environment ?: emptyList()) - env
                emit(
                    currentCase.copy(environment = next),
                    PatchOperation.SetFrontmatterList("environment", next),
                )
            }
        },
        onEditValue = { oldEnv -> editEnvironment(oldEnv) },
    ).also { cloud ->
        if (project != null) {
            cloud.setAllKnownTags {
                io.github.barsia.speqa.registry.SpeqaTagRegistry.getInstance(project).allEnvironments.toSet()
            }
        }
    }

    private val attachmentList: AttachmentList? = if (project != null && file != null) {
        AttachmentList(project, file, hideAddButton = true, showEmptyPlaceholder = true) { next ->
            if (mode == PanelMode.RUN) {
                emitRun(currentRun.withSingleCase { it.copy(attachments = next) })
            } else {
                emit(currentCase.copy(attachments = next), PatchOperation.SetAttachments(next))
            }
        }
    } else null

    private val linkList = LinkList(project, hideAddButton = true, showEmptyPlaceholder = true) { next ->
        if (mode == PanelMode.RUN) {
            emitRun(currentRun.withSingleCase { it.copy(links = next) })
        } else {
            emit(currentCase.copy(links = next), PatchOperation.SetLinks(next))
        }
    }

    private val descriptionSection = EditableBodyBlockSection(
        project = requireNotNull(this.project) { "TestCasePanel requires a real Project for inline markdown editors" },
        emptyLabel = SpeqaBundle.message("placeholder.descriptionBlock"),
        onCommit = { text -> commitDescription(text) },
    )
    private val preconditionsSection = EditableBodyBlockSection(
        project = requireNotNull(this.project) { "TestCasePanel requires a real Project for inline markdown editors" },
        emptyLabel = SpeqaBundle.message("placeholder.preconditionsBlock"),
        onCommit = { text -> commitPreconditions(text) },
    )

    private val scrollPaneForSteps = JBScrollPane().apply {
        setViewportView(JPanel())
        isOpaque = false
        viewport.isOpaque = false
        border = JBUI.Borders.empty()
    }

    internal val stepsSection = StepsSection(
        scrollPane = scrollPaneForSteps,
        project = project,
        tcFile = file,
        onStepsChange = { next: List<TestStep> ->
            val prevSteps = currentCase.steps
            currentCase = currentCase.copy(steps = next)
            if (mode == PanelMode.RUN) {
                // Map each new TestStep to its prior StepResult by uid (NOT
                // by index) so reorder/duplicate/delete keep verdict and
                // comment attached to the original step. Newly added or
                // duplicated steps (unknown uid) start with a fresh
                // StepResult — verdict=NONE, comment="".
                val resultByUid: Map<Long, StepResult> = prevSteps
                    .withIndex()
                    .mapNotNull { (i, s) ->
                        currentRun.stepResults.getOrNull(i)?.let { s.uid to it }
                    }
                    .toMap()
                val newResults = next.map { step ->
                    val base = resultByUid[step.uid] ?: StepResult()
                    base.copy(
                        action = step.action,
                        expected = step.expected.orEmpty(),
                        tickets = step.tickets,
                        links = step.links,
                        attachments = step.attachments,
                    )
                }
                emitRun(currentRun.withSingleCase { it.copy(stepResults = newResults) })
            } else if (onPatch == null) {
                onChange(currentCase)
            }
        },
        onStepPatch = when (mode) {
            PanelMode.CASE -> onPatch?.let { sink -> { op -> sink(currentCase, op) } }
            PanelMode.RUN -> null
        },
        runMode = (mode == PanelMode.RUN),
        onStepVerdictChange = if (mode == PanelMode.RUN) {
            { idx: Int, verdict: StepVerdict ->
                val list = currentRun.stepResults.toMutableList()
                if (idx in list.indices) {
                    list[idx] = list[idx].copy(verdict = verdict)
                    val updated = RunAutoTimestamps.apply(currentRun.withSingleCase { it.copy(stepResults = list) })
                    emitRun(updated)
                }
            }
        } else null,
        onStepCommentChange = if (mode == PanelMode.RUN) {
            { idx: Int, comment: String ->
                val list = currentRun.stepResults.toMutableList()
                if (idx in list.indices) {
                    list[idx] = list[idx].copy(comment = comment)
                    emitRun(currentRun.withSingleCase { it.copy(stepResults = list) })
                }
            }
        } else null,
    )

    private var connection: MessageBusConnection? = null
    private var firstUpdate: Boolean = true

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(12)
        applyBackground()
        buildLayout()
        focusTraversalPolicy = SpeqaFocusTraversalPolicy()
        isFocusCycleRoot = true

        // Steal focus when the user clicks an empty area of the preview.
        // Without this an inline-edit field (e.g. the ticket id input)
        // keeps focus indefinitely because JPanel is not focusable by
        // default — clicking outside the input does not move focus
        // anywhere, so its focusLost handler never fires.
        isFocusable = true
        addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mousePressed(e: java.awt.event.MouseEvent) {
                requestFocusInWindow()
            }
        })
    }

    /** Right-aligned run-action row (RUN mode): currently the Reset-results button. */
    private fun buildRunActionsRow(): JPanel = JPanel(BorderLayout()).apply {
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        val actions = javax.swing.Box.createHorizontalBox().apply {
            add(speqaIconButton(AllIcons.General.Reset, SpeqaBundle.message("panel.run.reset")) { confirmAndReset() })
        }
        add(actions, BorderLayout.EAST)
    }

    private fun confirmAndReset() {
        val confirmed = Messages.showYesNoDialog(
            project,
            SpeqaBundle.message("panel.run.reset.confirm.message"),
            SpeqaBundle.message("panel.run.reset.confirm.title"),
            Messages.getQuestionIcon(),
        ) == Messages.YES
        if (confirmed) emitRun(TestRunSupport.resetResults(currentRun))
    }

    private fun buildLayout() {
        val sectionGap = JBUI.scale(10)

        headerUtilityRow.alignmentX = Component.LEFT_ALIGNMENT
        add(headerUtilityRow)
        add(javax.swing.Box.createVerticalStrut(JBUI.scale(6)))

        titleRow.alignmentX = Component.LEFT_ALIGNMENT
        add(titleRow)
        add(javax.swing.Box.createVerticalStrut(sectionGap))

        if (mode == PanelMode.RUN) {
            val combo = requireNotNull(runResultCombo) { "runResultCombo must be non-null in RUN mode" }
            val indicator = requireNotNull(runManualIndicator) { "runManualIndicator must be non-null in RUN mode" }
            // BorderLayout keeps the combo filling the column width (CENTER) with the manual
            // indicator pinned to its right (EAST).
            val resultBody = JPanel(BorderLayout()).apply {
                isOpaque = false
                add(combo, BorderLayout.CENTER)
                add(indicator.apply { border = JBUI.Borders.emptyLeft(JBUI.scale(6)) }, BorderLayout.EAST)
            }
            // Field order: Result | Progress, then Runner | Priority.
            val resultProgressRow = twoColumnRow(
                leftCaption = SpeqaBundle.message("label.runResult"),
                rightCaption = SpeqaBundle.message("panel.run.progress"),
                leftBody = resultBody,
                rightBody = requireNotNull(progressLabel) { "progressLabel must be non-null in RUN mode" },
            )
            resultProgressRow.alignmentX = Component.LEFT_ALIGNMENT
            add(resultProgressRow)
            add(javax.swing.Box.createVerticalStrut(sectionGap))

            val runnerPriorityRow = twoColumnRow(
                leftCaption = SpeqaBundle.message("panel.run.runner"),
                rightCaption = SpeqaBundle.message("label.priority"),
                leftBody = requireNotNull(runnerField) { "runnerField must be non-null in RUN mode" },
                rightBody = priorityCombo,
            )
            runnerPriorityRow.alignmentX = Component.LEFT_ALIGNMENT
            add(runnerPriorityRow)
            add(javax.swing.Box.createVerticalStrut(sectionGap))

            add(buildRunActionsRow())
            add(javax.swing.Box.createVerticalStrut(sectionGap))
        } else {
            val priStatRow = twoColumnRow(
                leftCaption = SpeqaBundle.message("label.priority"),
                rightCaption = SpeqaBundle.message("label.status"),
                leftBody = priorityCombo,
                rightBody = requireNotNull(statusCombo) { "statusCombo must be non-null in CASE mode" },
            )
            priStatRow.alignmentX = Component.LEFT_ALIGNMENT
            add(priStatRow)
            add(javax.swing.Box.createVerticalStrut(sectionGap))
        }

        val envTagRow = twoColumnRow(
            leftCaption = SpeqaBundle.message("label.environment"),
            rightCaption = SpeqaBundle.message("label.tags"),
            leftBody = environmentCloud,
            rightBody = tagCloud,
            leftHeaderAction = headerAddIconButton(
                tooltip = SpeqaBundle.message("panel.header.addEnvironment"),
                onClick = { environmentCloud.startAdd() },
            ),
            rightHeaderAction = headerAddIconButton(
                tooltip = SpeqaBundle.message("panel.header.addTag"),
                onClick = { tagCloud.startAdd() },
            ),
        )
        envTagRow.alignmentX = Component.LEFT_ALIGNMENT
        add(envTagRow)
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

        add(captionedSection(SpeqaBundle.message("label.description"), descriptionSection))
        add(javax.swing.Box.createVerticalStrut(sectionGap))

        add(captionedSection(SpeqaBundle.message("label.preconditions"), preconditionsSection))
        add(javax.swing.Box.createVerticalStrut(sectionGap))

        add(captionedSection(SpeqaBundle.message("label.steps"), stepsSection))
    }

    private fun emptyColumn(): JComponent {
        val p = JPanel()
        p.isOpaque = false
        return p
    }

    /** Empty filler for `HeaderUtilityRow.forTestRun`'s non-nullable `trailing`. */
    private fun emptyHeaderTrailing(): JComponent = JPanel().apply {
        isOpaque = false
        preferredSize = Dimension(0, 0)
    }

    private fun captionedSection(caption: String, body: JComponent): JPanel {
        val wrapper = JPanel()
        wrapper.layout = BoxLayout(wrapper, BoxLayout.Y_AXIS)
        wrapper.isOpaque = false
        wrapper.alignmentX = Component.LEFT_ALIGNMENT
        val cap = sectionCaption(caption)
        cap.alignmentX = Component.LEFT_ALIGNMENT
        wrapper.add(cap)
        wrapper.add(javax.swing.Box.createVerticalStrut(JBUI.scale(4)))
        body.alignmentX = Component.LEFT_ALIGNMENT
        wrapper.add(body)
        return wrapper
    }

    fun updateFrom(case: TestCase, flash: Boolean = false, forceFocusedTextSync: Boolean = false) {
        val previous = displayedCase
        displayedCase = case
        currentCase = case
        val shouldFlash = flash && !firstUpdate
        firstUpdate = false

        if (previous.title != case.title) {
            titleRow.setTitle(case.title, flash = shouldFlash)
        }
        idRow.update(case.id, nextFreeId = (case.id ?: 0) + 1, isDuplicate = false)
        onHeaderStateChanged("TC-", case.id?.toString() ?: "", case.title)

        // Header dates — require project + file to resolve.
        val project = this.project
        val file = this.file
        if (project != null && file != null) {
            val meta = resolveTestCaseHeaderMeta(project, file)
            headerUtilityRow.setDates(
                leftText = meta.createdLabel,
                rightText = meta.updatedLabel,
            )
        }

        if (previous.priority != case.priority) {
            priorityCombo.setValue(case.priority ?: Priority.NORMAL)
            if (shouldFlash) CommitFlash.flash(priorityCombo)
        }
        if (previous.status != case.status) {
            statusCombo?.let { combo ->
                combo.setValue(case.status ?: Status.DRAFT)
                if (shouldFlash) CommitFlash.flash(combo)
            }
        }
        if (previous.tags != case.tags) {
            tagCloud.setTags(case.tags ?: emptyList())
            if (shouldFlash) CommitFlash.flash(tagCloud)
        }
        if (previous.environment != case.environment) {
            environmentCloud.setTags(case.environment ?: emptyList())
            if (shouldFlash) CommitFlash.flash(environmentCloud)
        }
        if (previous.attachments != case.attachments) {
            attachmentList?.setAttachments(case.attachments)
            if (shouldFlash) attachmentList?.let { CommitFlash.flash(it) }
        }
        if (previous.links != case.links) {
            linkList.setLinks(case.links)
            if (shouldFlash) CommitFlash.flash(linkList)
        }

        if (previous.bodyBlocks != case.bodyBlocks) {
            val prevDescription = mergeBodyBlocks(previous.bodyBlocks, DescriptionBlock::class.java)
            val newDescription = mergeBodyBlocks(case.bodyBlocks, DescriptionBlock::class.java)
            if (prevDescription != newDescription) {
                descriptionSection.setText(newDescription, forceFocusedTextSync = forceFocusedTextSync)
                if (shouldFlash) CommitFlash.flash(descriptionSection.flashTarget())
            }
            val prevPreconditions = mergeBodyBlocks(previous.bodyBlocks, PreconditionsBlock::class.java)
            val newPreconditions = mergeBodyBlocks(case.bodyBlocks, PreconditionsBlock::class.java)
            if (prevPreconditions != newPreconditions) {
                preconditionsSection.setText(newPreconditions, forceFocusedTextSync = forceFocusedTextSync)
                if (shouldFlash) CommitFlash.flash(preconditionsSection.flashTarget())
            }
        }

        if (previous.steps != case.steps) {
            val append = isSingleStepAppend(previous.steps, case.steps)
            val structural = stepsStructurallyChanged(previous.steps, case.steps)
            if (append) {
                stepsSection.updateStepsInPlace(case.steps, forceFocusedTextSync = forceFocusedTextSync)
            } else if (structural) {
                stepsSection.setSteps(case.steps)
                if (shouldFlash) CommitFlash.flash(stepsSection)
            } else {
                stepsSection.updateStepsInPlace(case.steps, forceFocusedTextSync = forceFocusedTextSync)
            }
        }
    }

    fun updateFromRun(run: TestRun, flash: Boolean = false, forceFocusedTextSync: Boolean = false) {
        val previous = displayedRun
        displayedRun = run
        currentRun = run
        val shouldFlash = flash && !firstUpdate
        firstUpdate = false

        if (previous.title != run.title) titleRow.setTitle(run.title, flash = shouldFlash)
        idRow.update(run.id, nextFreeId = (run.id ?: 0) + 1, isDuplicate = false)
        onHeaderStateChanged("TR-", run.id?.toString() ?: "", run.title)

        val fmt = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
        val createdText = if (project != null && file != null) {
            resolveTestCaseHeaderMeta(project, file).createdLabel
        } else ""
        headerUtilityRow.setRunDates(
            createdText = createdText,
            startedText = run.startedAt?.format(fmt).orEmpty(),
            finishedText = run.finishedAt?.format(fmt).orEmpty(),
        )

        if (previous.priority != run.priority) {
            priorityCombo.setValue(run.priority ?: Priority.NORMAL)
            if (shouldFlash) CommitFlash.flash(priorityCombo)
        }
        if (previous.result != run.result) {
            runResultCombo?.let { combo ->
                syncProgrammaticUiChange {
                    if (combo.selectedItem != run.result) combo.selectedItem = run.result
                }
                if (shouldFlash) CommitFlash.flash(combo)
            }
        }
        runManualIndicator?.isVisible = run.manualResult
        if (previous.runner != run.runner) {
            runnerField?.let { field ->
                syncProgrammaticUiChange {
                    if (field.text != run.runner) field.text = run.runner
                }
                if (shouldFlash) CommitFlash.flash(field)
            }
        }
        if (previous.tags != run.tags) {
            tagCloud.setTags(run.tags)
            if (shouldFlash) CommitFlash.flash(tagCloud)
        }
        if (previous.environment != run.environment) {
            environmentCloud.setTags(run.environment)
            if (shouldFlash) CommitFlash.flash(environmentCloud)
        }
        if (previous.attachments != run.attachments) {
            attachmentList?.setAttachments(run.attachments)
            if (shouldFlash) attachmentList?.let { CommitFlash.flash(it) }
        }
        if (previous.links != run.links) {
            linkList.setLinks(run.links)
            if (shouldFlash) CommitFlash.flash(linkList)
        }
        if (previous.bodyBlocks != run.bodyBlocks) {
            val prevD = mergeBodyBlocks(previous.bodyBlocks, DescriptionBlock::class.java)
            val newD = mergeBodyBlocks(run.bodyBlocks, DescriptionBlock::class.java)
            if (prevD != newD) descriptionSection.setText(newD, forceFocusedTextSync = forceFocusedTextSync)
            val prevP = mergeBodyBlocks(previous.bodyBlocks, PreconditionsBlock::class.java)
            val newP = mergeBodyBlocks(run.bodyBlocks, PreconditionsBlock::class.java)
            if (prevP != newP) preconditionsSection.setText(newP, forceFocusedTextSync = forceFocusedTextSync)
        }
        if (previous.stepResults != run.stepResults) {
            // Build synthetic TestStep wrappers with stable uids that both
            // this panel (for uid-keyed verdict/comment preservation in
            // onStepsChange) and StepsSection share. Position-preserving uid
            // reuse keeps existing rows stable; new rows get fresh uids.
            val runSteps = buildRunSteps(run.stepResults)
            currentCase = currentCase.copy(steps = runSteps)
            if (runStepsStructurallyChanged(previous.stepResults, run.stepResults)) {
                stepsSection.setRunStepResults(runSteps, run.stepResults)
                if (shouldFlash) CommitFlash.flash(stepsSection)
            } else {
                stepsSection.updateRunStepResultsInPlace(
                    runSteps,
                    run.stepResults,
                    forceFocusedTextSync = forceFocusedTextSync,
                )
            }
        }

        progressLabel?.text = run.progressDisplayText()
    }

    /**
     * Synthesize `TestStep` wrappers for run-mode rendering. The `uid`s here
     * are the identity keys used by [onStepsChange] to correlate edited steps
     * back to their existing `StepResult` (so `verdict` / `comment` stay
     * attached). Uids are preserved by position from the previous wrappers;
     * any extra results at the tail get fresh uids.
     */
    private fun buildRunSteps(results: List<StepResult>): List<TestStep> {
        val prev = currentCase.steps
        return results.mapIndexed { i, r ->
            TestStep(
                action = r.action,
                expected = r.expected.ifBlank { null },
                tickets = r.tickets,
                links = r.links,
                attachments = r.attachments,
                uid = prev.getOrNull(i)?.uid ?: TestStep.nextUid(),
            )
        }
    }

    private fun runStepsStructurallyChanged(old: List<StepResult>, new: List<StepResult>): Boolean =
        Companion.runStepsStructurallyChanged(old, new)

    private fun TestRun.progressDisplayText(): String {
        val total = stepResults.size
        val completed = stepResults.count { it.verdict != StepVerdict.NONE }
        return if (result == RunResult.NOT_STARTED && completed == 0) {
            SpeqaBundle.message("panel.run.progress.notStarted")
        } else {
            SpeqaBundle.message("panel.run.progress.steps", completed, total)
        }
    }

    private fun stepsStructurallyChanged(old: List<TestStep>, new: List<TestStep>): Boolean =
        Companion.stepsStructurallyChanged(old, new)

    companion object {
        internal fun isSingleStepAppend(old: List<TestStep>, new: List<TestStep>): Boolean =
            new.size == old.size + 1 && old.indices.all { index -> old[index] == new[index] }

        internal fun stepsStructurallyChanged(old: List<TestStep>, new: List<TestStep>): Boolean =
            old.size != new.size

        internal fun runStepsStructurallyChanged(old: List<StepResult>, new: List<StepResult>): Boolean =
            old.size != new.size
    }

    private fun commitDescription(text: String) {
        if (mode == PanelMode.RUN) {
            val next = replaceBodyBlocks(currentRun.bodyBlocks, DescriptionBlock::class.java) {
                DescriptionBlock(text)
            }
            emitRun(currentRun.withSingleCase { it.copy(bodyBlocks = next) })
        } else {
            val next = replaceBodyBlocks(currentCase.bodyBlocks, DescriptionBlock::class.java) {
                DescriptionBlock(text)
            }
            emit(currentCase.copy(bodyBlocks = next), PatchOperation.SetDescription(text))
        }
    }

    private fun commitPreconditions(text: String) {
        if (mode == PanelMode.RUN) {
            val existingStyle = currentRun.bodyBlocks
                .filterIsInstance<PreconditionsBlock>()
                .firstOrNull()?.markerStyle
                ?: io.github.barsia.speqa.model.PreconditionsMarkerStyle.PRECONDITIONS
            val next = replaceBodyBlocks(currentRun.bodyBlocks, PreconditionsBlock::class.java) {
                PreconditionsBlock(markerStyle = existingStyle, markdown = text)
            }
            emitRun(currentRun.withSingleCase { it.copy(bodyBlocks = next) })
        } else {
            val existingStyle = currentCase.bodyBlocks
                .filterIsInstance<PreconditionsBlock>()
                .firstOrNull()?.markerStyle
                ?: io.github.barsia.speqa.model.PreconditionsMarkerStyle.PRECONDITIONS
            val next = replaceBodyBlocks(currentCase.bodyBlocks, PreconditionsBlock::class.java) {
                PreconditionsBlock(markerStyle = existingStyle, markdown = text)
            }
            emit(currentCase.copy(bodyBlocks = next), PatchOperation.SetPreconditions(existingStyle, text))
        }
    }

    private fun editTag(oldValue: String) {
        val project = this.project ?: return
        val newValue = io.github.barsia.speqa.editor.ui.primitives.showCaretEndInputDialog(
            project = project,
            title = SpeqaBundle.message("metadata.editTagTitle"),
            prompt = SpeqaBundle.message("metadata.editTagPrompt"),
            initial = oldValue,
        ) ?: return
        if (mode == PanelMode.RUN) {
            val updated = editValueResult(currentRun.tags, oldValue, newValue)
            if (updated == currentRun.tags) return
            emitRun(currentRun.withSingleCase { it.copy(tags = updated) })
        } else {
            val updated = editValueResult(currentCase.tags ?: emptyList(), oldValue, newValue)
            if (updated == (currentCase.tags ?: emptyList<String>())) return
            emit(currentCase.copy(tags = updated), PatchOperation.SetFrontmatterList("tags", updated))
        }
    }

    private fun editEnvironment(oldValue: String) {
        val project = this.project ?: return
        val newValue = io.github.barsia.speqa.editor.ui.primitives.showCaretEndInputDialog(
            project = project,
            title = SpeqaBundle.message("metadata.editEnvironmentTitle"),
            prompt = SpeqaBundle.message("metadata.editEnvironmentPrompt"),
            initial = oldValue,
        ) ?: return
        if (mode == PanelMode.RUN) {
            val updated = editValueResult(currentRun.environment, oldValue, newValue)
            if (updated == currentRun.environment) return
            emitRun(currentRun.withSingleCase { it.copy(environment = updated) })
        } else {
            val updated = editValueResult(currentCase.environment ?: emptyList(), oldValue, newValue)
            if (updated == (currentCase.environment ?: emptyList<String>())) return
            emit(
                currentCase.copy(environment = updated),
                PatchOperation.SetFrontmatterList("environment", updated),
            )
        }
    }

    private inline fun syncProgrammaticUiChange(block: () -> Unit) {
        suppressProgrammaticSync = true
        try {
            block()
        } finally {
            suppressProgrammaticSync = false
        }
    }

    /**
     * Returns the y-coordinate (in this panel's coordinate space) of the
     * bottom of the title row, used by `FloatingHeaderHost` to decide when to
     * slide its floating bar in. Returns 0 if the title row has not been laid
     * out yet.
     */
    fun titleRowBottomY(): Int {
        if (titleRow.height <= 0) return 0
        return titleRow.y + titleRow.height
    }

    fun scrollLastStepIntoView() {
        SwingUtilities.invokeLater {
            val count = stepsSection.componentCount
            val last = (0 until count).map { stepsSection.getComponent(it) }
                .lastOrNull { it.javaClass.simpleName == "StepCard" } ?: return@invokeLater
            scrollRectToVisible(last.bounds)
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

    fun refreshTheme() {
        SwingUtilities.updateComponentTreeUI(this)
        applyBackground()
        revalidate()
        repaint()
    }

    private fun onThemeChanged() {
        refreshTheme()
    }

    private fun applyBackground() {
        val bg = EditorColorsManager.getInstance().let { manager ->
            (manager.activeVisibleScheme ?: manager.globalScheme).defaultBackground
        }
        background = bg
        isOpaque = true
    }
}
