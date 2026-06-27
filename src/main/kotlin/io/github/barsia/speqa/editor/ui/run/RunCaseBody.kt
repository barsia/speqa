package io.github.barsia.speqa.editor.ui.run

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.ui.PriorityComboBox
import io.github.barsia.speqa.editor.ui.attachments.AttachmentList
import io.github.barsia.speqa.editor.ui.chips.MetadataKind
import io.github.barsia.speqa.editor.ui.chips.MetadataScope
import io.github.barsia.speqa.editor.ui.chips.TagCloud
import io.github.barsia.speqa.editor.ui.chips.editValueResult
import io.github.barsia.speqa.editor.ui.links.LinkList
import io.github.barsia.speqa.editor.ui.primitives.headerAddIconButton
import io.github.barsia.speqa.editor.ui.primitives.sectionCaption
import io.github.barsia.speqa.editor.ui.primitives.twoColumnRow
import io.github.barsia.speqa.editor.ui.steps.EditableBodyBlockSection
import io.github.barsia.speqa.editor.ui.steps.StepsSection
import io.github.barsia.speqa.editor.ui.steps.mergeBodyBlocks
import io.github.barsia.speqa.editor.ui.steps.replaceBodyBlocks
import io.github.barsia.speqa.model.DescriptionBlock
import io.github.barsia.speqa.model.PreconditionsBlock
import io.github.barsia.speqa.model.PreconditionsMarkerStyle
import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.RunCase
import io.github.barsia.speqa.model.StepResult
import io.github.barsia.speqa.model.StepVerdict
import io.github.barsia.speqa.model.TestStep
import io.github.barsia.speqa.run.TestRunSupport
import java.awt.Component
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Reusable per-case body for a single [RunCase]. Renders the case-scoped
 * editable controls — priority, environment, tags, links, attachments,
 * description, preconditions, and the run-mode steps scenario — and reports
 * every edit as an updated [RunCase] through [onCaseChange].
 *
 * Run-level fields (title, runner, run result, progress) are NOT rendered
 * here; they belong to the surrounding run header. Both the multi-case
 * [RunCaseSection] and future single-case views build on this one
 * implementation so per-case rendering is not duplicated.
 */
class RunCaseBody(
    private val project: Project,
    private val file: VirtualFile?,
    initial: RunCase,
    private val onCaseChange: (RunCase) -> Unit,
) : JPanel() {

    private var case: RunCase = initial

    /** Synthetic step wrappers whose `uid`s key edits back to their [StepResult]. */
    private var runSteps: List<TestStep> = emptyList()

    private fun emit(transform: (RunCase) -> RunCase) {
        case = transform(case)
        onCaseChange(case)
    }

    private val priorityCombo = PriorityComboBox { picked ->
        if (picked != case.priority) emit { it.copy(priority = picked) }
    }

    // Section-header `+` buttons. Declared before the clouds/lists so each can
    // be handed to its section as the external add-button target for
    // `DeleteFocusRestorer`: the sections hide their internal add button
    // (`hideAddButton = true`), so deleting the last item must restore focus to
    // the header `+` (which is in the component tree) rather than a non-showing
    // internal button. The onClick lambdas reference the later-declared
    // clouds/lists, which is safe since they only run on click.
    private val tagHeaderAdd: JComponent = headerAddIconButton(
        tooltip = SpeqaBundle.message("panel.header.addTag"),
        onClick = { tagCloud.startAdd() },
    )
    private val environmentHeaderAdd: JComponent = headerAddIconButton(
        tooltip = SpeqaBundle.message("panel.header.addEnvironment"),
        onClick = { environmentCloud.startAdd() },
    )
    private val linkHeaderAdd: JComponent = headerAddIconButton(
        tooltip = SpeqaBundle.message("panel.header.addLink"),
        onClick = { linkList.startAdd() },
    )
    private val attachmentHeaderAdd: JComponent = headerAddIconButton(
        tooltip = SpeqaBundle.message("panel.header.addAttachment"),
        onClick = { attachmentList?.startAdd() },
    )

    private val tagCloud = TagCloud(
        coloredChips = false,
        metadataScope = MetadataScope.TEST_RUNS,
        metadataKind = MetadataKind.TAG,
        metadataProject = project,
        hideAddButton = true,
        externalAddButton = tagHeaderAdd,
        onAdd = { tag -> emit { it.copy(tags = it.tags + tag) } },
        onRemove = { tag -> emit { it.copy(tags = it.tags - tag) } },
        onEditValue = { oldTag -> editTag(oldTag) },
    ).also { cloud ->
        cloud.setAllKnownTags {
            io.github.barsia.speqa.registry.SpeqaTagRegistry.getInstance(project).allTags.toSet()
        }
    }

    private val environmentCloud = TagCloud(
        coloredChips = false,
        metadataScope = MetadataScope.TEST_RUNS,
        metadataKind = MetadataKind.ENVIRONMENT,
        metadataProject = project,
        hideAddButton = true,
        externalAddButton = environmentHeaderAdd,
        onAdd = { env -> emit { it.copy(environment = it.environment + env) } },
        onRemove = { env -> emit { it.copy(environment = it.environment - env) } },
        onEditValue = { oldEnv -> editEnvironment(oldEnv) },
    ).also { cloud ->
        cloud.setAllKnownTags {
            io.github.barsia.speqa.registry.SpeqaTagRegistry.getInstance(project).allEnvironments.toSet()
        }
    }

    private val linkList = LinkList(project, hideAddButton = true, showEmptyPlaceholder = true, externalAddButton = linkHeaderAdd) { next ->
        emit { it.copy(links = next) }
    }

    private val attachmentList: AttachmentList? = if (file != null) {
        AttachmentList(project, file, hideAddButton = true, showEmptyPlaceholder = true, externalAddButton = attachmentHeaderAdd) { next ->
            emit { it.copy(attachments = next) }
        }
    } else null

    private val descriptionSection = EditableBodyBlockSection(
        project = project,
        emptyLabel = SpeqaBundle.message("placeholder.descriptionBlock"),
        onCommit = { text -> commitDescription(text) },
    )

    private val preconditionsSection = EditableBodyBlockSection(
        project = project,
        emptyLabel = SpeqaBundle.message("placeholder.preconditionsBlock"),
        onCommit = { text -> commitPreconditions(text) },
    )

    private val scrollPaneForSteps = JBScrollPane().apply {
        setViewportView(JPanel())
        isOpaque = false
        viewport.isOpaque = false
        border = JBUI.Borders.empty()
    }

    private val stepsSection = StepsSection(
        scrollPane = scrollPaneForSteps,
        project = project,
        tcFile = file,
        onStepsChange = { next: List<TestStep> ->
            // Correlate each new wrapper to its prior StepResult by uid (NOT
            // index) so reorder/duplicate/delete keep verdict and comment
            // attached. Unknown uids (new/duplicated steps) start fresh.
            val resultByUid: Map<Long, StepResult> = runSteps
                .withIndex()
                .mapNotNull { (i, s) -> case.stepResults.getOrNull(i)?.let { s.uid to it } }
                .toMap()
            val newResults = next.map { step ->
                (resultByUid[step.uid] ?: StepResult()).copy(
                    action = step.action,
                    expected = step.expected.orEmpty(),
                    tickets = step.tickets,
                    links = step.links,
                    attachments = step.attachments,
                )
            }
            runSteps = next
            emit { TestRunSupport.recomputeCaseResult(it.copy(stepResults = newResults)) }
        },
        runMode = true,
        onStepVerdictChange = { idx: Int, verdict: StepVerdict ->
            val list = case.stepResults.toMutableList()
            if (idx in list.indices) {
                list[idx] = list[idx].copy(verdict = verdict)
                emit { TestRunSupport.recomputeCaseResult(it.copy(stepResults = list)) }
            }
        },
        onStepCommentChange = { idx: Int, comment: String ->
            val list = case.stepResults.toMutableList()
            if (idx in list.indices) {
                list[idx] = list[idx].copy(comment = comment)
                emit { TestRunSupport.recomputeCaseResult(it.copy(stepResults = list)) }
            }
        },
    )

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        buildLayout()
        populateInitial()
    }

    private fun buildLayout() {
        val sectionGap = JBUI.scale(10)

        addRow(
            twoColumnRow(
                leftCaption = SpeqaBundle.message("label.priority"),
                rightCaption = "",
                leftBody = priorityCombo,
                rightBody = emptyColumn(),
            ),
        )
        add(strut(sectionGap))

        addRow(
            twoColumnRow(
                leftCaption = SpeqaBundle.message("label.environment"),
                rightCaption = SpeqaBundle.message("label.tags"),
                leftBody = environmentCloud,
                rightBody = tagCloud,
                leftHeaderAction = environmentHeaderAdd,
                rightHeaderAction = tagHeaderAdd,
            ),
        )
        add(strut(sectionGap))

        val attachmentsBody: JComponent = attachmentList ?: emptyColumn()
        addRow(
            twoColumnRow(
                leftCaption = SpeqaBundle.message("label.links"),
                rightCaption = SpeqaBundle.message("label.attachments"),
                leftBody = linkList,
                rightBody = attachmentsBody,
                leftHeaderAction = linkHeaderAdd,
                rightHeaderAction = if (attachmentList != null) attachmentHeaderAdd else null,
            ),
        )
        add(strut(sectionGap))

        addRow(captionedSection(SpeqaBundle.message("label.description"), descriptionSection))
        add(strut(sectionGap))

        addRow(captionedSection(SpeqaBundle.message("label.preconditions"), preconditionsSection))
        add(strut(sectionGap))

        addRow(captionedSection(SpeqaBundle.message("label.steps"), stepsSection))
    }

    private fun populateInitial() {
        priorityCombo.setValue(case.priority ?: Priority.NORMAL)
        environmentCloud.setTags(case.environment)
        tagCloud.setTags(case.tags)
        linkList.setLinks(case.links)
        attachmentList?.setAttachments(case.attachments)
        descriptionSection.setText(mergeBodyBlocks(case.bodyBlocks, DescriptionBlock::class.java))
        preconditionsSection.setText(mergeBodyBlocks(case.bodyBlocks, PreconditionsBlock::class.java))
        runSteps = buildRunSteps(case.stepResults)
        stepsSection.setRunStepResults(runSteps, case.stepResults)
    }

    /** Refresh every control from [newCase], diffing against the displayed state. */
    fun update(newCase: RunCase) {
        val previous = case
        case = newCase

        if (previous.priority != newCase.priority) {
            priorityCombo.setValue(newCase.priority ?: Priority.NORMAL)
        }
        if (previous.environment != newCase.environment) environmentCloud.setTags(newCase.environment)
        if (previous.tags != newCase.tags) tagCloud.setTags(newCase.tags)
        if (previous.links != newCase.links) linkList.setLinks(newCase.links)
        if (previous.attachments != newCase.attachments) attachmentList?.setAttachments(newCase.attachments)
        if (previous.bodyBlocks != newCase.bodyBlocks) {
            descriptionSection.setText(mergeBodyBlocks(newCase.bodyBlocks, DescriptionBlock::class.java))
            preconditionsSection.setText(mergeBodyBlocks(newCase.bodyBlocks, PreconditionsBlock::class.java))
        }
        if (previous.stepResults != newCase.stepResults) {
            runSteps = buildRunSteps(newCase.stepResults)
            if (previous.stepResults.size != newCase.stepResults.size) {
                stepsSection.setRunStepResults(runSteps, newCase.stepResults)
            } else {
                stepsSection.updateRunStepResultsInPlace(runSteps, newCase.stepResults)
            }
        }
    }

    private fun buildRunSteps(results: List<StepResult>): List<TestStep> {
        val prev = runSteps
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

    private fun commitDescription(text: String) {
        val next = replaceBodyBlocks(case.bodyBlocks, DescriptionBlock::class.java) { DescriptionBlock(text) }
        emit { it.copy(bodyBlocks = next) }
    }

    private fun commitPreconditions(text: String) {
        val existingStyle = case.bodyBlocks
            .filterIsInstance<PreconditionsBlock>()
            .firstOrNull()?.markerStyle
            ?: PreconditionsMarkerStyle.PRECONDITIONS
        val next = replaceBodyBlocks(case.bodyBlocks, PreconditionsBlock::class.java) {
            PreconditionsBlock(markerStyle = existingStyle, markdown = text)
        }
        emit { it.copy(bodyBlocks = next) }
    }

    private fun editTag(oldValue: String) {
        val newValue = io.github.barsia.speqa.editor.ui.primitives.showCaretEndInputDialog(
            project = project,
            title = SpeqaBundle.message("metadata.editTagTitle"),
            prompt = SpeqaBundle.message("metadata.editTagPrompt"),
            initial = oldValue,
        ) ?: return
        val updated = editValueResult(case.tags, oldValue, newValue)
        if (updated == case.tags) return
        emit { it.copy(tags = updated) }
    }

    private fun editEnvironment(oldValue: String) {
        val newValue = io.github.barsia.speqa.editor.ui.primitives.showCaretEndInputDialog(
            project = project,
            title = SpeqaBundle.message("metadata.editEnvironmentTitle"),
            prompt = SpeqaBundle.message("metadata.editEnvironmentPrompt"),
            initial = oldValue,
        ) ?: return
        val updated = editValueResult(case.environment, oldValue, newValue)
        if (updated == case.environment) return
        emit { it.copy(environment = updated) }
    }

    private fun addRow(row: JComponent) {
        row.alignmentX = Component.LEFT_ALIGNMENT
        add(row)
    }

    private fun strut(height: Int) = javax.swing.Box.createVerticalStrut(height)

    private fun emptyColumn(): JComponent = JPanel().apply { isOpaque = false }

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
}
