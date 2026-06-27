package io.github.barsia.speqa.editor.runcreation

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.CheckBoxList
import com.intellij.ui.JBColor
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.RunCreationPathSupport
import io.github.barsia.speqa.editor.RunImportOptions
import io.github.barsia.speqa.editor.ui.primitives.handCursor
import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.Status
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Point
import javax.swing.DefaultListCellRenderer
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Creation request produced by [CreateTestRunDialog]. The caller passes it to
 * `CreateMultiCaseRunWriter.createMultiCaseRunFile` (wired in Task 7).
 */
internal data class CreateTestRunRequest(
    val selectedFiles: List<VirtualFile>,
    val destinationRelativePath: String,
    val fileName: String,
    val importOptions: RunImportOptions,
    val title: String,
)

/**
 * Adaptive Create Test Run dialog. Shows filter controls only for facets that are
 * present across the candidate cases, keeps a live checklist of the matching cases,
 * and produces a [CreateTestRunRequest] mapped back to the selected [VirtualFile]s.
 */
internal class CreateTestRunDialog(
    private val project: Project,
    private val candidates: List<CandidateCase>,
    private val filesByKey: Map<String, VirtualFile>,
    destinationRelativePath: String,
    fileName: String,
    private val timestamp: String,
) : DialogWrapper(project) {

    private val present: PresentFacets = CreateRunFacets.present(candidates.map { it.facets })

    private val presentStatuses: List<Status> =
        candidates.map { it.facets.status }.distinct().sortedBy { it.ordinal }
    private val presentPriorities: List<Priority> =
        candidates.map { it.facets.priority }.distinct().sortedBy { it.ordinal }
    private val presentTags: List<String> =
        candidates.flatMap { it.facets.tags }.distinct().sorted()
    private val presentEnvironments: List<String> =
        candidates.flatMap { it.facets.environments }.distinct().sorted()

    private val checkedKeys: MutableSet<String> = mutableSetOf()

    private var selectedStatus: Status? = null
    private var selectedPriority: Priority? = null
    private var selectedTag: String? = null
    private var selectedEnvironment: String? = null

    private var userEditedTitle = false

    private val statusCombo = ComboBox(presentStatuses.toTypedArray()).apply {
        selectedIndex = -1
        handCursor()
        renderer = placeholderRenderer(
            placeholder = SpeqaBundle.message("dialog.createRun.filter.status"),
            label = { (it as? Status)?.label?.replaceFirstChar { c -> c.uppercase() } },
        )
        accessibleContext.accessibleName = SpeqaBundle.message("dialog.createRun.filter.status")
        addActionListener { selectedStatus = selectedItem as? Status; onFilterChanged() }
    }

    private val priorityCombo = ComboBox(presentPriorities.toTypedArray()).apply {
        selectedIndex = -1
        handCursor()
        renderer = placeholderRenderer(
            placeholder = SpeqaBundle.message("dialog.createRun.filter.priority"),
            label = { (it as? Priority)?.label?.replaceFirstChar { c -> c.uppercase() } },
        )
        accessibleContext.accessibleName = SpeqaBundle.message("dialog.createRun.filter.priority")
        addActionListener { selectedPriority = selectedItem as? Priority; onFilterChanged() }
    }

    private val tagsCombo = ComboBox(presentTags.toTypedArray()).apply {
        selectedIndex = -1
        handCursor()
        renderer = placeholderRenderer(
            placeholder = SpeqaBundle.message("dialog.createRun.filter.tags"),
            label = { it as? String },
        )
        accessibleContext.accessibleName = SpeqaBundle.message("dialog.createRun.filter.tags")
        addActionListener { selectedTag = selectedItem as? String; onFilterChanged() }
    }

    private val environmentsCombo = ComboBox(presentEnvironments.toTypedArray()).apply {
        selectedIndex = -1
        handCursor()
        renderer = placeholderRenderer(
            placeholder = SpeqaBundle.message("dialog.createRun.filter.environment"),
            label = { it as? String },
        )
        accessibleContext.accessibleName = SpeqaBundle.message("dialog.createRun.filter.environment")
        addActionListener { selectedEnvironment = selectedItem as? String; onFilterChanged() }
    }

    private val titleField = JBTextField(defaultTitle()).apply {
        document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = markTitleEdited()
            override fun removeUpdate(e: DocumentEvent?) = markTitleEdited()
            override fun changedUpdate(e: DocumentEvent?) = markTitleEdited()
        })
    }
    private var suppressTitleEdit = false

    private val destinationField = TextFieldWithBrowseButton().apply {
        text = destinationRelativePath
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
            .withTitle(SpeqaBundle.message("dialog.createRun.destination"))
        addActionListener {
            val chosen = FileChooser.chooseFile(descriptor, project, null)
            if (chosen != null) text = chosen.path
        }
    }
    private val fileNameField = JBTextField(fileName)

    private val importDescriptionCheckBox = JBCheckBox(SpeqaBundle.message("dialog.createRun.import.description"), true)
    private val importTagsCheckBox = JBCheckBox(SpeqaBundle.message("dialog.createRun.import.tags"))
    private val importEnvironmentCheckBox = JBCheckBox(SpeqaBundle.message("dialog.createRun.import.environment"))
    private val importTicketsCheckBox = JBCheckBox(SpeqaBundle.message("dialog.createRun.import.tickets"), false)
    private val importLinksCheckBox = JBCheckBox(SpeqaBundle.message("dialog.createRun.import.links"), false)
    private val importAttachmentsCheckBox = JBCheckBox(SpeqaBundle.message("dialog.createRun.import.attachments"), false)

    private val footerLabel = JBLabel()
    // Native CheckBoxList (a JBList): clipped case titles are revealed by the platform's
    // expandable-item hover popup, matching the tool-window TCs/TRs trees instead of a tooltip.
    // The overrides make a click anywhere on a row (the title text included) toggle the checkbox,
    // not only a click on the box glyph: CheckBoxList delivers the click to the box at the point
    // these methods return, so we map any valid in-row point onto the glyph.
    private val casesList = object : CheckBoxList<CandidateCase>() {
        override fun findPointRelativeToCheckBox(x: Int, y: Int, checkBox: JCheckBox, width: Int): Point? {
            val base = super.findPointRelativeToCheckBox(x, y, checkBox, width) ?: return null
            return Point(checkBox.preferredSize.height / 2, base.y)
        }

        override fun findPointRelativeToCheckBoxWithAdjustedRendering(x: Int, y: Int, checkBox: JCheckBox, width: Int): Point? {
            val base = super.findPointRelativeToCheckBoxWithAdjustedRendering(x, y, checkBox, width) ?: return null
            return Point(checkBox.preferredSize.height / 2, base.y)
        }
    }.apply {
        emptyText.text = SpeqaBundle.message("dialog.createRun.noMatches")
        setCheckBoxListListener { index, value ->
            val item = getItemAt(index) ?: return@setCheckBoxListListener
            if (value) checkedKeys.add(item.key) else checkedKeys.remove(item.key)
            updateFooter()
            updateAutoTitle()
            updateValidation()
        }
    }
    private val destinationErrorLabel = JBLabel().apply { foreground = JBColor.RED; isVisible = false }
    private val fileNameErrorLabel = JBLabel().apply { foreground = JBColor.RED; isVisible = false }

    init {
        configureImportCheckBox(importTagsCheckBox, present.tags, SpeqaBundle.message("dialog.createRun.import.tags.empty"), true)
        configureImportCheckBox(importEnvironmentCheckBox, present.environments, SpeqaBundle.message("dialog.createRun.import.environment.empty"), true)
        listOf(importDescriptionCheckBox, importTicketsCheckBox, importLinksCheckBox, importAttachmentsCheckBox).forEach { it.handCursor() }
        importTagsCheckBox.handCursor()
        importEnvironmentCheckBox.handCursor()
        title = SpeqaBundle.message("dialog.createRun.title")
        setOKButtonText(SpeqaBundle.message("dialog.createRun.ok"))
        init()
        destinationField.textField.document.addDocumentListener(simpleListener { updateValidation() })
        fileNameField.document.addDocumentListener(simpleListener { updateValidation() })
        checkedKeys.addAll(CreateRunDialogState.initialCheckedKeys(candidates))
        rebuildCasesList()
        updateFooter()
        updateValidation()
    }

    val request: CreateTestRunRequest
        get() {
            val selectedFiles = CreateRunDialogState
                .selectedKeys(candidates, currentFilter(), checkedKeys)
                .mapNotNull { filesByKey[it] }
            val relativePath = RunCreationPathSupport.normalizeDestinationRelativePath(
                projectBasePath = project.basePath.orEmpty(),
                rawPath = destinationField.text,
            )
            return CreateTestRunRequest(
                selectedFiles = selectedFiles,
                destinationRelativePath = relativePath,
                fileName = fileNameField.text.trim(),
                importOptions = RunImportOptions(
                    importDescription = importDescriptionCheckBox.isSelected,
                    importTags = importTagsCheckBox.isSelected,
                    importEnvironment = importEnvironmentCheckBox.isSelected,
                    importTickets = importTicketsCheckBox.isSelected,
                    importLinks = importLinksCheckBox.isSelected,
                    importAttachments = importAttachmentsCheckBox.isSelected,
                ),
                title = titleField.text.trim(),
            )
        }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        var gridy = 0

        fun fullRow(comp: JComponent, topGap: Int = 0) {
            // Capture the row index outside apply{}: an unqualified `gridy` inside the
            // block resolves to GridBagConstraints' own member, not this function's local.
            val r = gridy++
            panel.add(comp, GridBagConstraints().apply {
                gridy = r
                gridx = 0; gridwidth = 2
                fill = GridBagConstraints.HORIZONTAL; weightx = 1.0
                anchor = GridBagConstraints.NORTHWEST
                insets = JBUI.insets(topGap, 0, 0, 0)
            })
        }

        fun labeledRow(labelKey: String, field: JComponent, topGap: Int = 0) {
            val r = gridy++
            // labelFor exposes this caption as the field's accessible name to screen readers.
            val rowLabel = JBLabel(SpeqaBundle.message(labelKey)).apply { labelFor = field }
            panel.add(rowLabel, GridBagConstraints().apply {
                gridy = r; gridx = 0
                fill = GridBagConstraints.NONE; weightx = 0.0
                anchor = GridBagConstraints.WEST
                insets = JBUI.insets(topGap, 0, 0, JBUI.scale(8))
            })
            panel.add(field, GridBagConstraints().apply {
                gridy = r; gridx = 1
                fill = GridBagConstraints.HORIZONTAL; weightx = 1.0
                anchor = GridBagConstraints.WEST
                insets = JBUI.insets(topGap, 0, 0, 0)
            })
        }

        // Optional filter section
        buildFilterPanel()?.let { fp ->
            fullRow(fp)
        }

        // Cases section
        val casesHeader = JPanel(BorderLayout()).apply {
            isOpaque = false
            // labelFor exposes the caption as the checklist's accessible name to screen readers.
            add(JBLabel(SpeqaBundle.message("dialog.createRun.cases.caption")).apply { labelFor = casesList }, BorderLayout.WEST)
            add(JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), 0)).apply {
                isOpaque = false
                add(buildActionButton(SpeqaBundle.message("dialog.createRun.selectAll")) { toggleVisible(true) })
                add(buildActionButton(SpeqaBundle.message("dialog.createRun.clear")) { toggleVisible(false) })
            }, BorderLayout.EAST)
        }
        fullRow(casesHeader, topGap = JBUI.scale(8))

        val casesScroll = ScrollPaneFactory.createScrollPane(casesList).apply {
            border = JBUI.Borders.customLine(JBColor.border())
            preferredSize = Dimension(JBUI.scale(200), JBUI.scale(160))
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        }
        fullRow(casesScroll, topGap = JBUI.scale(2))

        footerLabel.foreground = UIUtil.getContextHelpForeground()
        fullRow(footerLabel, topGap = JBUI.scale(2))

        // Title / destination / filename
        labeledRow("dialog.createRun.runTitle", titleField, topGap = JBUI.scale(8))
        labeledRow("dialog.createRun.destination", destinationField, topGap = JBUI.scale(4))
        fullRow(destinationErrorLabel)
        labeledRow("dialog.createRun.fileName", fileNameField, topGap = JBUI.scale(4))
        fullRow(fileNameErrorLabel)

        // Import section
        fullRow(TitledSeparator(SpeqaBundle.message("dialog.createRun.import.section")), topGap = JBUI.scale(8))
        fullRow(buildImportPanel(), topGap = JBUI.scale(2))

        // Push content to top
        val spacerRow = gridy
        panel.add(JLabel(), GridBagConstraints().apply {
            this.gridy = spacerRow; gridx = 0; gridwidth = 2
            fill = GridBagConstraints.BOTH; weighty = 1.0
        })

        return panel
    }

    private fun buildFilterPanel(): JComponent? {
        val items = buildList<JComponent> {
            if (present.status) add(statusCombo)
            if (present.priority) add(priorityCombo)
            if (present.tags) add(tagsCombo)
            if (present.environments) add(environmentsCombo)
        }
        if (items.isEmpty()) return null

        val panel = JPanel(GridBagLayout())
        panel.isOpaque = false
        val hGap = JBUI.scale(8)
        val vGap = JBUI.scale(4)

        items.forEachIndexed { i, comp ->
            val col = i % 3
            val row = i / 3
            panel.add(comp, GridBagConstraints().apply {
                gridy = row; gridx = col
                fill = GridBagConstraints.HORIZONTAL; weightx = 1.0 / 3.0
                anchor = GridBagConstraints.WEST
                insets = JBUI.insets(if (row > 0) vGap else 0, if (col > 0) hGap else 0, 0, 0)
            })
        }
        return panel
    }

    private fun buildActionButton(text: String, onClick: () -> Unit): JComponent =
        object : javax.swing.JButton(text) {
            // Darcula/Aqua enforce a wide minimum button width that ignores setMargin();
            // hug the text instead so these header actions stay compact.
            override fun getPreferredSize(): Dimension {
                val base = super.getPreferredSize()
                val width = getFontMetrics(font).stringWidth(getText()) + JBUI.scale(16)
                return Dimension(width, base.height)
            }

            override fun getMinimumSize(): Dimension = preferredSize
        }.apply {
            handCursor()
            UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, this)
            addActionListener { onClick() }
        }

    private fun buildImportPanel(): JComponent =
        JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(12), JBUI.scale(2))).apply {
            isOpaque = false
            listOf(
                importDescriptionCheckBox,
                importTagsCheckBox,
                importEnvironmentCheckBox,
                importTicketsCheckBox,
                importLinksCheckBox,
                importAttachmentsCheckBox,
            ).forEach { add(it) }
        }

    private fun toggleVisible(checked: Boolean) {
        CreateRunDialogState.visible(candidates, currentFilter()).forEach {
            if (checked) checkedKeys.add(it.key) else checkedKeys.remove(it.key)
        }
        rebuildCasesList()
        updateFooter()
        updateAutoTitle()
        updateValidation()
    }

    private fun rebuildCasesList() {
        casesList.clear()
        CreateRunDialogState.visible(candidates, currentFilter()).forEach { candidate ->
            casesList.addItem(candidate, candidate.title, candidate.key in checkedKeys)
        }
        casesList.revalidate()
        casesList.repaint()
    }

    private fun onFilterChanged() {
        rebuildCasesList()
        updateFooter()
        updateAutoTitle()
        updateValidation()
    }

    private fun currentFilter(): CreateRunFilter =
        CreateRunFilter(
            status = selectedStatus,
            priority = selectedPriority,
            tags = setOfNotNull(selectedTag),
            environments = setOfNotNull(selectedEnvironment),
        )

    private fun updateFooter() {
        val count = CreateRunDialogState.selectedCount(candidates, currentFilter(), checkedKeys)
        footerLabel.text = SpeqaBundle.message("dialog.createRun.footer", count, count)
    }

    private fun updateValidation() {
        val projectBase = project.basePath
        val destinationValid = projectBase != null &&
            RunCreationPathSupport.isDestinationInsideProject(projectBase, destinationField.text)
        val fileNameValid = RunCreationPathSupport.isValidFileName(fileNameField.text)
        val hasSelection = CreateRunDialogState.selectedCount(candidates, currentFilter(), checkedKeys) > 0

        val destinationError = if (destinationValid) null else SpeqaBundle.message("dialog.createRun.errorOutsideProject")
        destinationErrorLabel.text = destinationError.orEmpty()
        destinationErrorLabel.isVisible = !destinationValid
        // Mirror the inline error into the field's accessible description so screen-reader
        // users hear it on focus; the non-focusable error label alone never reaches them.
        destinationField.textField.accessibleContext.accessibleDescription = destinationError

        val fileNameError = if (fileNameValid) null else SpeqaBundle.message("dialog.createRun.errorInvalidFileName")
        fileNameErrorLabel.text = fileNameError.orEmpty()
        fileNameErrorLabel.isVisible = !fileNameValid
        fileNameField.accessibleContext.accessibleDescription = fileNameError

        isOKActionEnabled = hasSelection && destinationValid && fileNameValid
    }

    private fun markTitleEdited() {
        if (!suppressTitleEdit) userEditedTitle = true
    }

    private fun updateAutoTitle() {
        if (userEditedTitle) return
        suppressTitleEdit = true
        titleField.text = defaultTitle()
        suppressTitleEdit = false
    }

    private fun defaultTitle(): String = CreateRunTitle.defaultTitle(activeLabel(), timestamp)

    /**
     * The active facet label used to seed the run title: the sole constrained facet
     * when exactly one facet is constrained to a single value, otherwise null.
     */
    private fun activeLabel(): String? {
        val active = listOfNotNull(selectedStatus?.label, selectedPriority?.label, selectedTag, selectedEnvironment)
        return if (active.size == 1) active.first() else null
    }

    private fun configureImportCheckBox(
        checkBox: JBCheckBox,
        hasContent: Boolean,
        emptyTooltip: String,
        defaultSelected: Boolean,
    ) {
        checkBox.isEnabled = hasContent
        if (hasContent) {
            checkBox.isSelected = defaultSelected
            checkBox.toolTipText = null
        } else {
            checkBox.isSelected = false
            checkBox.toolTipText = emptyTooltip
        }
    }

    private fun simpleListener(onChange: () -> Unit): DocumentListener = object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent?) = onChange()
        override fun removeUpdate(e: DocumentEvent?) = onChange()
        override fun changedUpdate(e: DocumentEvent?) = onChange()
    }
}

private fun placeholderRenderer(placeholder: String, label: (Any?) -> String?): DefaultListCellRenderer =
    object : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean,
        ): Component {
            val text = label(value)
            val comp = super.getListCellRendererComponent(list, text ?: placeholder, index, isSelected, cellHasFocus)
            if (text == null) (comp as? JLabel)?.foreground = UIUtil.getContextHelpForeground()
            return comp
        }
    }
