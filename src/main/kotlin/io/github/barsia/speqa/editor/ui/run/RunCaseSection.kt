package io.github.barsia.speqa.editor.ui.run

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.ui.primitives.handCursor
import io.github.barsia.speqa.editor.ui.primitives.speqaIconButton
import io.github.barsia.speqa.model.RunCase
import io.github.barsia.speqa.run.TestRunSupport
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Renders a single [RunCase] as a collapsible section. The header is a [BorderLayout]: a square
 * collapse chevron at the left, the `TC-<id> <title>` label in the middle (ellipsized when long),
 * and a right-pinned cluster (manual indicator, result pill, drag handle) that stays visible
 * regardless of title length. The result pill is interactive: clicking it overrides the case
 * result (or re-derives it from steps); the manual indicator shows while the result is overridden.
 *
 * Every edit inside the body produces an updated [RunCase] reported through [onCaseChange].
 */
class RunCaseSection(
    project: Project,
    file: VirtualFile?,
    initial: RunCase,
    private val onCaseChange: (RunCase) -> Unit,
) : JPanel() {

    private var case: RunCase = initial
    private var expanded: Boolean = true

    private val body = RunCaseBody(project, file, initial, onCaseChange)

    private val headerLabel = JBLabel(RunCaseSectionState.headerLabel(initial))

    private val resultPill = ResultPill(
        initial = initial.result,
        tooltipText = SpeqaBundle.message("runCase.result.tooltip"),
        popupTitle = SpeqaBundle.message("runCase.result.popupTitle"),
        autoLabel = SpeqaBundle.message("runCase.result.auto"),
        onPick = { picked -> onCaseChange(TestRunSupport.overrideCaseResult(case, picked)) },
        onAuto = { onCaseChange(TestRunSupport.clearCaseOverride(case)) },
        dataContextProject = project,
    )

    /** Muted marker shown only while the case result is manually overridden. */
    private val manualIndicator = JBLabel(
        SpeqaBundle.message("runCase.result.manual.label"),
        AllIcons.General.Note,
        JBLabel.LEFT,
    ).apply {
        toolTipText = SpeqaBundle.message("runCase.result.manual.tooltip")
        foreground = JBColor.namedColor("Label.infoForeground", JBColor.GRAY)
        alignmentY = Component.CENTER_ALIGNMENT
        isVisible = RunCaseSectionState.isManual(initial)
    }

    /**
     * Drag grip for reordering whole case sections. [RunCasesContainer] attaches
     * the reusable [io.github.barsia.speqa.editor.ui.steps.DragReorderSupport]
     * gesture to this handle in the multi-case view.
     */
    val dragHandle: JComponent = JBLabel(AllIcons.General.Drag).apply {
        toolTipText = SpeqaBundle.message("runCase.dragHandle.tooltip")
        alignmentY = Component.CENTER_ALIGNMENT
        handCursor()
    }

    private val collapseToggle: JComponent = speqaIconButton(
        icon = AllIcons.General.ChevronDown,
        tooltip = SpeqaBundle.message("runCase.collapse.tooltip"),
        onAction = { setExpanded(!expanded) },
    )

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        add(buildHeader())
        add(Box.createVerticalStrut(JBUI.scale(8)))
        body.alignmentX = Component.LEFT_ALIGNMENT
        add(body)
        updateToggleIcon()
    }

    private fun buildHeader(): JComponent {
        val header = JPanel(BorderLayout(JBUI.scale(6), 0)).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
        }
        header.add(collapseToggle, BorderLayout.WEST)

        // CENTER takes the remaining width and ellipsizes a long label instead of pushing the
        // right-hand cluster off-screen.
        headerLabel.alignmentY = Component.CENTER_ALIGNMENT
        header.add(headerLabel, BorderLayout.CENTER)

        // EAST cluster stays at its preferred width, pinned to the right edge and always visible.
        val right = Box.createHorizontalBox()
        right.add(manualIndicator)
        right.add(Box.createHorizontalStrut(JBUI.scale(6)))
        resultPill.alignmentY = Component.CENTER_ALIGNMENT
        right.add(resultPill)
        right.add(Box.createHorizontalStrut(JBUI.scale(6)))
        right.add(dragHandle)
        header.add(right, BorderLayout.EAST)
        return header
    }

    /** Refresh the section from [newCase]. */
    fun update(newCase: RunCase) {
        case = newCase
        headerLabel.text = RunCaseSectionState.headerLabel(newCase)
        resultPill.setResult(newCase.result)
        manualIndicator.isVisible = RunCaseSectionState.isManual(newCase)
        body.update(newCase)
    }

    fun setExpanded(value: Boolean) {
        if (expanded == value) return
        expanded = value
        body.isVisible = value
        updateToggleIcon()
        revalidate()
        repaint()
    }

    private fun updateToggleIcon() {
        val icon = if (expanded) AllIcons.General.ChevronDown else AllIcons.General.ChevronRight
        val tooltip = SpeqaBundle.message(
            if (expanded) "runCase.collapse.tooltip" else "runCase.expand.tooltip",
        )
        (collapseToggle as? com.intellij.openapi.actionSystem.impl.ActionButton)?.let { button ->
            button.presentation.icon = icon
            button.presentation.description = tooltip
            button.toolTipText = tooltip
        }
    }
}
