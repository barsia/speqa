package io.github.barsia.speqa.editor.ui

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.resolveTestCaseHeaderMeta
import io.github.barsia.speqa.editor.ui.attachments.AttachmentList
import io.github.barsia.speqa.editor.ui.chips.InlineEditableIdRow
import io.github.barsia.speqa.editor.ui.chips.MetadataKind
import io.github.barsia.speqa.editor.ui.chips.MetadataScope
import io.github.barsia.speqa.editor.ui.chips.TagCloud
import io.github.barsia.speqa.editor.ui.links.LinkList
import io.github.barsia.speqa.editor.ui.primitives.CommitFlash
import io.github.barsia.speqa.editor.ui.primitives.SpeqaFocusTraversalPolicy
import io.github.barsia.speqa.editor.ui.primitives.headerAddIconButton
import io.github.barsia.speqa.editor.ui.primitives.sectionCaption
import io.github.barsia.speqa.editor.ui.primitives.twoColumnRow
import io.github.barsia.speqa.editor.ui.steps.EditableBodyBlockSection
import io.github.barsia.speqa.editor.ui.steps.StepsSection
import io.github.barsia.speqa.editor.ui.steps.mergeBodyBlocks
import io.github.barsia.speqa.editor.ui.steps.replaceBodyBlocks
import io.github.barsia.speqa.model.DescriptionBlock
import io.github.barsia.speqa.model.PreconditionsBlock
import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.Status
import io.github.barsia.speqa.model.TestCase
import io.github.barsia.speqa.model.TestStep
import io.github.barsia.speqa.parser.PatchOperation
import io.github.barsia.speqa.registry.IdType
import java.awt.Component
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Scrollable
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

/**
 * Top-level Swing panel rendering a `.tc.md` test-case preview.
 *
 * Layout contract is documented in the spec (`docs/specs/2026-04-06-speqa-design.md`
 * §15e "Swing panel layout"). The caller wraps the panel in a `JBScrollPane`.
 */
class TestCasePanel(
    private val project: Project?,
    private val file: VirtualFile?,
    private val onChange: (TestCase) -> Unit,
    private val onPatch: ((TestCase, PatchOperation) -> Unit)? = null,
    private val onRun: () -> Unit = {},
) : JPanel(), Scrollable {

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
    override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int =
        JBUI.scale(16)
    override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int =
        if (orientation == SwingConstants.VERTICAL) visibleRect.height else visibleRect.width
    override fun getScrollableTracksViewportWidth(): Boolean = true
    override fun getScrollableTracksViewportHeight(): Boolean = false

    private var current: TestCase = TestCase()

    private fun emit(updated: TestCase, op: PatchOperation? = null) {
        current = updated
        val patch = onPatch
        if (op != null && patch != null) patch(updated, op) else onChange(updated)
    }

    // --- UI leaves ---------------------------------------------------------
    private val idRow = InlineEditableIdRow(IdType.TEST_CASE) { newId ->
        emit(
            current.copy(id = newId),
            PatchOperation.SetFrontmatterField("id", newId.toString()),
        )
    }

    private val titleRow = InlineEditableTitleRow(
        initialTitle = "",
        placeholder = SpeqaBundle.message("panel.title.placeholder"),
        onCommit = { newTitle ->
            if (newTitle != current.title) {
                emit(
                    current.copy(title = newTitle),
                    PatchOperation.SetFrontmatterField("title", newTitle),
                )
            }
        },
    )

    private val headerUtilityRow: HeaderUtilityRow =
        HeaderUtilityRow.forTestCase(
            idChip = idRow,
            createdLabel = "",
            updatedLabel = "",
            onRun = onRun,
        )

    private val priorityCombo = PriorityComboBox { picked ->
        if (picked != current.priority) {
            emit(
                current.copy(priority = picked),
                PatchOperation.SetFrontmatterField("priority", picked.label),
            )
        }
    }

    private val statusCombo = StatusComboBox { picked ->
        if (picked != current.status) {
            emit(
                current.copy(status = picked),
                PatchOperation.SetFrontmatterField("status", picked.label),
            )
        }
    }

    private val tagCloud = TagCloud(
        coloredChips = true,
        metadataScope = MetadataScope.TEST_CASES,
        metadataKind = MetadataKind.TAG,
        metadataProject = project,
        hideAddButton = true,
        onAdd = { tag ->
            val next = (current.tags ?: emptyList()) + tag
            emit(current.copy(tags = next), PatchOperation.SetFrontmatterList("tags", next))
        },
        onRemove = { tag ->
            val next = (current.tags ?: emptyList()) - tag
            emit(current.copy(tags = next), PatchOperation.SetFrontmatterList("tags", next))
        },
    ).also { cloud ->
        if (project != null) {
            cloud.setAllKnownTags {
                io.github.barsia.speqa.registry.SpeqaTagRegistry.getInstance(project).allTags.toSet()
            }
        }
    }

    private val environmentCloud = TagCloud(
        coloredChips = false,
        metadataScope = MetadataScope.TEST_CASES,
        metadataKind = MetadataKind.ENVIRONMENT,
        metadataProject = project,
        hideAddButton = true,
        onAdd = { env ->
            val next = (current.environment ?: emptyList()) + env
            emit(
                current.copy(environment = next),
                PatchOperation.SetFrontmatterList("environment", next),
            )
        },
        onRemove = { env ->
            val next = (current.environment ?: emptyList()) - env
            emit(
                current.copy(environment = next),
                PatchOperation.SetFrontmatterList("environment", next),
            )
        },
    ).also { cloud ->
        if (project != null) {
            cloud.setAllKnownTags {
                io.github.barsia.speqa.registry.SpeqaTagRegistry.getInstance(project).allEnvironments.toSet()
            }
        }
    }

    private val attachmentList: AttachmentList? = if (project != null && file != null) {
        AttachmentList(project, file, hideAddButton = true) { next ->
            emit(current.copy(attachments = next), PatchOperation.SetAttachments(next))
        }
    } else null

    private val linkList = LinkList(project, hideAddButton = true) { next ->
        emit(current.copy(links = next), PatchOperation.SetLinks(next))
    }

    private val descriptionSection = EditableBodyBlockSection(
        emptyLabel = SpeqaBundle.message("placeholder.descriptionBlock"),
        onCommit = { text -> commitDescription(text) },
    )
    private val preconditionsSection = EditableBodyBlockSection(
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
            current = current.copy(steps = next)
            if (onPatch == null) onChange(current)
        },
        onStepPatch = onPatch?.let { sink -> { op -> sink(current, op) } },
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
    }

    private fun buildLayout() {
        val sectionGap = JBUI.scale(10)

        headerUtilityRow.alignmentX = Component.LEFT_ALIGNMENT
        add(headerUtilityRow)
        add(javax.swing.Box.createVerticalStrut(JBUI.scale(6)))

        titleRow.alignmentX = Component.LEFT_ALIGNMENT
        add(titleRow)
        add(javax.swing.Box.createVerticalStrut(sectionGap))

        val priStatRow = twoColumnRow(
            leftCaption = SpeqaBundle.message("label.priority"),
            rightCaption = SpeqaBundle.message("label.status"),
            leftBody = priorityCombo,
            rightBody = statusCombo,
        )
        priStatRow.alignmentX = Component.LEFT_ALIGNMENT
        add(priStatRow)
        add(javax.swing.Box.createVerticalStrut(sectionGap))

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

    fun updateFrom(case: TestCase, flash: Boolean = false) {
        val previous = current
        current = case
        val shouldFlash = flash && !firstUpdate
        firstUpdate = false

        if (previous.title != case.title) {
            titleRow.setTitle(case.title, flash = shouldFlash)
        }
        idRow.update(case.id, nextFreeId = (case.id ?: 0) + 1, isDuplicate = false)

        // Header dates — require project + file to resolve.
        val project = this.project
        val file = this.file
        if (project != null && file != null) {
            val meta = resolveTestCaseHeaderMeta(project, file)
            headerUtilityRow.setDates(
                leftText = SpeqaBundle.message("panel.header.created", meta.createdLabel),
                rightText = SpeqaBundle.message("panel.header.updated", meta.updatedLabel),
            )
        }

        if (previous.priority != case.priority) {
            priorityCombo.setValue(case.priority ?: Priority.NORMAL)
            if (shouldFlash) CommitFlash.flash(priorityCombo)
        }
        if (previous.status != case.status) {
            statusCombo.setValue(case.status ?: Status.DRAFT)
            if (shouldFlash) CommitFlash.flash(statusCombo)
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
                descriptionSection.setText(newDescription)
                if (shouldFlash) CommitFlash.flash(descriptionSection.flashTarget())
            }
            val prevPreconditions = mergeBodyBlocks(previous.bodyBlocks, PreconditionsBlock::class.java)
            val newPreconditions = mergeBodyBlocks(case.bodyBlocks, PreconditionsBlock::class.java)
            if (prevPreconditions != newPreconditions) {
                preconditionsSection.setText(newPreconditions)
                if (shouldFlash) CommitFlash.flash(preconditionsSection.flashTarget())
            }
        }

        if (previous.steps != case.steps) {
            if (stepsStructurallyChanged(previous.steps, case.steps)) {
                stepsSection.setSteps(case.steps)
                if (shouldFlash) CommitFlash.flash(stepsSection)
            } else {
                stepsSection.updateStepsInPlace(case.steps)
            }
        }
    }

    private fun stepsStructurallyChanged(old: List<TestStep>, new: List<TestStep>): Boolean {
        if (old.size != new.size) return true
        for (i in old.indices) {
            if (old[i].tickets != new[i].tickets) return true
            if (old[i].links != new[i].links) return true
            if (old[i].attachments != new[i].attachments) return true
            if ((old[i].expected == null) != (new[i].expected == null)) return true
        }
        return false
    }

    private fun commitDescription(text: String) {
        val next = replaceBodyBlocks(current.bodyBlocks, DescriptionBlock::class.java) {
            DescriptionBlock(text)
        }
        emit(current.copy(bodyBlocks = next), PatchOperation.SetDescription(text))
    }

    private fun commitPreconditions(text: String) {
        val existingStyle = current.bodyBlocks
            .filterIsInstance<PreconditionsBlock>()
            .firstOrNull()?.markerStyle
            ?: io.github.barsia.speqa.model.PreconditionsMarkerStyle.PRECONDITIONS
        val next = replaceBodyBlocks(current.bodyBlocks, PreconditionsBlock::class.java) {
            PreconditionsBlock(markerStyle = existingStyle, markdown = text)
        }
        emit(current.copy(bodyBlocks = next), PatchOperation.SetPreconditions(existingStyle, text))
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

    private fun onThemeChanged() {
        applyBackground()
        SwingUtilities.updateComponentTreeUI(this)
    }

    private fun applyBackground() {
        val bg = EditorColorsManager.getInstance().globalScheme.defaultBackground
        background = bg
        isOpaque = true
    }
}
