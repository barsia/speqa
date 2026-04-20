package io.github.barsia.speqa.editor

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import io.github.barsia.speqa.SpeqaBundle
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridLayout
import java.awt.Insets
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

internal data class RunImportOptions(
    val importTags: Boolean = true,
    val importEnvironment: Boolean = true,
    val importTickets: Boolean = false,
    val importLinks: Boolean = false,
    val importAttachments: Boolean = false,
)

internal data class RunCreationRequest(
    val destinationRelativePath: String,
    val fileName: String,
    val importOptions: RunImportOptions,
)

internal fun runCreationImportColumns(): Int = 2

internal object RunCreationPathSupport {
    fun normalizeDestinationRelativePath(projectBasePath: String, rawPath: String): String {
        val projectRoot = Paths.get(projectBasePath).normalize()
        val trimmed = rawPath.trim()
        if (trimmed.isBlank()) return "."

        val inputPath = Paths.get(trimmed)
        val normalizedDestination = if (inputPath.isAbsolute) {
            inputPath.normalize()
        } else {
            projectRoot.resolve(inputPath).normalize()
        }

        return projectRoot.relativize(normalizedDestination).invariantSeparatorsPath.ifBlank { "." }
    }

    fun isDestinationInsideProject(projectBasePath: String, rawPath: String): Boolean {
        val projectRoot = Paths.get(projectBasePath).normalize()
        val trimmed = rawPath.trim()
        if (trimmed.isBlank()) return true

        val inputPath = Paths.get(trimmed)
        val normalizedDestination = if (inputPath.isAbsolute) {
            inputPath.normalize()
        } else {
            projectRoot.resolve(inputPath).normalize()
        }

        return normalizedDestination.startsWith(projectRoot)
    }

    fun isValidFileName(rawFileName: String): Boolean {
        val trimmed = rawFileName.trim()
        if (trimmed.isBlank()) return false
        if (trimmed == "." || trimmed == "..") return false
        if (trimmed.contains('/') || trimmed.contains('\\')) return false
        if (INVALID_FILE_NAME_CHARS.any(trimmed::contains)) return false
        return true
    }

    private val Path.invariantSeparatorsPath: String
        get() = toString().replace('\\', '/')

    private val INVALID_FILE_NAME_CHARS = charArrayOf(':', '*', '?', '"', '<', '>', '|')
}

internal class RunCreationDialog(
    private val project: Project,
    destinationRelativePath: String,
    fileName: String,
    hasTags: Boolean,
    hasEnvironment: Boolean,
    hasTickets: Boolean,
    hasLinks: Boolean,
    hasAttachments: Boolean,
) : DialogWrapper(project) {

    private val destinationField = TextFieldWithBrowseButton().apply {
        text = destinationRelativePath
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
            .withTitle(SpeqaBundle.message("dialog.createRun.destination"))
        addActionListener {
            val chosen = com.intellij.openapi.fileChooser.FileChooser.chooseFile(descriptor, project, null)
            if (chosen != null) {
                text = chosen.path
            }
        }
    }
    private val fileNameField = JBTextField(fileName)
    private val importTagsCheckBox = JBCheckBox(SpeqaBundle.message("dialog.createRun.import.tags"), true)
    private val importEnvironmentCheckBox = JBCheckBox(SpeqaBundle.message("dialog.createRun.import.environment"), true)
    private val importTicketsCheckBox = JBCheckBox(SpeqaBundle.message("dialog.createRun.import.tickets"), false)
    private val importLinksCheckBox = JBCheckBox(SpeqaBundle.message("dialog.createRun.import.links"), false)
    private val importAttachmentsCheckBox = JBCheckBox(SpeqaBundle.message("dialog.createRun.import.attachments"), false)
    private val destinationErrorLabel = JBLabel().apply {
        foreground = JBColor.RED
        isVisible = false
    }
    private val fileNameErrorLabel = JBLabel().apply {
        foreground = JBColor.RED
        isVisible = false
    }
    private lateinit var centerPanel: JPanel
    private lateinit var destinationErrorPanel: JPanel
    private lateinit var fileNameErrorPanel: JPanel

    init {
        configureImportCheckBox(
            checkBox = importTagsCheckBox,
            hasContent = hasTags,
            emptyTooltip = SpeqaBundle.message("dialog.createRun.import.tags.empty"),
        )
        configureImportCheckBox(
            checkBox = importEnvironmentCheckBox,
            hasContent = hasEnvironment,
            emptyTooltip = SpeqaBundle.message("dialog.createRun.import.environment.empty"),
        )
        configureImportCheckBox(
            checkBox = importTicketsCheckBox,
            hasContent = hasTickets,
            emptyTooltip = SpeqaBundle.message("dialog.createRun.import.tickets.empty"),
        )
        configureImportCheckBox(
            checkBox = importLinksCheckBox,
            hasContent = hasLinks,
            emptyTooltip = SpeqaBundle.message("dialog.createRun.import.links.empty"),
        )
        configureImportCheckBox(
            checkBox = importAttachmentsCheckBox,
            hasContent = hasAttachments,
            emptyTooltip = SpeqaBundle.message("dialog.createRun.import.attachments.empty"),
        )
        applyHandCursor(destinationField)
        applyHandCursor(importTagsCheckBox)
        applyHandCursor(importEnvironmentCheckBox)
        applyHandCursor(importTicketsCheckBox)
        applyHandCursor(importLinksCheckBox)
        applyHandCursor(importAttachmentsCheckBox)
        title = SpeqaBundle.message("dialog.createRun.title")
        setOKButtonText(SpeqaBundle.message("dialog.createRun.ok"))
        init()
        SwingUtilities.invokeLater { updateErrorAlignment() }
        destinationField.textField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = updateDestinationValidation()
            override fun removeUpdate(e: DocumentEvent?) = updateDestinationValidation()
            override fun changedUpdate(e: DocumentEvent?) = updateDestinationValidation()
        })
        fileNameField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = updateValidationState()
            override fun removeUpdate(e: DocumentEvent?) = updateValidationState()
            override fun changedUpdate(e: DocumentEvent?) = updateValidationState()
        })
        updateValidationState()
    }

    override fun createSouthPanel(): JComponent {
        val panel = super.createSouthPanel()
        applyHandCursor(panel)
        return panel
    }

    val request: RunCreationRequest
        get() {
            val projectBase = project.basePath.orEmpty()
            val relativePath = RunCreationPathSupport.normalizeDestinationRelativePath(
                projectBasePath = projectBase,
                rawPath = destinationField.text,
            )
            return RunCreationRequest(
                destinationRelativePath = relativePath,
                fileName = fileNameField.text.trim(),
                importOptions = RunImportOptions(
                    importTags = importTagsCheckBox.isSelected,
                    importEnvironment = importEnvironmentCheckBox.isSelected,
                    importTickets = importTicketsCheckBox.isSelected,
                    importLinks = importLinksCheckBox.isSelected,
                    importAttachments = importAttachmentsCheckBox.isSelected,
                ),
            )
        }

    override fun createCenterPanel(): JComponent {
        destinationField.preferredSize = Dimension(420, destinationField.preferredSize.height)
        fileNameField.preferredSize = Dimension(420, fileNameField.preferredSize.height)
        val importSectionLabel = JBLabel(SpeqaBundle.message("dialog.createRun.import.section"))
        val importCheckBoxes = listOf(
            importTagsCheckBox,
            importEnvironmentCheckBox,
            importTicketsCheckBox,
            importLinksCheckBox,
            importAttachmentsCheckBox,
        )
        importCheckBoxes.forEach { it.margin = Insets(0, 0, 0, 0) }
        val importColumns = runCreationImportColumns()
        val importCheckBoxRows: List<JPanel> = importCheckBoxes
            .chunked(importColumns)
            .map { boxesInRow ->
                JPanel(GridLayout(1, importColumns, 12, 0)).apply {
                    isOpaque = false
                    boxesInRow.forEach { add(it) }
                    repeat(importColumns - boxesInRow.size) {
                        add(JPanel().apply { isOpaque = false })
                    }
                }
            }
        destinationErrorPanel = JPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.X_AXIS)
            isOpaque = false
            add(destinationErrorLabel)
        }
        fileNameErrorPanel = JPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.X_AXIS)
            isOpaque = false
            add(fileNameErrorLabel)
        }
        centerPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(SpeqaBundle.message("dialog.createRun.destination"), destinationField)
            .addComponent(destinationErrorPanel)
            .addLabeledComponent(SpeqaBundle.message("dialog.createRun.fileName"), fileNameField)
            .addComponent(fileNameErrorPanel)
            .panel
        val importRowY = centerPanel.componentCount
        centerPanel.add(
            importSectionLabel,
            GridBagConstraints().apply {
                gridx = 0
                gridy = importRowY
                anchor = GridBagConstraints.WEST
                insets = Insets(10, 0, 0, 5)
            },
        )
        importCheckBoxRows.forEachIndexed { index, rowPanel ->
            centerPanel.add(
                rowPanel,
                GridBagConstraints().apply {
                    gridx = 1
                    gridy = importRowY + index
                    anchor = GridBagConstraints.WEST
                    weightx = 1.0
                    fill = GridBagConstraints.HORIZONTAL
                    insets = Insets(if (index == 0) 10 else 4, 0, 0, 0)
                },
            )
        }
        return centerPanel
    }

    private fun configureImportCheckBox(
        checkBox: JBCheckBox,
        hasContent: Boolean,
        emptyTooltip: String,
    ) {
        checkBox.isEnabled = hasContent
        if (!hasContent) {
            checkBox.isSelected = false
            checkBox.toolTipText = emptyTooltip
        } else {
            checkBox.toolTipText = null
        }
    }

    private fun updateDestinationValidation() {
        updateValidationState()
    }

    private fun updateValidationState() {
        val projectBase = project.basePath
        val isDestinationValid = projectBase != null &&
            RunCreationPathSupport.isDestinationInsideProject(projectBase, destinationField.text)
        val isFileNameValid = RunCreationPathSupport.isValidFileName(fileNameField.text)

        val destinationErrorText = if (isDestinationValid) "" else SpeqaBundle.message("dialog.createRun.errorOutsideProject")
        destinationErrorLabel.text = destinationErrorText
        destinationErrorLabel.isVisible = destinationErrorText.isNotBlank()

        val fileNameErrorText = if (isFileNameValid) "" else SpeqaBundle.message("dialog.createRun.errorInvalidFileName")
        fileNameErrorLabel.text = fileNameErrorText
        fileNameErrorLabel.isVisible = fileNameErrorText.isNotBlank()

        isOKActionEnabled = isDestinationValid && isFileNameValid
        updateErrorAlignment()
    }

    private fun updateErrorAlignment() {
        if (!::centerPanel.isInitialized) return
        alignErrorPanel(destinationErrorPanel, destinationField.textField)
        alignErrorPanel(fileNameErrorPanel, fileNameField)
        centerPanel.revalidate()
        centerPanel.repaint()
    }

    private fun alignErrorPanel(errorPanel: JPanel, textComponent: JComponent) {
        if (!textComponent.isShowing || !errorPanel.isShowing) return
        val textStart = SwingUtilities.convertPoint(textComponent, textComponent.insets.left, 0, centerPanel).x
        errorPanel.border = BorderFactory.createEmptyBorder(0, textStart, 0, 0)
    }

    private fun applyHandCursor(component: Component) {
        if (component is javax.swing.AbstractButton) {
            component.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }
        if (component is java.awt.Container) {
            component.components.forEach(::applyHandCursor)
        }
    }
}
