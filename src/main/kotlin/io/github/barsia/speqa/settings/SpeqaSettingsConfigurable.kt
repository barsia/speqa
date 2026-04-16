package io.github.barsia.speqa.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.Status
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

class SpeqaSettingsConfigurable(private val project: Project) : Configurable {
    private val settings get() = SpeqaSettings.getInstance(project)

    private val priorityCombo = JComboBox(Priority.entries.toTypedArray())
    private val statusCombo = JComboBox(Status.entries.toTypedArray())
    private val environmentsField = JTextField(24)
    private val attachmentsFolderField = JTextField(24)
    private val ticketTrackerCombo = JComboBox(SpeqaSettings.TRACKER_OPTIONS.toTypedArray())
    private val customTicketUrlField = JTextField(24).apply {
        toolTipText = "https://your-tracker.example.com/issue/"
    }
    private var rootPanel: JPanel? = null

    override fun getDisplayName(): String = SpeqaBundle.message("settings.Speqa.displayName")

    override fun createComponent(): JComponent {
        if (rootPanel != null) return rootPanel!!

        val panel = JPanel(GridBagLayout())
        panel.border = javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12)

        val constraints = GridBagConstraints().apply {
            insets = Insets(0, 0, 8, 0)
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            gridx = 0
            gridy = 0
        }

        addRow(panel, constraints, SpeqaBundle.message("settings.defaultPriority"), priorityCombo, SpeqaBundle.message("settings.defaultPriority.comment"))
        addRow(panel, constraints, SpeqaBundle.message("settings.defaultStatus"), statusCombo, SpeqaBundle.message("settings.defaultStatus.comment"))
        addRow(panel, constraints, SpeqaBundle.message("settings.defaultEnvironments"), environmentsField, SpeqaBundle.message("settings.defaultEnvironments.comment"))
        addRow(panel, constraints, SpeqaBundle.message("settings.defaultAttachmentsFolder"), attachmentsFolderField, SpeqaBundle.message("settings.defaultAttachmentsFolder.comment"))
        val ticketRow = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0)).apply {
            add(ticketTrackerCombo)
            add(customTicketUrlField)
        }
        ticketTrackerCombo.addActionListener {
            val isCustom = ticketTrackerCombo.selectedItem == SpeqaSettings.TRACKER_CUSTOM
            customTicketUrlField.isVisible = isCustom
            ticketRow.revalidate()
        }
        addRow(panel, constraints, SpeqaBundle.message("settings.ticketTracker"), ticketRow, SpeqaBundle.message("settings.ticketTracker.comment"))

        rootPanel = JPanel(BorderLayout()).apply { add(panel, BorderLayout.NORTH) }
        reset()
        return rootPanel!!
    }

    override fun isModified(): Boolean {
        return priorityCombo.selectedItem != settings.defaultPriority ||
            statusCombo.selectedItem != settings.defaultStatus ||
            environmentsField.text != settings.defaultEnvironments.joinToString(", ") ||
            attachmentsFolderField.text != settings.defaultAttachmentsFolder ||
            ticketTrackerCombo.selectedItem != settings.ticketTracker ||
            customTicketUrlField.text != settings.customTicketUrl
    }

    override fun apply() {
        settings.defaultPriority = priorityCombo.selectedItem as? Priority ?: Priority.NORMAL
        settings.defaultStatus = statusCombo.selectedItem as? Status ?: Status.DRAFT
        settings.defaultEnvironments = environmentsField.text
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        settings.defaultAttachmentsFolder = attachmentsFolderField.text.trim().ifEmpty { SpeqaSettings.DEFAULT_ATTACHMENTS_FOLDER }
        settings.ticketTracker = ticketTrackerCombo.selectedItem as? String ?: SpeqaSettings.TRACKER_YOUTRACK
        settings.customTicketUrl = customTicketUrlField.text.trim()
    }

    override fun reset() {
        priorityCombo.selectedItem = settings.defaultPriority
        statusCombo.selectedItem = settings.defaultStatus
        environmentsField.text = settings.defaultEnvironments.joinToString(", ")
        attachmentsFolderField.text = settings.defaultAttachmentsFolder
        ticketTrackerCombo.selectedItem = settings.ticketTracker
        customTicketUrlField.text = settings.customTicketUrl
        customTicketUrlField.isVisible = settings.ticketTracker == SpeqaSettings.TRACKER_CUSTOM
    }

    override fun disposeUIResources() {
        rootPanel = null
    }

    private fun addRow(
        panel: JPanel,
        constraints: GridBagConstraints,
        label: String,
        field: JComponent,
        comment: String,
    ) {
        val row = constraints.gridy

        val labelConstraints = constraints.clone() as GridBagConstraints
        labelConstraints.gridx = 0
        labelConstraints.gridy = row
        labelConstraints.weightx = 0.0
        labelConstraints.insets = Insets(0, 0, 4, 12)
        panel.add(JLabel(label), labelConstraints)

        val fieldConstraints = constraints.clone() as GridBagConstraints
        fieldConstraints.gridx = 1
        fieldConstraints.gridy = row
        fieldConstraints.weightx = 1.0
        panel.add(field, fieldConstraints)

        val commentConstraints = constraints.clone() as GridBagConstraints
        commentConstraints.gridx = 1
        commentConstraints.gridy = row + 1
        commentConstraints.weightx = 1.0
        commentConstraints.anchor = GridBagConstraints.WEST
        commentConstraints.fill = GridBagConstraints.NONE
        commentConstraints.insets = Insets(0, 0, 10, 0)
        panel.add(JLabel("<html><span style='color:#808080'>$comment</span></html>"), commentConstraints)

        constraints.gridy = row + 2
    }
}
