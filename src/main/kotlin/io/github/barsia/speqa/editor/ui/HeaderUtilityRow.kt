package io.github.barsia.speqa.editor.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.ui.primitives.dateIconLabel
import io.github.barsia.speqa.editor.ui.primitives.speqaIconButton
import java.awt.Component
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.Box
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Single-line header row composed of: the editable id chip, a pair of
 * timestamp labels (created + updated / finished), and a trailing action
 * component (Run button on test cases, verdict control on test runs).
 *
 * @param idChip existing `InlineEditableIdRow` (reused unchanged).
 * @param leftDateIcon icon for the first timestamp (typically `calendarCreated.svg`).
 * @param leftDateText rendered text for the first timestamp.
 * @param leftDateTooltip optional tooltip for the first timestamp.
 * @param rightDateIcon icon for the second timestamp (typically `calendarUpdated.svg` or `calendarFinished.svg`).
 * @param rightDateText rendered text for the second timestamp.
 * @param rightDateTooltip optional tooltip for the second timestamp.
 * @param trailing component added at the far right (glue-pushed).
 */
class HeaderUtilityRow(
    idChip: JComponent,
    leftDateIcon: Icon,
    leftDateText: String,
    leftDateTooltip: String? = null,
    rightDateIcon: Icon,
    rightDateText: String,
    rightDateTooltip: String? = null,
    trailing: JComponent,
) : JPanel(GridBagLayout()) {

    private val createdLabel = dateIconLabel(leftDateIcon, leftDateText, leftDateTooltip)
    private val updatedLabel = dateIconLabel(rightDateIcon, rightDateText, rightDateTooltip)

    init {
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT

        val gap = JBUI.scale(12)

        add(idChip, GridBagConstraints().apply {
            gridx = 0; gridy = 0
            anchor = GridBagConstraints.WEST
            insets = Insets(0, 0, 0, gap)
        })
        add(createdLabel, GridBagConstraints().apply {
            gridx = 1; gridy = 0
            anchor = GridBagConstraints.WEST
            insets = Insets(0, 0, 0, gap)
        })
        add(updatedLabel, GridBagConstraints().apply {
            gridx = 2; gridy = 0
            anchor = GridBagConstraints.WEST
            insets = Insets(0, 0, 0, gap)
        })
        add(Box.createHorizontalGlue(), GridBagConstraints().apply {
            gridx = 3; gridy = 0
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
        })
        add(trailing, GridBagConstraints().apply {
            gridx = 4; gridy = 0
            anchor = GridBagConstraints.EAST
        })
    }

    fun setDates(leftText: String, rightText: String) {
        createdLabel.text = leftText
        updatedLabel.text = rightText
    }

    companion object {
        /** Test-case variant with a Run button on the far right. */
        fun forTestCase(
            idChip: JComponent,
            createdLabel: String,
            updatedLabel: String,
            onRun: () -> Unit,
        ): HeaderUtilityRow {
            val runButton = speqaIconButton(
                icon = AllIcons.Actions.Execute,
                tooltip = SpeqaBundle.message("tooltip.startTestRun"),
                onAction = onRun,
            )
            return HeaderUtilityRow(
                idChip = idChip,
                leftDateIcon = IconLoader.getIcon("/icons/calendarCreated.svg", HeaderUtilityRow::class.java),
                leftDateText = createdLabel,
                leftDateTooltip = SpeqaBundle.message("preview.created"),
                rightDateIcon = IconLoader.getIcon("/icons/calendarUpdated.svg", HeaderUtilityRow::class.java),
                rightDateText = updatedLabel,
                rightDateTooltip = SpeqaBundle.message("preview.updated"),
                trailing = runButton,
            )
        }

        /** Test-run variant with a verdict control on the far right. */
        fun forTestRun(
            idChip: JComponent,
            startedLabel: String,
            finishedLabel: String,
            trailing: JComponent,
        ): HeaderUtilityRow {
            return HeaderUtilityRow(
                idChip = idChip,
                leftDateIcon = IconLoader.getIcon("/icons/calendarCreated.svg", HeaderUtilityRow::class.java),
                leftDateText = startedLabel,
                leftDateTooltip = SpeqaBundle.message("run.tooltip.started"),
                rightDateIcon = IconLoader.getIcon("/icons/calendarFinished.svg", HeaderUtilityRow::class.java),
                rightDateText = finishedLabel,
                rightDateTooltip = SpeqaBundle.message("run.tooltip.finished"),
                trailing = trailing,
            )
        }
    }
}
